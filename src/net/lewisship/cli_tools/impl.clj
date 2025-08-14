(ns ^:no-doc net.lewisship.cli-tools.impl
  "Private namespace for implementation details for new.lewisship.cli-tools, subject to change."
  (:require [clojure.string :as string]
            [clj-commons.ansi :refer [compose perr]]
            [clojure.tools.cli :as cli]
            [clj-commons.humanize :as h]
            [clj-commons.humanize.inflect :as inflect]
            [clojure.java.io :as io])
  (:import (java.util.regex Pattern)))

(def prevent-exit false)

(def ^:dynamic *tool-options*
  "Bound by [[dispatch]] so that certain functions, such as help, can operate."
  nil)

(def ^:dynamic *command-map*
  "Bound to the command map selected by dispatch for execution."
  nil)

(def ^:dynamic *introspection-mode*
  "When true, defcommands, when invoked, bypass normal logic and simply return the
  command spec. Used when extracting options for completions."
  false)

(def ^:private supported-keywords #{:in-order :args :options :command :title :let :validate})

(defn command-path
  []
  (let [{:keys [tool-name]} *tool-options*
        path (:command-path *command-map*)]
    (when tool-name
      [:bold.green
       tool-name
       (when (seq path)
         (list
           " "
           (string/join " " path)))])))

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

(defn- numberword
  [n]
  (if (< n 20)
    (h/numberword n)
    (format "%,2d" n)))

(defn compose-list
  ([terms]
   (compose-list terms nil))
  ([terms opts]
   (let [{:keys [conjuction max-terms font]
          :or   {conjuction "and"
                 font       :bold.green
                 max-terms  3}} opts
         n    (count terms)
         terms' (sort terms)
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
         (-> terms' first wrap)
         (str " " conjuction " ")
         (-> terms' second wrap))

       (<= n max-terms)
       (let [n'            (dec n)
             leading-terms (take n' terms')
             final-term    (nth terms' n')]
         (concat
           (inject-commas (map wrap leading-terms))
           [(str ", " conjuction " ") (wrap final-term)]))

       :else
       (let [listed-terms (take max-terms terms')
             n-unlisted   (- n max-terms)]
         (concat
           (inject-commas (map wrap listed-terms))
           [(str " (" conjuction " "
                 (numberword n-unlisted) " "
                 (inflect/pluralize-noun n-unlisted "other") ")")]))))))

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
  (when (string? s)
    (-> s
        string/trim
        string/split-lines
        first
        (string/split #"\s*\.")
        first
        string/trim)))

(defn- indentation-of-line
  [line]
  (if (string/blank? line)
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
  (let [docstring'       (string/trim docstring)
        lines            (->> docstring'
                              string/split-lines
                              (map indentation-of-line))
        non-zero-indents (->> lines
                              (map first)
                              (remove zero?))]
    (if (empty? non-zero-indents)
      docstring'
      (let [indentation (reduce min non-zero-indents)]
        (->> lines
             (mapv #(strip-indent indentation %))
             (string/join "\n"))))))

(defn print-summary
  [command-doc command-map]
  (let [{:keys [tool-name]} *tool-options*
        {:keys [command-path]} *command-map*
        {:keys [command-name positional-specs summary]} command-map
        arg-strs (map arg-spec->str positional-specs)]
    (perr
      "Usage: "
      ;; A stand-alone tool doesn't have a tool-name (*options* will be nil)
      (when tool-name
        [:bold.green tool-name " "])
      ;; A stand-alone tool will use its command-name, a command within
      ;; a multi-command tool will have a command-path.
      [:bold.green (if command-path
                     (string/join " " command-path)
                     command-name)]
      " [OPTIONS]"
      (map list (repeat " ") arg-strs))
    (when command-doc
      (-> command-doc cleanup-docstring perr))

    ;; There's always at least -h/--help:
    (perr "\nOptions:\n" summary)

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
        (perr "\nArguments:")
        (perr (interpose \newline lines))))))

(defn print-errors
  [errors]
  (let [{:keys [tool-name]} *tool-options*]
    (perr
      [:red
       (inflect/pluralize-noun (count errors) "Error")
       (when tool-name
         (list
           " in "
           (command-path)))
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
        :align :left} opt-label]
      " "
      [{:width max-default-width
        :align :left} default]
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
          (perr (format "Warning: command %s, argument %s contains invalid key(s): %s"
                               command-name
                               id
                               (string/join ", " invalid-keys))))
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
  (apply perr compose-inputs)
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

(defmethod consumer :title
  [state form]
  (when-not (string? form)
    (fail "Expected string title for command" state form))

  (-> state
      (assoc :title form)
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
  "Parses the interface forms of a `defcommand` into a command spec; the interface
   defines the options and positional arguments that will be parsed."
  [forms]
  (let [initial-state {:consuming       :options
                       :option-symbols  []
                       :command-options []
                       :command-args    []
                       :let-forms       []
                       :validate-cases  []}
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
  "Given a command specification (returned from [[compile-interface]]), this is called during
  execution time to convert command line arguments into options. The command map merges new
  keys into the command spec."
  [command-name command-doc command-line-arguments command-spec]
  (cond-let
    :let [{:keys [command-args command-options parse-opts-options]} command-spec
          {:keys [in-order]
           :or   {in-order false}} parse-opts-options
          positional-specs (compile-positional-specs command-name command-args)
          command-map (merge command-spec
                             {:command-name     command-name
                              :positional-specs positional-specs}
                             (cli/parse-opts command-line-arguments command-options
                                             :in-order in-order
                                             :summary-fn summarize-specs))
          {:keys [arguments options]} command-map]

    ;; Check for help first, as otherwise can get needless errors r.e. missing required positional arguments.
    (:help options)
    (do
      (print-summary command-doc command-map)
      (exit 0))

    :let [[positional-arguments arg-errors] (parse-positional-arguments positional-specs arguments)
          errors (concat (:errors command-map)
                         arg-errors)]

    (seq errors)
    (do
      (print-errors errors)
      (exit 1))

    :else
    ;; option and positional argument are verified to have unique symbols, so merge it all together
    (update command-map :options merge positional-arguments)))

(defn- command-match?
  [command search-term]
  (let [{:keys [doc command-summary command-path]} command]
    (or (string/includes? (string/lower-case doc) search-term)
        (some #(string/includes? (string/lower-case %) search-term)
              (map string/lower-case command-path))
        (and command-summary
             (string/includes? (string/lower-case command-summary) search-term)))))

(defn- collect-subs
  [commands-map *result]
  (doseq [command (vals commands-map)]
    (vswap! *result conj! command)
    (collect-subs (:subs command) *result)))

(defn- collect-commands
  [command-root]
  (let [*result (volatile! (transient []))]
    (collect-subs command-root *result)
    (-> *result deref persistent!)))

(def ^:private missing-doc [:red "(missing documentation)"])

(defn extract-command-title
  [command-map]
  (or (:title command-map)
      (-> command-map :doc first-sentence)
      missing-doc))

(defn- print-commands
  [command-name-width container-map commands-map recurse?]
  ;; subs is a mix of commands and groups
  (let [sorted-commands     (->> commands-map
                             vals
                                 (sort-by :command))
        command-name-width' (or command-name-width
                                (->> sorted-commands
                                     (map #(-> % :command count))
                                     (reduce max 0)))]
    (when container-map
      (perr (when recurse? "\n")
            [:bold (string/join " " (:command-path container-map))]
            " - "
            (-> container-map :doc cleanup-docstring)))

    (when (seq sorted-commands)
      (perr "\nCommands:"))

    ;; Commands (including sub-groups) inside this command
    (doseq [{:keys [fn command] :as command-map} sorted-commands]
      (perr
        "  "
        [{:width command-name-width'} [:bold.green command]]
        ": "
        [(when-not fn :italic)
         (extract-command-title command-map)]))

    ;; Recurse and print sub-groups
    (when recurse?
      (->> sorted-commands
           (remove :fn)                                     ; Remove commands, leave groups
           (run! #(print-commands command-name-width' % (:subs %) true))))))

(defn- command-path-width
  [path]
  (apply + (count path) -1 (map count path)))

(defn print-tool-help
  [options search-term full?]
  (cond-let
    :let [search-term' (when search-term
                         (string/lower-case search-term))
          {tool-doc :doc
           :keys    [tool-name command-root]} options
          _ (do
              (perr "Usage: " [:bold.green tool-name] " [TOOL OPTIONS] COMMAND ...")
              (when tool-doc
                (perr "\n"
                      (cleanup-docstring tool-doc)))
              (perr "\nTool options:\n"
                    (-> options :tool-summary deref)))
          all-commands (collect-commands command-root)]

    (nil? search-term')
    (let [command-name-width (when full?
                               (->> all-commands
                                    (map :command)
                                    (map count)
                                    (apply max 0)))]
      (print-commands command-name-width nil command-root full?))

    :let [matching-commands (filter #(command-match? % search-term') all-commands)]

    (seq matching-commands)
    (let [command-width (->> matching-commands
                             (map :command-path)
                             (map command-path-width)
                             (reduce max 0))
          n (count matching-commands)]
      (perr
        "\n"
        (-> n numberword string/capitalize)
        (if (= n 1)
          " command matches "
          " commands match ")
        [:italic search-term]
        ":" "\n")
      (doseq [command (sort-by :command-path matching-commands)]
        (perr [{:font  :bold.green
                :width command-width}
               (string/join " " (:command-path command))]
              ": "
              (extract-command-title command))))

    :else
    (perr "\nNo commands match " [:italic search-term]))
  (exit 0))

(defn- to-matcher
  "A matcher for string s that is case insenstive and matches an initial prefix."
  [s]
  (let [re-pattern (str "(?i)" (Pattern/quote s) ".*")
        re         (Pattern/compile re-pattern)]
    (fn [input]
      (re-matches re input))))

(defn find-matches
  [s values]
  (let [values' (set values)]
    ;; If can find an exact match, then keep just that
    (if (contains? values' s)
      [s]
      ;; Otherwise, treat s as a match string and find any values that loosely match it.
      (sort (filter (to-matcher s) values')))))

(defn- use-help-message
  [tool-name]
  (list ", use " [:bold.green tool-name " help"] " to list commands"))

(defn dispatch
  [{:keys [command-root arguments tool-name] :as options}]
  (binding [*tool-options* options]
    (let [command-name (first arguments)]
      (if (or (nil? command-name)
              (string/starts-with? command-name "-"))
        (abort [:bold.green tool-name] ": no command provided" (use-help-message tool-name))
        (loop [prefix            []                           ; Needed?
               term              command-name
               remaining-args    (next arguments)
               container-map     nil
               commands-map      command-root]
          (cond-let
            (#{"-h" "--help"} term)
            (do
              (print-commands nil container-map commands-map false)
              (exit 0))

            :let [possible-commands commands-map
                  matchable-terms (keys possible-commands)]

            ;; Options start with a '-', but we're still looking for commands
            (or (nil? term)
                (string/starts-with? term "-"))
            (abort
              [:bold.green tool-name ": "
               (string/join " " (butlast prefix))
               [:red (last prefix)]]
              " is incomplete; "
              (compose-list matchable-terms)
              " could follow; use "
              [:bold [:green tool-name " " (string/join " " prefix) " --help (or -h)"]]
              " to list commands")

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
                                [:bold [:green tool-name " "
                                        (if (seq prefix)
                                          (string/join " " prefix)
                                          "help")]]
                                (when (seq prefix)
                                  " --help (or -h)")
                                " to list commands")]
              (abort
                [:bold [:green tool-name] ": "
                 [:green (string/join " " prefix)]
                 (when (seq prefix) " ")
                 [:red term]]
                " "
                body
                help-suffix))

            ;; Exactly one match
            :let [matched-term (first matched-terms)
                  matched-command (get possible-commands matched-term)]

            (:fn matched-command)
            (binding [*command-map* matched-command]
              (apply (-> matched-command :fn requiring-resolve) remaining-args))

            ;; Otherwise, it was a command group.
            :else
            (recur (:command-path matched-command)
                   (first remaining-args)
                   (rest remaining-args)
                   matched-command
                   (:subs matched-command)))))))
  nil)

(defn command-map?
  [arguments]
  (and (= 1 (count arguments))
       (-> arguments first map?)))

(defn- default-tool-name
  []
  (when-let [path (System/getProperty "babashka.file")]
    (-> path io/file .getName)))

(defn invert-tests-in-validate-cases
  [validate-cases]
  (->> validate-cases
       (partition 2)
       (mapcat (fn [[test expr]]
                 [(list not test) expr]))))

(defn- collect-nested-commands
  [path in-namespace]
  (require in-namespace)
  (->> in-namespace
       find-ns
       ns-publics
       vals
       (reduce (fn [result command-var]
                 (let [{:keys  [doc]
                        ::keys [command-name title]} (meta command-var)]
                   (cond-> result
                     command-name (assoc command-name
                                         (cond->
                                           {:fn           (symbol command-var)
                                            ;; Commands have a full :doc and an optional short :title
                                            ;; (the title defaults to the first sentence of the :doc
                                            ;; if not provided
                                            :doc          doc
                                            :command      command-name
                                            :command-path (conj path command-name)}
                                           title (assoc :title title))))))
               {})))


(defn- build-command-group
  [descriptor path command]
  (let [{:keys [namespaces doc groups]} descriptor
        path'           (if (nil? path)
                          []
                          (conj path command))
        direct-commands (->> namespaces
                             (map #(collect-nested-commands path' %))
                             (reduce merge {}))
        ;; Mix in nested groups to form the subs for this group
        subs            (reduce-kv
                          (fn [commands group-command group-descriptor]
                            (assoc commands group-command
                                   (build-command-group group-descriptor path' group-command)))
                          direct-commands
                          groups)
        doc'            (or doc
                            (some #(-> % find-ns meta :doc) namespaces))]
    {:doc          doc'                                     ; groups have just :doc, no :title
     :command      command
     :command-path path'
     :subs         subs}))

(defn expand-tool-options
  [dispatch-options]
  (let [{:keys [tool-name transformer]} dispatch-options
        tool-name' (or tool-name
                       (default-tool-name)
                       (throw (ex-info "No :tool-name specified" {:options dispatch-options})))
        ;; options are also the root descriptor for the built-in namespace
        root       (-> dispatch-options
                       (update :namespaces conj 'net.lewisship.cli-tools.builtins)
                       (build-command-group nil tool-name)
                       :subs
                       (cond-> transformer #(transformer dispatch-options %)))]
    {:tool-name tool-name'
     :command-root root}))

