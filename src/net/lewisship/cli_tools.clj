(ns net.lewisship.cli-tools
  "Utilities for create CLIs around functions, and creating tools with multiple sub-commands."
  (:require [clojure.spec.alpha :as s]
            [net.lewisship.cli-tools.impl :as impl :refer [cond-let]]
            [clojure.string :as str]))

(defn exit
  "An indirect call to System/exit, passing a numeric status code (0 for success, non-zero for
  an error).

  This is provided so that, during testing, when [[set-prevent-exit!]] has been called, the call
  to `exit` will instead throw an exception."
  [status]
  (impl/exit status))

(defn set-prevent-exit!
  "Normally, after displaying a command summary, `System/exit` is called (with 0 if for --help,
   or 1 if a validation error).

   For testing purposes, this can be prevented; instead, an exception is thrown,
   with message \"Exit\" and ex-data {:status <status>}."
  [flag]
  (alter-var-root #'impl/prevent-exit (constantly flag)))

(defn print-summary
  "Prints the command's summary to `*out*`; partially generated by clojure.tools.cli, and then
  enhanced with more information about positional command line arguments.

  This is often used when a command performs additional validation of its arguments
  and needs to output the summary and errors on failure.

  Uses the command map that is available in `defcommand` function
  (using the :as clause).

  errors is a seq of strings to display as errors."
  [command-map errors]
  (impl/print-summary command-map errors))

(defn best-match
  "Given an input string and a seq of possible values, returns the matching value if it can
  be uniquely identified.

  Values may be strings, symbols, or keywords.

  best-match does a caseless substring match against the provided values. It returns the single
  value that matches the input. It returns nil if no value matches, or if multiple values match.

  Some special handling for the `-` character; the input value is split on `-` and turned into
  a generous regular expression that matches the substring on either side of the `-` as well as the `-`
  itself.

  Returns the string/symbol/keyword from values.

  e.g. `:parse-fn #(cli-tools/best-match % #{:red :green :blue})` would parse an input of `red` to
  `:red`, or an input of `b` to `:blue`; `z` matches nothing and returns nil, as would
  `e` which matches multiple values.

  Expects symbols and keywords to be unqualified."
  [input values]
  (let [m (reduce (fn [m v]
                    (assoc m (name v) v))
                  {}
                  values)
        matches (impl/find-matches input (keys m))]
    (when (= 1 (count matches))
      (get m (first matches)))))

(defn sorted-name-list
  "Converts a seq of strings, keywords, or symbols (as used with [[best-match]]) to a comma-separated
  string listing the values. This is often used with help summary or error messages."
  [values]
  (->> values
       (map name)
       sort
       (str/join ", ")))

(defmacro defcommand
  "Defines a command.

   A command's _interface_ identifies how to parse command options and positional arguments,
   mapping them to local symbols.

   Commands must always have a docstring; this is part of the `-h` / `--help` summary.

   The returned function is variadic, accepting a number of strings, much
   like a `-main` function. For testing purposes, it may instead be passed a single map,
   a map of options, which bypasses parsing and validation of the arguments, and is used only for testing.

   Finally, the body is evaluated inside a let that destructures the options and positional arguments into local symbols."
  [command-name docstring interface & body]
  (assert (simple-symbol? command-name)
          "defcommand expects a symbol for command name")
  (assert (string? docstring)
          "defcommand requires a docstring")
  (assert (vector? interface)
          "defcommand expects a vector to define the interface")
  (let [symbol-meta (meta command-name)
        parsed-interface (impl/compile-interface docstring interface)
        {:keys [option-symbols command-map-symbol command-summary let-forms validate-cases]
         :or {command-map-symbol (gensym "command-map-")}} parsed-interface
        command-name' (or (:command-name parsed-interface)
                          (name command-name))
        let-option-symbols (cond-> []
                             (seq option-symbols)
                             (into `[{:keys ~option-symbols} (:options ~command-map-symbol)]))
        symbol-with-meta (cond-> (assoc symbol-meta
                                        :doc docstring
                                        :arglists '[['& 'args]]
                                        ::impl/command-name command-name')
                           command-summary (assoc ::impl/command-summary command-summary))
        ;; Keys actually used by parse-cli and print-summary
        parse-cli-keys [:command-args :command-options :parse-opts-options :command-doc :summary]
        validations (when (seq validate-cases)
                      `(when-let [message# (cond ~@(impl/invert-tests-in-validate-cases validate-cases))]
                         (print-summary ~command-map-symbol [message#])
                         (exit 1)))]
    `(defn ~command-name
       ~symbol-with-meta
       [~'& args#]
       (let [~@let-forms
             ;; args# is normally a seq of strings, from *command-line-arguments*, but for testing,
             ;; it can also be a map with key :options
             test-mode?# (impl/command-map? args#)
             ~command-map-symbol (if test-mode?#
                                   {:options (first args#)}
                                   (impl/parse-cli ~command-name'
                                                   args#
                                                   ~(select-keys parsed-interface parse-cli-keys)))
             ~@let-option-symbols]
         (when-not test-mode?#
           ~validations)
         ~@body))))

(defn- source-of
  [v]
  (str (-> v meta :ns ns-name) "/" (-> v meta :name)))

(defn- resolve-ns
  [ns-symbol]
  (if-let [ns-object (find-ns ns-symbol)]
    ns-object
    (throw (RuntimeException. (format "namespace %s not found (it may need to be required)" (name ns-symbol))))))

(defn- namespace->category
  [ns-symbol]
  (let [ns      (resolve-ns ns-symbol)
        ns-meta (meta ns)]
    {:category      ns-symbol
     :ns            ns
     :command-group (:command-group ns-meta)
     :label         (:command-category ns-meta (name ns-symbol))
     :order         (:command-category-order ns-meta 0)}))

(defn locate-commands
  "Passed a seq of symbols identifying *loaded* namespaces, this function
  locates commands, functions defined by [[defcommand]].

  Normally, this is called from [[dispatch]] and is only needed when calling [[dispatch*]] directly.

  Returns a tuple: the command categories map, and the command map."
  [namespace-symbols]
  (let [categories     (map namespace->category namespace-symbols)
        ;; Each category that is a command group gets a psuedo command
        group-commands (->> categories
                            (filter :command-group)
                            (reduce (fn [m category-map]
                                      (let [{:keys [command-group]} category-map]
                                        ;; TODO: Check for conflicts
                                        ;; Currently, we only allow two levels of nesting: top level, and directly
                                        ;; within a group. This is the first place that would change if we allowed groups
                                        ;; within groups.
                                        (assoc m command-group {:command-path   [command-group]
                                                                :group-category category-map})))
                                    {}))
        f              (fn [m category-map]
                         (let [{:keys [category command-group ns]} category-map
                               base-path (cond-> []
                                                 command-group (conj command-group))]
                           (->> ns
                                ns-publics
                                ;; Iterate over the public vars of the namespace
                                vals
                                (reduce (fn [m v]
                                          (let [command-name (-> v meta ::impl/command-name)]
                                            (cond-let
                                              :let [command-name (-> v meta ::impl/command-name)]

                                              ;; Not a defcommand?
                                              (nil? command-name)
                                              m

                                              :let [command-path (conj base-path command-name)
                                                    conflict (get-in m command-path)]

                                              conflict
                                              (let [command-var (:var conflict)
                                                    where       (if command-var
                                                                  (source-of command-var)
                                                                  (str "namespace " (name (get-in conflict [:group-category :category]))))]
                                                (throw (RuntimeException. (format "command %s defined by %s conflicts with %s"
                                                                                  (str/join " " command-path)
                                                                                  (source-of v)
                                                                                  where))))
                                              :else
                                              (assoc-in m command-path {:category     category
                                                                        :command-name command-name ; name within group
                                                                        :command-path command-path
                                                                        :var          v}))))
                                        m))))
        commands       (reduce f group-commands categories)]
    [categories commands]))

(defn dispatch*
  "Invoked by [[dispatch]] after namespace and command resolution.

  This can be used, for example, to avoid including the builtin help command
  (or when providing an override).

  options:
  
  - :tool-name - used in command summary and errors
  - :tool-doc - used in command summary
  - :arguments - seq of strings; first is name of command, rest passed to command
  - :categories - seq of maps describing the command categories (see [[locate-commands]])
  - :commands - seq of command maps (see [[locate-commands]])

  Each namespace forms a command category, represented as a map with keys:
  - :category - symbol identifying the namespace
  - :command-group string - optional, from :command-group metadata on namespace, groups commands with a prefix name
  - :label - string (from :command-category metadata on namespace), defaults to the namespace name
  - :order - number (from :command-category-order metadata on namespace), defaults to 0

  In the `help` command summary, the categories are sorted into ascending order by :order,
  then by :label. Individual commands are listed under each category, in ascending alphabetic order.

  All options are required.

  Returns nil."
  [options]
  (impl/dispatch options))

(defn expand-dispatch-options
  "Called by [[dispatch]] to expand the options before calling [[dispatch*]].
  Some applications may call this instead of `dispatch`, modify the results, and then
  invoke `dispatch*`."
  [options]
  (let [{:keys [namespaces arguments tool-name tool-doc flat]} options
        tool-name'  (or tool-name
                        (impl/default-tool-name)
                        (throw (ex-info "No :tool-name specified" {:options options})))
        _           (when-not (seq namespaces)
                      (throw (ex-info "No :namespaces specified" {:options options})))
        namespaces' (cons 'net.lewisship.cli-tools.builtins namespaces)
        _           (run! require namespaces')
        [command-categories commands] (locate-commands namespaces')]
    {:tool-name  tool-name'
     :tool-doc   (or tool-doc
                     (some-> namespaces first find-ns meta :doc))
     :flat       (boolean flat)
     :categories command-categories
     :commands   commands
     :arguments  (or arguments *command-line-args*)}))

(defn dispatch
  "Locates commands in namespaces, finds the current command
  (as identified by the first command line argument) and processes CLI options and arguments.

  options:
  
  - :tool-name (optional, string) - used in command summary and errors
  - :tool-doc (optional, string) - used in help summary
  - :arguments - command line arguments to parse (defaults to `*command-line-args*`)
  - :namespaces - symbols identifying namespaces to search for commands
  - :flat (optional, boolean) - if true, then the default help will be flat (no categories)

  The :tool-name option is only semi-optional; in a Babashka script, it will default
  from the `babashka.file` system property, if any. An exception is thrown if :tool-name
  is not provided and can't be defaulted.

  The default for :tool-doc is the docstring of the first namespace.

  dispatch will load any namespaces specified, then scan those namespaces to identify commands.
  It also adds a `help` command from this namespace.

  If option and argument parsing is unsuccessful, then
  a command usage summary is printed, along with errors, and the program exits
  with error code 1.

  dispatch simply loads and scans the namespaces, adds the `help` command, then calls [[dispatch*]].

  Returns nil."
  [options]
  (-> options
      expand-dispatch-options
      dispatch*))

(s/def ::dispatch-options (s/keys :req-un [::namespaces]
                                  :opt-un [::tool-name ::tool-doc ::arguments ::flat]))
(s/def ::non-blank-string (s/and string?
                                 #(not (str/blank? %))))
(s/def ::tool-name ::non-blank-string)
(s/def ::tool-doc string?)
(s/def ::arguments (s/coll-of string?))
(s/def ::namespaces (s/coll-of simple-symbol?))
(s/def ::flat boolean?)

;; dispatch doesn't actually return
(s/fdef dispatch :args (s/cat :options ::dispatch-options))

(defn select-option
  "Builds a standard option spec for selecting from a list of possible values.
  Uses [[best-match]] to parse the user-supplied value (allowing for
  reasonable abbeviations).

  Following the input values is a list of key value pairs; the :default
  key, if non-nil, should be a member of input-values and will generate
  :default and :default-desc keys in the option spec.

  Adds :parse-fn and :validate keys to the returned option spec,
  as well as :default and :default-desc.
  Additional key/value pairs are passed through as-is.

  Usage (as part of a command's interface):

  ```
  ... format (select-option
                 \"-f\" \"--format FORMAT\" \"Output format:\"
                 #{:plain :csv :tsv :json :edn}) ...
  "
  {:added "0.10"}
  [short-opt long-opt desc-prefix input-values & {:keys [default] :as kvs}]
  (let [input-values-list (sorted-name-list input-values)
        extra-kvs         (reduce into [] (dissoc kvs :default))]
    (cond-> [short-opt long-opt (str desc-prefix " " input-values-list)
             :parse-fn #(best-match % input-values)
             :validate [some? (str "Must be one of " input-values-list)]]
            default (conj :default default
                          :default-desc (name default))
            (seq extra-kvs) (into extra-kvs))))
