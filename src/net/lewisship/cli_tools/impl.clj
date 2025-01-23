(ns ^:no-doc net.lewisship.cli-tools.impl
  "Private namespace for implementation details for new.lewisship.cli-tools, subject to change."
  (:require [clojure.string :as string]
            [clojure.string :as str]
            [clj-commons.ansi :refer [compose pout perr *color-enabled*]]
            [clojure.tools.cli :as cli]
            [clj-commons.humanize :as h]
            [clj-commons.humanize.inflect :as inflect]
            [clojure.java.io :as io])
  (:import (java.util.regex Pattern)))

(def prevent-exit false)

(def ^:dynamic *options*
  "Bound by [[dispatch]] so that certain functions, such as help, can operate."
  nil)

(def ^:dynamic *command*
  "Bound to the command map selected by dispatch for execution."
  nil)

(def ^:dynamic *introspection-mode*
  "When true, defcommands, when invoked, bypass normal logic and simply return the
  command map. Used when extracting options for completions."
  false)

(def ^:private supported-keywords #{:in-order :as :args :options :command :summary :let :validate})

(defn exit
  [status]
  (when-not prevent-exit
    (System/exit status))
  ;; If in testing mode ...
  (throw (ex-info "Exit" {:status status})))

;; better-cond is a better implementation, but has some dependencies
(defmacro cond-let
  "An alternative to `clojure.core/cond` where instead of a test/expression pair, it is possible
  to have a :let/binding vector pair."
  [& clauses]
  (cond (empty? clauses)
        nil

        (not (even? (count clauses)))
        (throw (ex-info (str `cond-let " requires an even number of forms")
                        {:form &form
                         :meta (meta &form)}))

        :else
        (let [[test expr-or-binding-form & more-clauses] clauses]
          (if (= :let test)
            `(let ~expr-or-binding-form (cond-let ~@more-clauses))
            ;; Standard case
            `(if ~test
               ~expr-or-binding-form
               (cond-let ~@more-clauses))))))

(defn- inject-commas
  [terms]
  (interpose ", " terms))

(defn compose-list
  ([terms]
   (compose-list terms nil))
  ([terms opts]
   (let [{:keys [conjuction max-terms noun font]
          :or   {conjuction "and"
                 font       :bold.green
                 max-terms  3
                 noun       "command"}} opts
         n    (count terms)
         wrap (fn [term]
                [font term])]
     (cond
       ;; First two cases are for correctness
       (zero? n)
       nil

       (= 1 n)
       (-> terms first wrap)

       (= 2 n)
       (list
         (-> terms first wrap)
         (str " " conjuction " ")
         (-> terms second wrap))

       (<= n max-terms)
       (let [n'            (dec n)
             leading-terms (take n' terms)
             final-term    (nth terms n')]
         (concat
           (inject-commas (map wrap leading-terms))
           [(str ", " conjuction " ") (wrap final-term)]))

       :else
       (let [listed-terms (take max-terms terms)
             n-unlisted   (- n max-terms)]
         (concat
           (inject-commas (map wrap listed-terms))
           [(str (when (> max-terms 1) ",")
                 " " conjuction " "
                 (h/numberword n-unlisted)
                 " other "
                 (inflect/pluralize-noun n-unlisted noun))]))))))

(defn- println-err
  [s]
  (binding [*out* *err*] (println s)))

(defn- print-errors
  [errors]
  (when (seq errors)
    (println)
    (pout [:red (if (= 1 (count errors)) "Error:" "Errors:")])
    (doseq [e errors]
      (pout "  " [:red e]))))

(defn- arg-spec->str
  [arg-spec]
  (let [{:keys [label optional repeatable]} arg-spec]
    (apply str
           (when optional "[")
           label
           (when optional "]")
           (when repeatable
             (if optional "*" "+")))))

(defn- first-sentence
  [s]
  (-> s
      str/trim
      str/split-lines
      first
      (str/split #"\s*\.")
      first
      str/trim))

(defn- indentation-of-line
  [line]
  (if (str/blank? line)
    [0 ""]
    (let [[_ indent text] (re-matches #"(\s+)(.*)" line)]
      (if
        (some? indent)
        [(count indent) text]
        [0 line]))))

(defn- strip-indent
  [strip-chars [indent text]]
  (if (<= indent strip-chars)
    text
    (str (apply str (repeat (- indent strip-chars) " "))
         text)))

(defn- cleanup-docstring
  [docstring]
  (let [docstring'       (str/trim docstring)
        lines            (->> docstring'
                              str/split-lines
                              (map indentation-of-line))
        non-zero-indents (->> lines
                              (map first)
                              (remove zero?))]
    (if (empty? non-zero-indents)
      docstring'
      (let [indentation (reduce min non-zero-indents)]
        (->> lines
             (mapv #(strip-indent indentation %))
             (str/join "\n"))))))

(defn print-summary
  [command-map]
  (binding [*out* *err*]
    (let [{:keys [tool-name]} *options*
          {:keys [command-path]} *command*
          {:keys [command-name positional-specs command-doc summary]} command-map
          arg-strs (map arg-spec->str positional-specs)]
      (pout
        "Usage: "
        ;; A stand-alone tool doesn't have a tool-name (*options* will be nil)
        (when tool-name
          [:bold.green tool-name " "])
        ;; A stand-alone tool will use its command-name, a command within
        ;; a multi-command tool will have a command-path.
        [:bold.green (if command-path
                       (str/join " " command-path)
                       command-name)]
        " [OPTIONS]"
        (map list (repeat " ") arg-strs))
      (when command-doc
        (-> command-doc cleanup-docstring println))

      ;; There's always at least -h/--help:
      (println "\nOptions:")
      (println summary)

      (when (seq positional-specs)
        (let [max-label-width (->> positional-specs
                                   (map :label)
                                   (map count)
                                   (reduce max)
                                   ;; For indentation
                                   (+ 2))
              lines           (for [{:keys [label doc]} positional-specs]
                                (list
                                  [{:width max-label-width}
                                   [:bold label]]
                                  ": "
                                  doc))]
          (println "\nArguments:")
          (pout (interpose \newline lines)))))))

(defn print-errors
  [command-map errors]
  (let [{:keys [tool-name]} *options*
        {:keys [command-path]} *command*
        {:keys [command-name]} command-map]
    (perr
      [:red
       (inflect/pluralize-noun (count errors) "Error")
       " in "
       [:bold.green
        tool-name
        (when tool-name " ")

        (if command-path
          (str/join " " command-path)
          command-name)]
       ":"
       (if (= 1 (count errors))
         (list " " (first errors))
         (map list (repeat "\n  ") errors))])))

(defn- format-option-summary
  [max-option-width max-default-width summary-part]
  (let [{:keys [opt-label default opt-desc]} summary-part]
    (list
      "  "
      [{:width max-option-width
        :pad   :right} opt-label]
      " "
      [{:width max-default-width
        :pad   :right} default]
      (when (pos? max-default-width)
        " ")
      opt-desc)))

(defn- make-summary-part
  "Given a single compiled option spec, into a compose-compatible label, a width for that label,
  a compose-compatible default description, and a width for the description."
  [show-defaults? spec]
  (let [{:keys [short-opt long-opt required desc
                default default-desc default-fn]} spec
        opt          (cond (and short-opt long-opt) (str short-opt ", " long-opt)
                           long-opt (str "    " long-opt)
                           short-opt short-opt)
        opt-label    (if required
                       (str opt " " required)
                       opt)
        default-desc (if show-defaults?
                       (or default-desc
                           (when (contains? spec :default)
                             (if (some? default)
                               (str default)
                               "nil"))
                           (when default-fn "<computed>")
                           "")
                       "")]
    {:opt-label     [:bold opt-label]
     :opt-width     (.length opt-label)
     :default       [:italic default-desc]
     :default-width (.length default-desc)
     :opt-desc      desc}))

(defn summarize-specs
  [specs]
  (if (seq specs)
    (let [show-defaults?    (some #(or (contains? % :default)
                                       (contains? % :default-fn)) specs)
          parts             (map #(make-summary-part show-defaults? %) specs)
          max-of            (fn [k] (->> parts
                                         (map k)
                                         (reduce max)))
          max-opt-width     (max-of :opt-width)             ; Indent by two
          max-default-width (max-of :default-width)
          lines             (interpose \newline
                                       (map #(format-option-summary max-opt-width max-default-width %) parts))]
      (compose lines))
    ""))

(defn- compile-positional-spec
  "Positional specs are similar to option specs."
  [command-name terms]
  (let [[label & more] terms]
    ;; The label is required, then it's the optional documentation string
    (if (-> more first string?)
      (recur command-name
             (into [label :doc (first more)]
                   (rest more)))
      (let [spec-map     (apply hash-map more)
            {:keys [id]} spec-map
            invalid-keys (-> spec-map
                             ;; :id is actually set from the local symbol
                             (dissoc :id :doc :optional :repeatable :parse-fn :update-fn :assoc-fn :validate)
                             keys
                             sort)
            {:keys [validate update-fn repeatable doc optional parse-fn assoc-fn]} spec-map
            _            (when (and update-fn assoc-fn)
                           (throw (ex-info "May only specify one of :update-fn and :assoc-fn"
                                           {:command-name command-name
                                            :spec         spec-map})))
            assoc-fn'    (cond
                           assoc-fn
                           assoc-fn

                           update-fn
                           (fn [m k v]
                             (update m k update-fn v))

                           repeatable
                           (fn [m k v]
                             (update m k (fnil conj []) v))

                           :else
                           assoc)]
        (when (seq invalid-keys)
          (println-err (format "Warning: command %s, argument %s contains invalid key(s): %s"
                               command-name
                               id
                               (str/join ", " invalid-keys))))
        {:label      label
         :id         id
         :doc        doc
         :optional   optional
         :repeatable repeatable
         :assoc-fn   assoc-fn'
         :parse-fn   (or parse-fn identity)
         :validate   validate}))))

(defn- compile-positional-specs
  [command-name specs]
  (let [compiled (map #(compile-positional-spec command-name %) specs)]
    (loop [[this-spec & more-specs] compiled
           ids           #{}
           optional-id   nil
           repeatable-id nil]
      ;; Do some validation before returning the seq of positional specs (each a map)
      (cond-let
        (nil? this-spec)
        compiled

        :let [this-id (:id this-spec)]

        (contains? ids this-id)
        (throw (ex-info (str "Argument " this-id " of command " command-name " is not unique")
                        {:command-name command-name
                         :spec         this-spec}))

        ;; Use the keyword ids, not the labels, since these are programmer errors, not a runtime error

        (and optional-id
             (not (:optional this-spec)))
        (throw (ex-info (str "Argument " this-id " of command " command-name " is not optional but follows optional argument " optional-id)
                        {:command-name command-name
                         :spec         this-spec}))

        (some? repeatable-id)
        (throw (ex-info (str "Argument " this-id " of command " command-name " follows repeatable argument " repeatable-id ", but only the final argument may be repeatable")
                        {:command-name command-name
                         :spec         this-spec}))

        :else
        (recur more-specs
               (conj ids this-id)
               (or (when (:optional this-spec)
                     this-id)
                   optional-id)
               (or (when (:repeatable this-spec)
                     this-id)
                   repeatable-id))))))

(defn- validate-argument
  "Validates the value against the :validate vector of the spec, returning nil on
   success, or the first error.  A validation fn that returns false or throws an exception
   is a failure."
  [positional-spec value]
  (loop [[validation-fn validation-msg & more] (:validate positional-spec)]
    (when validation-fn
      (if-not (try
                (validation-fn value)
                (catch Exception _ false))
        validation-msg
        (recur more)))))

(defn- parse-positional-arguments
  "Parses the remaining command line arguments based on the positional specs.
  Returns [map errors] where map is keyed on argument id, and errors is a seq of strings."
  [positional-specs arguments]
  (loop [state {:specs           positional-specs
                :remaining       arguments
                :argument-map    {}
                :errors          []
                :ignore-required false}]
    (cond-let
      :let [{:keys [specs remaining argument-map errors ignore-required]} state
            [this-spec & more-specs] specs
            {:keys [label repeatable optional parse-fn assoc-fn id]} this-spec
            [this-argument & more-arguments] remaining]

      ;; specs and arguments exhausted
      (and (nil? this-spec)
           (nil? this-argument))
      [argument-map errors]

      ;; Hit the first optional argument and out of command line arguments.
      ;; Since all subsequent arguments must be optional (verified by compile), we can stop here.
      (and (nil? this-argument)
           ;; After the first argument is consumed by a repeatable, we treat the repeatable
           ;; command as optional.
           (or optional ignore-required))
      [argument-map errors]

      ;; Have a required argument and nothing to match it against.
      (nil? this-argument)
      [argument-map (conj errors (str "No value for required argument " label))]

      ;; Ran out of specs before running out of arguments.
      (nil? this-spec)
      [argument-map (conj errors (format "Unexpected argument '%s'" this-argument))]

      :let [[parsed error] (try
                             [(parse-fn this-argument) nil]
                             (catch Exception t
                               [nil (format "Error in %s: %s" label (ex-message t))]))]

      error
      [argument-map (conj errors (str label ": " error))]

      :let [validation-error (validate-argument this-spec parsed)]

      (some? validation-error)
      [argument-map (conj errors (str label ": " validation-error))]

      :else
      (let [state' (assoc state
                          ;; Consume an argument
                          :remaining more-arguments
                          ;; Apply the argument
                          :argument-map (assoc-fn argument-map id parsed))]
        (recur (if repeatable
                 ;; leave the last, repeatable spec in place
                 (assoc state' :ignore-required true)
                 ;; Not repeatable; it has "consumed" an argument, so continue with
                 ;; next spec and next argument
                 (assoc state' :specs more-specs)))))))

(defn abort
  [& compose-inputs]
  (println-err (apply compose compose-inputs))
  (exit 1))

(defn- fail
  [message state form]
  (throw (ex-info message
                  {:state state
                   :form  form})))

(defmulti consumer (fn [state _form]
                     ; Dispatch on the type of value to be consumed
                     (:consuming state))
          :default ::default)

(defmethod consumer ::default
  [state form]
  (fail "Unexpected interface form" state form))

(defn- consume-keyword
  [state form]
  (consumer (assoc state :consuming :keyword) form))

(defmethod consumer :options
  [state form]
  (cond
    (keyword? form)
    (consume-keyword state form)

    (not (simple-symbol? form))
    (fail "Expected option name symbol" state form)

    (contains? (-> state :option-symbols set) form)
    (fail "Option and argument symbols must be unique" state form)

    :else
    (assoc state
           :symbol form
           :pending true
           :consuming :option-def)))

(defn- append-id
  [form id-symbol]
  (let [id-keyword (-> id-symbol name keyword)]
    (if (vector? form)
      (conj form :id id-keyword)
      ;; Otherwise form is a symbol or a function call list
      (list 'conj form :id id-keyword))))

(defn- valid-definition?
  [form]
  (or (vector? form)                                        ; Normal case
      (symbol? form)                                        ; A symbol may be used when sharing options between commands
      (list? form)))                                        ; Or maybe it's a function call to generate the vector


(defmethod consumer :option-def
  [state option-def]
  (when-not (valid-definition? option-def)
    (fail "Expected option definition" state option-def))

  (let [option-symbol (:symbol state)
        ;; Explicitly add an :id to the option def to ensure that the value can be extracted
        ;; from the parsed :options map correctly via a keyword destructure
        option-def'   (append-id option-def option-symbol)]
    (-> state
        (update :command-options conj option-def')
        (update :option-symbols conj option-symbol)
        (dissoc :symbol)
        (assoc :consuming :options
               :pending false))))

(defmethod consumer :arg-def
  ;; A positional argument
  [state arg-def]
  (when-not (valid-definition? arg-def)
    (fail "Expected argument definition" state arg-def))

  (let [arg-symbol (:symbol state)
        arg-def'   (append-id arg-def arg-symbol)]
    (-> state
        (update :command-args conj arg-def')
        (update :option-symbols conj arg-symbol)
        (dissoc :symbol)
        (assoc :consuming :args
               :pending false))))

(defmethod consumer :args
  [state form]
  (cond
    (keyword? form)
    (consume-keyword state form)

    (not (simple-symbol? form))
    (fail "Expected argument name symbol" state form)

    (contains? (-> state :option-symbols set) form)
    (fail "Option and argument symbols must be unique" state form)

    :else
    (assoc state
           :symbol form
           :pending true
           :consuming :arg-def)))

(defmethod consumer :keyword
  [state form]
  (when-not (keyword? form)
    (fail "Expected a keyword" state form))

  (when-not (contains? supported-keywords form)
    (fail "Unexpected keyword" state form))

  (assoc state :consuming form
         :pending true))

(defn- complete-keyword
  [state]
  (assoc state :consuming :keyword
         :pending false))

(defmethod consumer :as
  [state form]
  (when-not (simple-symbol? form)
    (fail "Expected command-map symbol" state form))

  (-> state
      (assoc :command-map-symbol form)
      complete-keyword))

(defmethod consumer :in-order
  [state form]
  (when-not (boolean? form)
    (fail "Expected boolean after :in-order" state form))

  (-> state
      (assoc-in [:parse-opts-options :in-order] form)
      complete-keyword))

(defmethod consumer :let
  [state form]
  (when-not (and (vector? form)
                 (even? (count form)))
    (fail "Expected a vector of symbol/expression pairs" state form))

  (-> state
      (update :let-forms into form)
      complete-keyword))

(defmethod consumer :command
  [state form]
  (when-not (string? form)
    (fail "Expected string for name of command" state form))

  (-> state
      (assoc :command-name form)
      complete-keyword))

(defmethod consumer :summary
  [state form]
  (when-not (string? form)
    (fail "Expected string summary for command" state form))

  (-> state
      (assoc :command-summary form)
      complete-keyword))

(defmethod consumer :validate
  [state form]
  (when-not (vector? form)
    (fail "Expected a vector of test/message pairs" state form))

  (when-not (-> form count even?)
    (fail "Expected even number of tests and messages" state form))

  (-> state
      (update :validate-cases into form)
      complete-keyword))

(defn compile-interface
  "Parses the interface forms of a `defcommand` into a base command map; the interface
   defines the options and positional arguments that will be parsed."
  [command-doc forms]
  (let [initial-state {:consuming       :options
                       :option-symbols  []
                       :command-options []
                       :command-args    []
                       :let-forms       []
                       :validate-cases  []
                       :command-doc     command-doc}
        final-state   (reduce consumer
                              initial-state forms)]
    (when (:pending final-state)
      (throw (ex-info "Missing data in interface definitions"
                      {:state final-state
                       :forms forms})))
    (-> final-state
        (dissoc :consuming :pending :symbol)
        (update :command-options conj ["-h" "--help" "This command summary" :id :help]))))

(defn parse-cli
  [command-name command-line-arguments command-spec]
  (cond-let
    :let [{:keys [command-args command-options parse-opts-options]} command-spec
          {:keys [in-order summary-fn]
           :or   {in-order   false
                  summary-fn summarize-specs}} parse-opts-options
          positional-specs (compile-positional-specs command-name command-args)
          command-map (merge command-spec
                             {:command-name     command-name
                              :positional-specs positional-specs}
                             (cli/parse-opts command-line-arguments command-options
                                             :in-order in-order
                                             :summary-fn summary-fn))
          {:keys [arguments options]} command-map]

    ;; Check for help first, as otherwise can get needless errors r.e. missing required positional arguments.
    (:help options)
    (do
      (print-summary command-map)
      (exit 0))

    :let [[positional-arguments arg-errors] (parse-positional-arguments positional-specs arguments)
          errors (concat (:errors command-map)
                         arg-errors)]

    (seq errors)
    (do
      (print-errors command-map errors)
      (exit 1))

    :else
    ;; option and positional argument are verified to have unique symbols, so merge it all together
    (update command-map :options merge positional-arguments)))

(defn extract-command-summary
  [command-var]
  (let [v-meta (meta command-var)
        {:keys [::command-summary]} v-meta]
    (or command-summary
        (-> v-meta :doc first-sentence))))

(defn- print-commands
  [command-name-width commands]
  (doseq [{:keys [command-name]
           :as   command-map} (sort-by :command-name commands)]
    (pout
      "  "
      [{:width command-name-width} [:bold.green command-name]]
      ": "
      (:command-summary command-map))))

(defn- command-match?
  [command search-term]
  (or (nil? search-term)
      (let [{:keys [command-name command-summary]} command]
        (or (string/includes? (string/lower-case command-name) search-term)
            (string/includes? (string/lower-case command-summary) search-term)))))

(defn- prune-empty-values
  [m]
  (reduce-kv (fn [m k v]
               (cond-> m
                 (seq v) (assoc k v)))
             {}
             m))

(defn- collect-commands
  "Walks the commands tree, to produce a map from command category to seq of command map."
  ([commands search-term] (collect-commands {} commands search-term))
  ([category->command commands search-term]
   (->> commands
        (reduce-kv (fn [m k v]
                     (if (string? k)
                       (let [{:keys [category]} v]
                         (if category
                           (cond-> m
                             (command-match? v search-term) (update category conj v))
                           ;; It's a group psuedo-command, a container of nested commands; recurse in
                           ;; (this is where non-string keys come in)
                           (collect-commands m v search-term)))
                       m))
                   category->command)
        prune-empty-values)))

(defn show-tool-help
  [options search-term]
  (binding [*out* *err*]
    (let [{:keys [tool-name tool-doc commands categories flat]} options]
      (pout "Usage: " [:bold.green tool-name] " [TOOL OPTIONS] COMMAND ...")
      (when tool-doc
        (println)
        (-> tool-doc cleanup-docstring println))
      (println "\nTool options:")
      (pout [:bold "  -C, --color"] "    Enable ANSI color output")
      (pout [:bold "  -N, --no-color"] " Disable ANSI color output")
      (pout [:bold "  -h, --help"] "     This tool summary\n")
      (let [grouped-commands   (collect-commands commands (when search-term
                                                            (string/lower-case search-term)))
            all-commands       (cond->> (reduce into [] (vals grouped-commands))
                                        ;; For a flat view, each command's name is its path (i.e., prefixed with the command group).
                                        flat (map (fn [command]
                                                    (assoc command :command-name (str/join " " (:command-path command))))))
            command-name-width (->> all-commands
                                    (map :command-name)
                                    (map count)
                                    (apply max 0))]
        (cond
          (empty? all-commands)
          (do
            (if search-term
              (pout "No commands match " [:italic search-term])
              (println "No commands are configured"))
            (exit 0))

          search-term
          (pout "Commands (matching " [:italic search-term] "):")

          :else
          (println "Commands:"))
        (if flat
          (print-commands command-name-width all-commands)
          (doseq [{:keys [category label command-group]} (sort-by (juxt :order :label) categories)
                  :when (contains? grouped-commands category)]
            (println)
            (pout [:bold
                   (when command-group
                     (list
                       [:green command-group]
                       " - "))
                   label])
            (print-commands command-name-width (get grouped-commands category)))))))
  (exit 0))

(defn- to-matcher
  [s]
  (let [terms      (str/split s #"\-")
        re-pattern (apply str "(?i)"
                          (map-indexed (fn [i term]
                                         (str
                                           (when (pos? i) "\\-")
                                           ".*\\Q" term "\\E.*"))
                                       terms))
        re         (Pattern/compile re-pattern)]
    (fn [input]
      (re-matches re input))))

(defn find-matches
  [s values]
  (let [values' (set values)]
    ;; If can find an exact match, then keep just that;
    (if (contains? values' s)
      [s]
      ;; Otherwise, treat s as a match string and find any values that loosely match it.
      (sort (filter (to-matcher s) values')))))

(defn use-help-message
  [tool-name]
  (list ", use " [:bold.green tool-name " help"] " to list commands"))

(defn- invoke-command
  [command-map args]
  (binding [*command* command-map]
    (apply (-> command-map :var requiring-resolve) args)))

(defn- inner-dispatch
  [tool-name arguments commands]
  (let [command-name (first arguments)]
    (if (or (nil? command-name)
            (str/starts-with? command-name "-"))
      (abort [:bold tool-name] ": no command provided" (use-help-message tool-name))
      (loop [prefix            []
             term              command-name
             remaining-args    (next arguments)
             possible-commands commands]
        (cond-let
          :let [matchable-terms (filter string? (keys possible-commands))]

          ;; Options start with a '-', but we're still looking for commands
          (or (nil? term)
              (str/starts-with? term "-"))
          (abort
            [:bold.green tool-name ": " [:red (str/join " " prefix)]]
            " is incomplete; "
            (compose-list matchable-terms)
            " could follow; use "
            [:bold [:green tool-name " help"]]
            " to list commands")

          ;; In a command group, only the string keys map to further commands; keyword keys are other structure.
          :let [matched-terms (find-matches term matchable-terms)
                match-count (count matched-terms)]

          (not= 1 match-count)
          (let [body        (if (pos? match-count)
                              (list "matches "
                                    (compose-list matched-terms))
                              (list "is not a command, expected "
                                    (compose-list matchable-terms {:conjuction "or"})))
                help-suffix (list
                              "; use "
                              [:bold [:green tool-name " help"]]
                              " to list commands")]
            (abort
              [:bold [:green tool-name] ": "
               [:red (string/join " " (conj prefix term))]]
              " "
              body
              help-suffix))

          :let [matched-term (first matched-terms)
                matched-command (get possible-commands matched-term)]

          (:var matched-command)
          (invoke-command matched-command remaining-args)

          ;; Otherwise, it was a command group.
          ;; The map for a group contains string keys for nested commands, as well as keyword keys
          ;; not used here.
          :else
          (recur (conj prefix term)
                 (first remaining-args)
                 (rest remaining-args)
                 matched-command))))))

(defn dispatch-options-parser
  [tool-name arguments commands]
  (let [[first-arg & remaining-args] arguments]
    (cond
      ;; In the normal case, when help is available, treat -h or --help the same as help
      (#{"-h" "--help"} first-arg)
      (inner-dispatch tool-name (cons "help" remaining-args) commands)

      (#{"-C" "--color"} first-arg)
      (binding [*color-enabled* true]
        ;; Can't use recur, due to binding
        (dispatch-options-parser tool-name remaining-args commands))

      (#{"-N" "--no-color"} first-arg)
      (binding [*color-enabled* false]
        (dispatch-options-parser tool-name remaining-args commands))

      :else
      (inner-dispatch tool-name arguments commands))))

(defn dispatch
  [{:keys [tool-name commands arguments] :as options}]
  ;; Capture these options for use by help command or when printing usage
  (binding [*options* options]
    (when (str/blank? tool-name)
      (throw (ex-info "must specify :tool-name" {:options options})))
    (dispatch-options-parser tool-name arguments commands)
    nil))

(defn command-map?
  [arguments]
  (and (= 1 (count arguments))
       (-> arguments first map?)))

(defn default-tool-name
  []
  (when-let [path (System/getProperty "babashka.file")]
    (-> path io/file .getName)))

(defn invert-tests-in-validate-cases
  [validate-cases]
  (->> validate-cases
       (partition 2)
       (mapcat (fn [[test expr]]
                 [(list not test) expr]))))

