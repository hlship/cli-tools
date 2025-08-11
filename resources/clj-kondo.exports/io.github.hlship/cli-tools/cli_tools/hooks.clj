(ns cli-tools.hooks
  (:require [clj-kondo.hooks-api :as api]))

(defn- parse
  [state terms blocks]
  (if-not (seq terms)
    blocks
    (let [[term & more-terms] terms]
      (if (api/keyword-node? term)
        (let [{:keys [k]} term]
          (case k
            (:in-order :command :title)
            (recur state (next more-terms) blocks)

            (:args :options)
            (recur k more-terms blocks)

            :let
            (recur state
                   (next more-terms)
                   (update blocks :lets into (-> more-terms first :children)))

            :validate
            (recur state
                   (next more-terms)
                   (update blocks :afters conj (api/token-node (gensym "_validate")) (first more-terms)))))
        ;; Not a keyword, so either a flag/option or a position argument.
        (recur state
               (next more-terms)
               (update blocks :opts conj term (first more-terms)))))))

(defn xform-opts
  "This is a lot of work to actually defeat clj-kondo's normal static analysis.

  See https://github.com/hlship/cli-tools/issues/28 for more info."
  [opts]
  (when (seq opts)
    (let [pairs     (partition 2 opts)
          collector (api/token-node (gensym "options"))
          map-terms (mapcat (fn [[sym v]]
                              [(api/keyword-node (-> sym str keyword)) v])
                            pairs)]
      [collector
       (api/map-node (vec map-terms))

       (api/map-node [(api/keyword-node :keys)
                      (api/vector-node (map first pairs))])
       collector])))

(defn- parse-interface
  [interface]
  (let [terms  (:children interface)
        blocks (parse :opts terms {:lets   []
                                   :opts   []
                                   :afters []})
        {:keys [lets opts afters]} blocks
        vector (api/vector-node
                 (concat
                   lets
                   (xform-opts opts)
                   afters))]
    vector))

(defn defcommand
  "Converts defcommand into something parseable. Assumption: the defcommand is valid."
  [{:keys [node]}]
  (let [[command-symbol docstring interface & body] (-> node :children rest)
        interface-vector (parse-interface interface)
        node'            (api/list-node
                           [(api/token-node 'defn)
                            command-symbol
                            docstring
                            (api/vector-node [(api/token-node '&)
                                              (api/token-node '_args)])
                            (api/list-node
                              (concat
                                [(api/token-node 'let)
                                 interface-vector]
                                body))])]
    {:node node'}))

(defn expand-cond-let
  [nodes]
  (if-not (seq nodes)
    (api/token-node 'nil)
    (let [[condition expr & more-nodes] nodes]
      (if (and (api/keyword-node? condition)
               (-> condition :k (= :let)))
        (api/list-node
          [(api/token-node 'let)
           expr                                             ; vector body of let
           (expand-cond-let more-nodes)                     ; cond-let continues as body of let
           ])
        ;; Normal case
        (api/list-node
          [(api/token-node 'if)
           condition
           expr
           (expand-cond-let more-nodes)])))))

(defn cond-let
  [{:keys [node]}]
  (let [children (-> node :children rest)
        node'    (expand-cond-let children)]
    {:node node'}))
