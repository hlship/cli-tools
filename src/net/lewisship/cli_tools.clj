(ns net.lewisship.cli-tools
  "Utilities for create CLIs around functions, and creating tools with multiple sub-commands."
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :as ansi]
            [clojure.string :as string]
            [net.lewisship.cli-tools.impl :as impl :refer [cond-let]]
            [net.lewisship.cli-tools.cache :as cache]
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
  "Prints the command's summary to `*err*`; partially as generated by clojure.tools.cli, and then
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
  (let [m       (reduce (fn [m v]
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
  (let [symbol-meta        (meta command-name)
        parsed-interface   (impl/compile-interface docstring interface)
        {:keys [option-symbols command-map-symbol command-summary let-forms validate-cases]
         :or   {command-map-symbol (gensym "command-map-")}} parsed-interface
        command-name'      (or (:command-name parsed-interface)
                               (name command-name))
        let-option-symbols (cond-> []
                             (seq option-symbols)
                             (into `[{:keys ~option-symbols} (:options ~command-map-symbol)]))
        symbol-with-meta   (cond-> (assoc symbol-meta
                                          :doc docstring
                                          :arglists '[['& 'args]]
                                          ::impl/command-name command-name')
                             command-summary (assoc ::impl/command-summary command-summary))
        ;; Keys actually used by parse-cli and print-summary
        parse-cli-keys     [:command-args :command-options :parse-opts-options :command-doc :summary]
        validations        (when (seq validate-cases)
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
     ;; :ns is removed before being written to cache
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
                                        ;; Currently, we only allow two levels of nesting: top level, and directly
                                        ;; within a group. This is the first place that would change if we allowed groups
                                        ;; within groups.
                                        (assoc m command-group {:command-path   [command-group]
                                                                :group-category (dissoc category-map :ns)})))
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
                                                                (str (symbol command-var))
                                                                (str "namespace " (name (get-in conflict [:group-category :category]))))]
                                              (throw (RuntimeException. (format "command %s defined by %s conflicts with %s"
                                                                                (str/join " " command-path)
                                                                                (str (symbol v))
                                                                                where))))
                                            :else
                                            (assoc-in m command-path {:category     category
                                                                      :command-name command-name ; name within group
                                                                      :command-path command-path
                                                                      :command-summary
                                                                      (impl/extract-command-summary v)
                                                                      :var          (symbol v)})))
                                        m))))
        commands       (reduce f group-commands categories)
        categories'    (map #(dissoc % :ns) categories)]
    [categories' commands]))

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

(defn- expand-dispatch-options*
  [options]
  (let [{:keys [namespaces tool-name tool-doc flat]} options
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
     :commands   commands}))

(defn expand-dispatch-options
  "Called by [[dispatch]] to expand the options before calling [[dispatch*]].
  Some applications may call this instead of `dispatch`, modify the results, and then
  invoke `dispatch*`."
  [options]
  (let [{:keys [cache-dir arguments]} options
        ;; Don't include everything when building the digest, especially the command line arguments@
        options' (dissoc options :arguments :cache-dir)
        result   (if-not cache-dir
                   (expand-dispatch-options* options')
                   (let [cache-dir' (fs/expand-home cache-dir)
                         digest     (cache/classpath-digest options')
                         cached     (cache/read-from-cache cache-dir' digest)]
                     (if cached
                       cached
                       (let [full (expand-dispatch-options* options)]
                         (cache/write-to-cache cache-dir' digest full)
                         full))))]
    (assoc result :arguments (or arguments *command-line-args*))))

(def ^:private default-options
  {:cache-dir (or (System/getenv "CLI_TOOLS_CACHE_DIR")
                  "~/.cli-tools-cache")})

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
  It also adds a `help` command from the net.lewisship.cli-tools namespace.

  If option and argument parsing is unsuccessful, then
  a command usage summary is printed, along with errors, and the program exits
  with error code 1.

  dispatch simply loads and scans the namespaces (or obtains the necessary data from the
  cache), adds the `help` command, and finally calls [[dispatch*]].

  Returns nil."
  [options]
  (-> (merge default-options options)
      expand-dispatch-options
      dispatch*))

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

(defn- expand-response
  [response]
  (cond

    (keyword? response)
    {:value response
     :label (name response)}

    (map? response)
    response

    :else
    (throw (ex-info "unexpected response value" {:response response}))))

(defn- ask-prompt
  [prompt response-maps default]
  (let [label->value  (reduce (fn [m {:keys [label value]}]
                                (assoc m label value))
                              {}
                              response-maps)
        all-labels    (map :label response-maps)
        ;; When there's a default that corresponds to multiple labels, use the longest
        ;; one.
        default-label (when default
                        (->> response-maps
                             (keep #(when (= default (:value %))
                                      (:label %)))
                             (sort-by #(-> % .length))
                             last))
        n             (-> response-maps count dec)
        full-prompt   (ansi/compose
                        prompt
                        " ("

                        (map-indexed
                          (fn [i {:keys [label value]}]
                            (list
                              (when (pos? i)
                                "/")
                              [(when (= label default-label) :bold)
                               label]))
                          response-maps)
                        ") ")]
    (binding [*out* *err*]
      (loop []
        (print full-prompt)
        (flush)
        (let [input (read-line)]
          (cond-let
            (and (string/blank? input)
                 default)
            default

            (str/blank? input)
            (recur)

            :let [match (best-match input all-labels)]

            match
            (get label->value match)

            :else
            (do
              (ansi/perr "Input '" [:yellow input] "' not recognized; enter "
                         (map-indexed
                           (fn [i {:keys [label]}]
                             (list
                               (cond
                                 (zero? i)
                                 nil

                                 default
                                 ", "

                                 (< i n)
                                 ", "

                                 :else
                                 "or ")
                               [:italic label]))
                           response-maps)
                         (when default
                           (list
                             ", or just enter for the default ("
                             [:bold default-label]
                             ")")))
              (recur))))))))

(defn ^{:added "0.12.0"} ask
  "Ask the user a quesion with a fixed number of possible responses.

  The prompt is a string (possibly, a composed string) and should
  usually end with a question mark.

  Each response is a map with

  Key    | Type   | Value
  ---    |---     |---
  :label | String | Response entered by user, e.g., \"yes\"
  :value | any    | Value to returned by `ask`, e.g., true

  A response may be a keyword; the :value will be the keyword, and the label
  will simply be the name of the keyword.

  Ex:

      (ask \"Are you sure?\" cli/yes-or-no {:default true})

  Will prompt with:

      Are you sure? (yes/no)

  With \"yes\" in bold.


  The prompt is written to `*err*`.

  The :value is typically unique, but this is not enforced, and it can be
  useful to have distinct labels map to the same output value.

  The user is allowed to enter a shorter input, if that shorter input
  (via [[best-match]]) uniquely identifies a label.

  Options:

  :default - the default value which must correspond to one value
  :force? - if true, then the user is not prompted and the default (which must be non-nil)
    is returned

  The default, if any, is returned when the user simply hits enter (enters a blank string).

  The user input must correspond to a label; if not, a warning is printed and the user
  is again prompted.

  Once a label is identified, `ask` returns the corresponding value."
  ([prompt responses]
   (ask prompt responses nil))
  ([prompt responses opts]
   (let [response-maps (map expand-response responses)
         {:keys [default force?]} opts]
     (cond
       (and force? (nil? default))
       (throw (ex-info ":force is set, but no :default" {:opts opts}))

       (and default (not (contains? (->> response-maps (map :value) set) default)))
       (throw (ex-info ":default does not correspond to any value"
                       {:opts      opts
                        :responses response-maps}))

       force?
       default

       :else
       (ask-prompt prompt
                   response-maps default)))))

(def ^{:added "0.12.0"} yes-or-no
  "For use with [[ask]], provides responses 'yes' (true) and 'no' (false)."
  [{:label "yes"
    :value true}
   {:label "no"
    :value false}])
