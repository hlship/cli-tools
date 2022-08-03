(ns ^:no-doc net.lewisship.bb.tasks.impl
  "Private namespace for implementation details for bb.tasks, subject to change."
  (:require [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(def prevent-exit false)

(def tool-name "bb")

(defn- exit
  [status]
  (when-not prevent-exit
    (System/exit status))
  ;; If in testing mode ...
  (throw (ex-info "Exit" {:status status})))

(defn- pad-left
  [s pad n]
  (let [x (- n (.length s))]
    (if (pos? x)
      (str (apply str (repeat x pad))
           s)
      s)))

;; better-cond has some dependencies
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

(defn- println-err
  [s]
  (binding [*out* *err*] (println s)))

(defn- print-errors
  [errors]
  (when (seq errors)
    (println)
    (println (if (= 1 (count errors)) "Error:" "Errors:"))
    (doseq [e errors]
      (println (str "  " e)))))

(defn- arg-spec->str
  [arg-spec]
  (let [{:keys [label optional repeatable]} arg-spec]
    (apply str
           (when optional "[")
           label
           (when optional "]")
           (when repeatable
             (if optional "*" "+")))))

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
  (let [docstring' (str/trim docstring)
        lines (->> docstring'
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
  [task-map errors]
  (let [{:keys [task-name positional-specs task-doc summary]} task-map]
    (apply println
           (remove nil? (concat ["Usage:" tool-name task-name
                                 "[OPTIONS]"]
                                (map arg-spec->str positional-specs))))

    (when task-doc
      (-> task-doc cleanup-docstring println))

    ;; There's always at least -h/--help:
    (println "\nOptions:")
    (println summary)

    (when (seq positional-specs)
      (let [label-width (->> positional-specs
                             (map :label)
                             (map count)
                             (reduce max)
                             ;; For indentation
                             (+ 2))]
        (println "\nArguments:")
        (doseq [{:keys [label doc]} positional-specs]
          (println (str
                     (pad-left label " " label-width)
                     ": "
                     doc)))))

    (print-errors errors)))

(defn- compile-positional-spec
  "Positional specs are similar to option specs."
  [task-name terms]
  (let [[label & more] terms]
    ;; The label is required, then it's the optional documentation string
    (if (-> more first string?)
      (recur task-name
             (into [label :doc (first more)]
                   (rest more)))
      (let [spec-map (apply hash-map more)
            {:keys [id]} spec-map
            invalid-keys (-> spec-map
                             ;; :id is actually set from the local symbol
                             (dissoc :id :doc :optional :repeatable :parse-fn :update-fn :assoc-fn :validate)
                             keys
                             sort)
            {:keys [validate update-fn repeatable doc optional parse-fn assoc-fn]} spec-map
            _ (when (and update-fn assoc-fn)
                (throw (ex-info "May only specify one of :update-fn and :assoc-fn"
                                {:task-name task-name
                                 :spec spec-map})))
            assoc-fn' (cond
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
          (println-err (format "Warning: task %s, argument %s contains invalid key(s): %s"
                               task-name
                               id
                               (str/join ", " invalid-keys))))
        {:label label
         :id id
         :doc doc
         :optional optional
         :repeatable repeatable
         :assoc-fn assoc-fn'
         :parse-fn (or parse-fn identity)
         :validate validate}))))

(defn- compile-positional-specs
  [task-name specs]
  (let [compiled (map #(compile-positional-spec task-name %) specs)]
    (loop [[this-spec & more-specs] compiled
           ids #{}
           optional-id nil
           repeatable-id nil]
      ;; Do some validation before returning the seq of positional specs (each a map)
      (cond-let
        (nil? this-spec)
        compiled

        :let [this-id (:id this-spec)]

        (contains? ids this-id)
        (throw (ex-info (str "Argument " this-id " of command " task-name " is not unique")
                        {:task-name task-name
                         :spec this-spec}))

        ;; Use the keyword ids, not the labels, since these are a programmer errors, not a runtime error

        (and optional-id
             (not (:optional this-spec)))
        (throw (ex-info (str "Argument " this-id " of command " task-name " is not optional but follows optional argument " optional-id)
                        {:task-name task-name
                         :spec this-spec}))

        (some? repeatable-id)
        (throw (ex-info (str "Argument " this-id " of command " task-name " follows repeatable argument " repeatable-id ", but only the final argument may be repeatable")
                        {:task-name task-name
                         :spec this-spec}))

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
  (loop [state {:specs positional-specs
                :remaining arguments
                :argument-map {}
                :errors []
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

(defn- fail
  [message state form]
  (throw (ex-info message
                  {:state state
                   :form form})))

(defmulti consumer (fn [state _form]
                     ; Dispatch on the type of value to be consumed
                     (:consuming state))
                   :default ::default)

(defmethod  consumer ::default
         [state form]
         (fail "Unexpected interface form" state form))

(defmethod consumer :options
  [state form]
  (cond
    (keyword? form)
    (assoc state :consuming form :pending true)

    (not (simple-symbol? form))
    (fail "Expected option name symbol" state form)

    ;; Could check for uniqueness here

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
      (symbol? form)                                        ; A symbol may be used when sharing options between tasks
      (list? form)))                                        ; Or maybe it's a function call to generate the vector

(defmethod consumer :option-def
  [state option-def]
  (when-not (valid-definition? option-def)
    (fail "Expected option definition" state option-def))

  (let [option-symbol (:symbol state)
        ;; Explicitly add an :id to the option def to ensure that the value can be extracted
        ;; from the parsed :options map correctly via a keyword destructure
        option-def' (append-id option-def option-symbol)]
    (-> state
        (update :task-options conj option-def')
        (update :option-symbols conj option-symbol)
        (dissoc :symbol :pending)
        (assoc :consuming :options))))

(defmethod consumer :arg-def
  ;; A positional argument
  [state arg-def]
  (when-not (valid-definition? arg-def)
    (fail "Expected argument definition" state arg-def))

  (let [arg-symbol (:symbol state)
        arg-def' (append-id arg-def arg-symbol)]
    (-> state
        (update :task-args conj arg-def')
        (update :arg-symbols conj arg-symbol)
        (dissoc :symbol :pending)
        (assoc :consuming :args))))

(defmethod consumer :args
  [state form]
  (cond
    (keyword? form)
    (assoc state :consuming form :pending true)

    (not (simple-symbol? form))
    (fail "Expected argument name symbol" state form)

    ;; Could check for uniqueness here

    :else
    (assoc state
           :symbol form
           :pending true
           :consuming :arg-def)))

(defmethod consumer :as
  [state form]
  (when-not (simple-symbol? form)
    (fail "Expected task-map symbol" state form))

  (-> state
      (assoc :task-map-symbol form
             :consuming :keyword)
      (dissoc :pending)))

(defmethod consumer :in-order
  [state form]
  (when-not (boolean? form)
    (fail "Expected boolean after :in-order" state form))

  (-> state
      (dissoc :pending)
      ;; This is slightly dodgy, we should have a memory of whether in :options or :args
      ;; and return to that.
      (assoc :consuming :args)
      (assoc-in [:parse-opts-options :in-order] form)))

(defmethod consumer :keyword
  [state form]
  (when-not (keyword? form)
    (fail "Expected a keyword" state form)

    (assoc state :consuming form :pending true)))

(defn compile-interface
  "Parses the interface forms of a `deftask` into a base task-map; the interface
   defines the options and positional arguments that will be parsed."
  [task-doc forms]
  (let [initial-state {:consuming :options
                       :option-symbols []
                       :arg-symbols []
                       :task-options []
                       :task-args []
                       :task-doc task-doc}
        final-state (reduce consumer
                            initial-state forms)]
    (when (:pending final-state)
      (throw (ex-info "Missing data in interface definitions"
                      {:state final-state
                       :forms forms})))
    (-> final-state
        (dissoc :consuming :pending :symbol)
        (update :task-options conj ["-h" "--help" "This command summary" :id :help]))))

(defn parse-cli
  [current-task command-line-arguments task-map]
  (cond-let
    :let [{:keys [task-args task-options parse-opts-options]} task-map
          task-name (:name current-task)
          {:keys [in-order]
           :or {in-order false}} parse-opts-options
          positional-specs (compile-positional-specs task-name task-args)
          task-map' (merge task-map
                           {:task-name task-name
                            :positional-specs positional-specs}
                           (cli/parse-opts command-line-arguments task-options :in-order in-order))
          {:keys [arguments options]} task-map']

    ;; Check for help first, as otherwise can get needless errors r.e. missing required positional arguments.
    (:help options)
    (do
      (print-summary task-map' nil)
      (exit 0))

    :let [[positional-arguments arg-errors] (parse-positional-arguments positional-specs arguments)
          errors (concat (:errors task-map')
                         arg-errors)]

    (seq errors)
    (do
      (print-summary task-map' errors)
      (exit 1))

    :else
    ;; Replace :arguments from the raw strings to a map
    (assoc task-map' :arguments positional-arguments)))