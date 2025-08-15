(ns net.lewisship.cli-tools
  "Utilities for create CLIs around functions, and creating tools with multiple sub-commands."
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :as ansi]
            [clojure.string :as string]
            [net.lewisship.cli-tools.impl :as impl :refer [cond-let]]
            [clojure.tools.cli :as cli]
            [net.lewisship.cli-tools.cache :as cache]))

(defn exit
  "An indirect call to System/exit, passing a numeric status code (0 for success, non-zero for
  an error).

  This is provided so that, during testing, when [[set-prevent-exit!]] has been called, the call
  to `exit` will instead throw an exception."
  [status]
  (impl/exit status))

(defn- abort*
  [status messages]
  (apply ansi/perr messages)
  (exit status))

(defn command-path
  "Returns a composed string of the tool name and command path that can be used
  in error messages. This function requires
  global data bound by [[dispatch*]] and returns nil when invoked outside that
  context."
  {:added "0.16.0"}
  []
  (impl/command-path))

(defn abort
  "Invoked when a tool has a runtime failure.  The messages, composed strings, are
  printed to `*err*` and then [[exit]] is invoked.

  By default, the exit status is 1.  If the first message value is a number, it is used
  as the exit status instead."
  {:added    "0.15"
   :arglists '[[status & message]
               [& message]]}
  [& message]
  (if (-> message first number?)
    (abort* (first message) (rest message))
    (abort* 1 message)))

(defn set-prevent-exit!
  "cli-tools will call [[exit]] when help is requested (with a 0 exit status, or 1 for
  a input validation error).  Normally, that results in a call to System/exit, but this function,
  used for testing, allow [[exit]] to throw an exception instead."
  [flag]
  (alter-var-root #'impl/prevent-exit (constantly flag)))

(defn print-errors
  "Prints the errors for the command to `*err*`.

   Ex:

       Error in my-tool my-command: --count is not a number"
  {:added "0.13"}
  [errors]
  (impl/print-errors errors))

(defn best-match
  "Given an input string and a seq of possible values, returns the matching value if it can
  be uniquely identified.

  Values may be strings, symbols, or keywords.

  best-match does a caseless prefix match against the provided values. It returns the single
  value that matches the input. It returns nil if no value matches, or if multiple values match.

  Returns the string/symbol/keyword from values.

  e.g. `:parse-fn #(cli-tools/best-match % #{:red :green :blue :grey})` would parse an input of `red` to
  `:red`, or an input of `b` to `:blue`; `z` matches nothing and returns nil, as would
  `g` which matches multiple values.

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
       (string/join ", ")))

(defmacro defcommand
  "Defines a command.

   A command's _interface_ identifies how to parse command options and positional arguments,
   mapping them to local symbols.

   Commands must always have a docstring; this is part of the `-h` / `--help` summary.

   The returned function is variadic, accepting a number of strings, much
   like a `-main` function. For testing purposes, it may instead be passed a single map,
   a map of options, which bypasses parsing and validation of the arguments.

   Finally, the body is evaluated inside a let that destructures the options and positional arguments into local symbols."
  [fn-name docstring interface & body]
  (assert (simple-symbol? fn-name)
          "defcommand expects a symbol for command name")
  (assert (string? docstring)
          "defcommand requires a docstring")
  (assert (vector? interface)
          "defcommand expects a vector to define the interface")
  (let [symbol-meta        (meta fn-name)
        parsed-interface   (impl/compile-interface  interface)
        {:keys [option-symbols title let-forms validate-cases]} parsed-interface
        command-map-symbol (gensym "command-map-")
        command-name'      (or (:command-name parsed-interface)
                               (name fn-name))
        let-option-symbols (cond-> []
                             (seq option-symbols)
                             (into `[{:keys ~option-symbols} (:options ~command-map-symbol)]))
        symbol-with-meta   (cond-> (assoc symbol-meta
                                          :doc docstring
                                          :arglists '[['& 'args]]
                                          ::impl/command-name command-name')
                             title (assoc ::impl/title title))
        ;; Keys actually used by parse-cli and print-summary
        parse-cli-keys     [:command-args :command-options :parse-opts-options :summary]
        validations        (when (seq validate-cases)
                             `(when-let [message# (cond ~@(impl/invert-tests-in-validate-cases validate-cases))]
                                (print-errors [message#])
                                (exit 1)))]
    `(defn ~fn-name
       ~symbol-with-meta
       [~'& args#]
       (let [~@let-forms
             ;; args# is normally a seq of strings, from *command-line-arguments*, but for testing,
             ;; it can also be a map with key :options
             test-mode?# (impl/command-map? args#)
             command-spec# ~(select-keys parsed-interface parse-cli-keys)]
         (if impl/*introspection-mode*
           command-spec#
           (let [~command-map-symbol (cond
                                       test-mode?#
                                       {:options (first args#)}

                                       impl/*introspection-mode*
                                       command-spec#

                                       :else
                                       (impl/parse-cli ~command-name'
                                                       ~docstring
                                                       args#
                                                       command-spec#))
                 ;; These symbols de-reference the command-map returned from parse-cli.
                 ~@let-option-symbols]
             (when-not test-mode?#
               ~validations)
             ~@body))))))


(def ^{:added "0.16.0"}
  default-tool-options
  "Default tool command line options."
  [["-C" "--color" "Enable ANSI color output"]
   ["-N" "--no-color" "Disable ANSI color output"]
   ["-h" "--help" "This tool summary"]])

(defn- expand-tool-options
  "Expanded dispatch options into tool options, leveraging a cache."
  [options]
  (let [{:keys [cache-dir]} options
        ;; Only include the options that should be part of the digest.
        digest-options (select-keys options [:tool-name
                                             :doc
                                             :namespaces
                                             :groups
                                             :source-dirs])
        cache-dir'     (when cache-dir
                         (fs/expand-home cache-dir))
        digest         (when cache-dir'
                         (cache/classpath-digest digest-options))
        cached         (when digest
                         (cache/read-from-cache cache-dir' digest))
        result         (if cached
                         cached
                         (let [expanded (impl/expand-tool-options options)]
                       (when cache-dir'
                         (cache/write-to-cache cache-dir' digest expanded))
                       expanded))]
    (merge result
           (select-keys options [:tool-name :doc :arguments :tool-summary]))))

(defn dispatch*
  "Called from a tool handler to process remaining command line arguments.

  - dispatch-options - modified dispatch options
  - color-flag - if non-nil, enables or disables ANSI colors before dispatching
  - help - if true, then change the arguments to \"help\", to print tool help

  In the dispatch options map, the tool handler should have set the following:

  - :arguments -- seq of remaining arguments after processing tool-level options
  - :tool-summary -- summary of tool options (used when printing tool help)."
  [dispatch-options color-flag help?]
  (cond
    (some? color-flag)
    (binding [ansi/*color-enabled* color-flag]
      (dispatch* dispatch-options nil help?))

    help?
    (recur (assoc dispatch-options :arguments ["help"]) nil false)

    :else
    (-> dispatch-options expand-tool-options impl/dispatch)))

(defn summarize-specs
  "Converts a tools.cli command specification to a description of the options; this is an enhanced version of
  clojure.tools.cli/summarize that makes use of indentation and ANSI colors.

  Returns a delay (to ensure that ANSI color enabled/disabled options are enforced)."
  {:added "0.16.0"}
  [specs]
  ;; summarize-specs is called before we parse the command line options (-C, -N) that may enable/disable
  ;; ANSI colors, so a delay is used to prevent premature evaluation.
  (delay (impl/summarize-specs specs)))

(defn default-tool-handler
  "Default tool handler, passed the tool options.  The [[default-tool-options]] support enabling or disabling
  ANSI fonts, and requesting top-level help.

  This is the default for the :handler key of dispatch options.

  This function is passed the dispatch options, parses the default tool options, and delegates the rest to [[dispatch*]]."
  {:added "0.16.0"}
  [dispatch-options]
  (let [{:keys [options arguments summary errors]
         :as   result} (cli/parse-opts (:arguments dispatch-options)
                                       default-tool-options
                                       :in-order true
                                       :summary-fn summarize-specs)
        {:keys [color no-color help]} options
        color-flag (cond color true
                         no-color false)]
    (when errors
      (throw (ex-info "Tool parse sanity check" result)))

    (dispatch* (-> dispatch-options
                   (assoc :arguments arguments
                          :tool-summary summary))
               color-flag help)))

(def ^:private default-dispatch-options
  {:cache-dir (or (System/getenv "CLI_TOOLS_CACHE_DIR")
                  "~/.cli-tools-cache")
   :handler   default-tool-handler})

(defn dispatch
  "Locates commands in namespaces, finds the current command
  (as identified by the first command line argument) and processes CLI options and arguments.

  dispatch-options:
  
  - :tool-name (optional, string) - used in command summary and errors
  - :doc (optional, string) - used in help summary
  - :arguments - command line arguments to parse (defaults to `*command-line-args*`)
  - :namespaces - seq of symbols identifying namespaces to search for root-level commands
  - :groups - map of group command (a string) to a group map
  - :handler - function to handle tool-level options, defaults to [[default-tool-handler]]
  - :cache-dir (optional, string) - directory to cache data in, or nil to disable cache
  - :transformer (optional, function) - transforms the root command map
  - :source-dirs (optional, seq of strings) - additional directories related to caching

  The :tool-name option is only semi-optional; in a Babashka script, it will default
  from the `babashka.file` system property, if any. An exception is thrown if :tool-name
  is not provided and can't be defaulted.

  A group map defines a set of commands grouped under a common name.  Its structure:

  - :doc (optional, string) - a short string identifying the purpose of the group
  - :namespaces (seq of symbols, required) - identifies namespaces providing commands in the group
  - :groups (optional, map) - recusive map of groups nested within the group

  dispatch will always add the `net.lewiship.cli-tools.builtins` namespace to the root
  namespace list; this ensures the built-in `help` command is available.

  If option and argument parsing is unsuccessful, then
  an error message is written to \\*err\\*, and the program exits
  with error code 1.

  Caching is enabled by default; this means that a scan of all namespaces is only required on the first
  execution; subsequently, only the single namespace implementing the selected command will need to
  be loaded.  :cache-dir defaults to the value of the CLI_TOOLS_CACHE_DIR environment variable, or
  to the default value `~/.cli-tools-cache`.  If set to nil, then caching is disabled.

  The :source-dirs option is typically used with the :transformer option; the source directories are
  additional directories whose contents should be included by the cache digest (because the transformer
  reads files in those directories).

  The transformer function is passed the dispatch options and the root commands map and returns
  an updated commands map; typically this involves identifying additional commands and adding them
  via [[inject-command]].

  Returns nil."
  [dispatch-options]
  (let [options' (merge {:arguments *command-line-args*}
                        default-dispatch-options
                        dispatch-options)
        {:keys [handler]} options']
    (handler (dissoc options' :handler))))

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
  ```
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
        default-label (when (some? default)
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
                          (fn [i label]
                            (list
                              (when (pos? i)
                                "/")
                              [(when (= label default-label) :bold)
                               label]))
                          all-labels)
                        ") ")]
    (binding [*out* *err*]
      (loop []
        (print full-prompt)
        (flush)
        (let [input (read-line)]
          (cond-let
            (and (string/blank? input)
                 (some? default))
            default

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

                                 (< i n)
                                 ", "

                                 default-label
                                 ", "

                                 (< (count response-maps) 3)
                                 " or "

                                 :else
                                 ", or ")
                               [:italic label]))
                           response-maps)
                         (when default-label
                           (list
                             ", or just enter for the default ("
                             [:bold default-label]
                             ")")))
              (recur))))))))

(defn ^{:added "0.12.0"} ask
  "Ask the user a question with a fixed number of possible responses.

  The prompt is a string (possibly, a composed string) and should
  usually end with a question mark.

  Each response is a map with

  Key    | Type   | Value
  ---    |---     |---
  :label | String | Response entered by user, e.g., \"yes\"
  :value | any    | Value to be returned by `ask`, e.g., true

  A response may also be abbreviated as a single keyword; it will be expanded into
  a map where the :value will be the keyword, and the label
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

  :default - the default value which must correspond to one value (may not be nil)
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
       (throw (ex-info ":force? option is set, but no :default" {:opts opts}))

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

(defn selected-command
  "Returns a map defining the selected command. This will be nil until the end of
  dispatch (i.e., just before the command function is invoked). This can be used
  in the implementation of commands not defined by [[defcommand]]."
  {:added "0.16.0"}
  []
  impl/*command-map*)

(defn- inject
  [commands-map remaining-command-path path-to-here command-map]
  (let [command    (first remaining-command-path)
        remaining' (next remaining-command-path)
        path'      (conj path-to-here command)]
    (cond
      (nil? remaining')
      (assoc commands-map command command-map)

      (contains? commands-map command)
      (update-in commands-map [command :subs]
                 inject remaining' path' command-map)

      :else
      (assoc commands-map command
             {:command-path path'
              :command      command
              :subs         (inject {} remaining' path' command-map)}))))

(defn inject-command
  "Injects a new command into the command root; presumably one not defined via
   [[defcommand]].

  command-path - a seq of command name strings leading to the new command
  command-map - map that defines the command

  The final term of the command path is the name of the command itself.

  A command map has keys:
  - :fn (keyword, required) - identifies function to invoke
  - :doc (string, required) - long description of the command
  - :title (string, optional) - short description of the command
  "
  {:added "0.16.0"}
  [command-root command-path command-map]
  (let [command-map' (assoc command-map
                            :command (last command-path)
                            :command-path command-path)]
    (inject command-root command-path [] command-map')))

