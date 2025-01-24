(ns cli-tools.hooks
  (:require [clj-kondo.hooks-api :as api]
            [clojure.pprint :refer [pprint]]))


(comment
  (defcommand foo
              "docstring"
              [opta [a...]
               optb [b...]
               :let [s1 ...
                     s2 ...]]
              ...body)
  =>

  (defn foo "docstring"
    [& _args]
    (let [s1   ...
          s2   ...
          opta [a...]
          obtb [b...]]
      body)))

(defn- parse
  [state terms blocks]
  (if-not (seq terms)
    blocks
    (let [[term & more-terms] terms]
      (if (api/keyword-node? term)
        (let [{:keys [k]} term]
          (case k
            (:in-order :command :summary)
            (recur state (next more-terms) blocks)

            (:args :options)
            (recur k more-terms blocks)

            :as
            (recur state
                   (next more-terms)
                   (update blocks :afters conj (first more-terms) (api/token-node 'nil)))

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


(defn- parse-interface
  [interface]
  #_(pprint {:interface interface})
  (let [terms  (:children interface)
        blocks (parse :opts terms {:lets   []
                                   :opts   []
                                   :afters []})
        {:keys [lets opts afters]} blocks
        vector (api/vector-node
                 (concat
                   lets
                   opts
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
                                ;; Will this work if more than one expression?
                                body))])]
    (println "defcommand input:")
    (println node)
    (println "\ndefcommand output:")
    (println node')
    (println)
    {:node node'}))
