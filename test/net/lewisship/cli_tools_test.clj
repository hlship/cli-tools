(ns net.lewisship.cli-tools-test
  (:require [clj-commons.ansi :as ansi :refer [compose]]
            [clojure.string :as string]
            [clojure.test :refer [deftest is use-fixtures are]]
            [net.lewisship.cli-tools :as cli-tools :refer [defcommand select-option inject-command]]
            net.lewisship.cli-tools.builtins
            net.lewisship.group-ns
            net.lewisship.conflict
            [net.lewisship.cli-tools.impl :as impl]
            [net.lewisship.cli-tools.test :refer [with-err-str capture-result]]
            [net.lewisship.cli-tools.aux :refer [with-exit-errors]]
            [clojure.repl :as repl])
  (:import (java.io BufferedReader StringReader)))

(defn- dispatch
  [options]
  (capture-result
    (cli-tools/dispatch options)))

(use-fixtures
  :once
  (fn [f]
    (binding [impl/*tool-options* {:tool-name "harness"
                                   :cache-dir nil}]
      (f))))

;; An example to test around

(defcommand configure
  "Configures the system for some thing.

  This is more detail.

    This is indented.

  This is not indented."
  [verbose ["-v" "--verbose" "Enable verbose logging"]
   :args
   host ["HOST" "System configuration URL"
         :validate [#(re-matches #"https?://.+" %) "must be a URL"]]
   key-values ["KV-DATA" "Data to configure as KEY=VALUE"
               :parse-fn (fn [s]
                           (when-let [[_ k v] (re-matches #"(.+)=(.+)" s)]
                             [(keyword k) v]))
               :update-fn (fn [m [k v]]
                            (assoc m k v))
               :repeatable true]]
  {:verbose verbose :host host :key-values key-values})


(defcommand collect
  "Collect key and value."
  [:args
   k ["KEY" "Key to set"]
   v ["VAL" "Value to set"]]
  [k v])

(defcommand tool-info
  "Echoes the tool name and root command map keys."
  []
  (println "Tool name:" (cli-tools/tool-name))
  (println "Commands:" (->> (cli-tools/command-root)
                            keys
                            sort
                            (string/join ", "))))

(defn prep-input
  [input]
  (-> (str (if (vector? input)
             (string/join "\n" input)
             input)
           "\n")
      StringReader.
      BufferedReader.))

(defmacro with-input
  [input & body]
  `(binding [*in* (prep-input ~input)]
     ~@body))

(defmacro with-result+err-str
  "Captures output to \\*err\\* and returns a vector of the body's
  result and that output."
  [& body]
  `(let [result# (volatile! nil)
         error#  (with-err-str
                   (vreset! result# (do ~@body)))]
     [@result# error#]))

(deftest success
  (is (= (configure "-v" "http://myhost.com" "fred=flintstone")
         {:verbose    true
          :host       "http://myhost.com"
          :key-values {:fred "flintstone"}}))

  (is (= {:verbose    nil
          :host       "http://myhost.com"
          :key-values {:fred   "flintstone"
                       :barney "rubble"}}
         (configure "http://myhost.com" "fred=flintstone" "barney=rubble"))))

(deftest exit-will-throw-exception-instead
  (is (match? {:status 999}
              (capture-result
                (cli-tools/exit 999)))))


(defn invoke-command
  [& args]
  (dispatch {:tool-name  "harness"
             :namespaces ['net.lewisship.cli-tools-test]
             :arguments  args}))


(deftest tool-options-access
  (is (match? {:status    0
               :out-lines ["Tool name: harness"
                           "Commands: collect, configure, default-variants, help, in-order, set-mode, tool-info, validate"]}
              (invoke-command "tool-info"))))

(deftest unknown-tool-option
  (is (match? {:status 1
               :out    ""
               :err-lines
               ["Error in harness: Unknown option: \"--unknown\""]}
              (binding [ansi/*color-enabled* false]
                (invoke-command "--unknown")))))

(deftest standard-help
  (is (match? {:status 0
               :out    (slurp "test-resources/help.txt")}
              (invoke-command "configure" "-h"))))

(deftest help-with-no-color
  (is (match? {:status 0
               :out    (slurp "test-resources/help-with-no-color.txt")}
              (invoke-command "-N" "-h"))))

(deftest help-with-color-enabled
  (binding [ansi/*color-enabled* false]
    (is (match? {:status 0
                 :out    (slurp "test-resources/help-with-color-enabled.txt")}
                (invoke-command "-C" "-h")))))

(deftest unknown-option
  (is (match? {:status 1
               :err    (slurp "test-resources/unknown-option.txt")}
              (invoke-command "configure" "--debug"))))

(deftest pos-arg-validation-failure
  (is (match? {:status 1
               :err    (slurp "test-resources/pos-arg-validation-failure.txt")}
              (invoke-command "configure" "myhost.com" "fred=flinstone"))))

(deftest insuffient-values
  (is (match? {:status 1
               :err    (slurp "test-resources/insufficient-values.txt")}
              (invoke-command "collect" "just-key"))))

(deftest excess-values
  (is (match? {:status 1
               :err    (slurp "test-resources/excess-values.txt")}
              (invoke-command "collect" "the-key" "the-value" "the-extra"))))


(defcommand default-variants
  "Different option defaults."
  [foo [nil "--foo FOO" "Foo option" :default "abc"]
   bar [nil "--bar BAR" "Bar option" :default-fn (constantly "xyz")]
   bazz [nil "--bazz BAZZ" "Bazz option" :default :bazz :default-desc "Bazzy"]]
  [foo bar bazz])

(deftest option-defaults
  (is (match? {:status 0
               :out    (slurp "test-resources/option-defaults.txt")}
              (invoke-command "default-variants" "-h"))))

(defcommand in-order
  "Test of :in-order option"
  [verbose ["-v" "--verbose"]
   :args
   command ["COMMAND" "Remote command to execute"]
   args ["ARGS" "Arguments to remote command"
         :optional true
         :repeatable true]
   :in-order true
   :title "Execute remote command"]
  {:command command
   :args    args
   :verbose verbose})

(deftest in-order-arguments
  (is (= {:command "ls"
          :args    ["-lR"]
          :verbose true}
         ;; Without :in-order true, the -lR is flagged as an error
         (in-order "-v" "ls" "-lR"))))


(deftest help-with-default-and-explicit-summary
  (is (match? {:status 0
               :out    (slurp "test-resources/tool-help.txt")}
              (dispatch {:tool-name  "test-harness"
                         :doc        "Example commands as part of unit test suite.

  Even this docstring is part of the test."
                         :namespaces '[net.lewisship.example-ns]
                         :arguments  ["help"]}))))

(deftest group-help-defaults-from-first-ns-meta
  (is (match? {:status 0
               :out    (slurp "test-resources/tool-help-group-default.txt")}
              (dispatch {:tool-name "test-harness"
                         :groups
                         {"group" {:namespaces '[net.lewisship.group-default-ns
                                                 net.lewisship.example-ns]}}
                         :arguments ["help"]}))))

(deftest group-help-full
  (is (match? {:status 0
               :out    (slurp "test-resources/tool-help-group-full.txt")}
              (dispatch {:tool-name "test-harness"
                         :groups
                         {"group" {:namespaces '[net.lewisship.group-default-ns
                                                 net.lewisship.example-ns]}}
                         :arguments ["help" "-c" "all"]}))))

(deftest help-with-search-term
  (is (match?
        {:status 0
         :out    (slurp "test-resources/tool-help-search.txt")}
        (dispatch {:tool-name  "test-harness"
                   :namespaces '[net.lewisship.example-ns]
                   :arguments  ["help" "EXP"]}))))

(deftest help-with-search-term-no-match
  (is (match? {:status 0
               :out    (slurp "test-resources/tool-help-search-no-match.txt")}
              (dispatch {:tool-name  "test-harness"
                         :namespaces '[net.lewisship.example-ns]
                         :arguments  ["help" "Xyzzyx"]}))))

(deftest use-of-command-ns-meta
  (is (match? {:status 0
               :out    (slurp "test-resources/combo-help.txt")}
              (dispatch {:tool-name  "combo"
                         :namespaces '[net.lewisship.cli-tools.colors]
                         :arguments  ["-h"]}))))

(defcommand set-mode
  "Sets the execution mode"
  [mode ["-m" "--mode MODE" (str "Execution mode, one of " mode-names)
         :parse-fn keyword
         :validate [allowed-modes (str "Must be one of " mode-names)]]
   :let [allowed-modes #{:batch :async :real-time}
         mode-names (->> allowed-modes (map name) sort (string/join ", "))]]
  {:mode mode})


(deftest let-directive
  (is (match? {:status 1
               :err    (slurp "test-resources/let-directive.txt")}
              (invoke-command "set-mode" "-m" "unknown"))))

(defcommand validate
  "validate command"
  [a ["-a" "--alpha S"]
   n ["-n" "--numeric N"]
   :validate [(some? a) "--alpha is required"
              (some? n) "--numeric is required"
              (not (and a n)) "--alpha and --numeric are exclusive"]]
  {:alpha   a
   :numeric n})

(deftest validate-directive

  (with-exit-errors ["--numeric is required"]
                    (validate "-a" "alpha"))

  (with-exit-errors ["--alpha and --numeric are exclusive"]
                    (validate "-a" "alpha" "-n" "123")))

(deftest map-bypasses-validations
  (is (= {:mode ::unexpected}
         (set-mode {:mode ::unexpected})))

  (is (= {:alpha   true
          :numeric true}
         (validate {:a true :n true}))))


;; This test fails under Babashka (repl/doc returns empty string) and not sure why.
;; There are some subtle differences in now macros are expanded when meta-data is involved.
;; Just ignoring it for now.
(deftest generate-correct-meta
  (is (= (slurp "test-resources/set-mode-doc.txt")
         (with-out-str
           (repl/doc set-mode)))))

(deftest best-match
  (let [colors   #{:red :green :blue}
        commands #{:help :frob-widget :gnip-gnop :setup :teardown :tip-top :tele-type :go}]
    (are [input values expected] (= expected (cli-tools/best-match input values))

      "r" colors :red                                       ; multiple matches
      "red" colors :red                                     ; exact match
      "Red" colors :red                                     ; caseless

      "b" colors :blue                                      ; partial match
      "z" colors nil                                        ; no match

      "exact" #{:exact :exact-is-prefix} :exact

      "h" commands :help
      "g" commands nil
      "gn" commands :gnip-gnop
      "frob-wg" commands nil
      "f" commands :frob-widget
      "TI" commands :tip-top)))

(deftest sorted-name-list
  (is (= "foxtrot, tango, whiskey"
         (cli-tools/sorted-name-list [:whiskey :tango :foxtrot]))))

(defn exec-group
  [& args]
  (dispatch {:tool-name  "group-test"
             :namespaces '[net.lewisship.example-ns]
             :groups     {"group" {:namespaces '[net.lewisship.group-ns]
                                   :doc        "Grouped commands"
                                   :groups     {"nested" {:namespaces '[net.lewisship.cli-tools.group-nested]
                                                          :doc        "Nested commands inside group"}}}}
             :arguments  args}))

(deftest help-with-default-and-explicit-summary-grouped
  (is (match? {:status 0
               :out    (slurp "test-resources/tool-help-grouped.txt")}
              (exec-group "help" "-c" "all"))))

(deftest help-for-group
  (let [expected (slurp "test-resources/sub-group-help.txt")]
    (is (match? {:status 0
                 :out    expected}
                (exec-group "group" "-h")))

    (is (match? {:status 0
                 :out    expected}
                (exec-group "gr" "--help")))))

(comment
  (->> (dispatch {:tool-name "test-harness"
                  :groups
                  {"group" {:namespaces '[net.lewisship.group-default-ns
                                          net.lewisship.example-ns]}}
                  :arguments ["help" "-c" "all"]})
       :out
       (spit "test-resources/tool-help-grouped.txt"))

  )

(deftest can-find-a-grouped-command
  (is (match? {:out "echo: fancy\n"}
              (exec-group "group" "echo" "fancy"))))

(deftest suggest-help-when-name-incomplete
  (is (match? {:status 1
               :err    (slurp "test-resources/help-incomplete.txt")}
              (exec-group "gr"))))

(deftest suggest-help-when-name-incomplete-nested
  (is (match? {:status 1
               :err    (slurp "test-resources/help-incomplete-nested.txt")}
              (exec-group "gr" "nested"))))


(deftest can-use-group-abbreviations
  (is (match? {:out "echo: abbreviated\n"}
              (exec-group "g" "ec" "abbreviated"))))

(deftest reports-group-match-failure
  (binding [ansi/*color-enabled* false]
    (is (match? {:err "group-test: group e could match echo or edit; use group-test group --help (or -h) to list commands\n"}
                (exec-group "g" "e" "multiple")))

    (is (match? {:err "group-test: echo is not a command, expected default, explicit, group (or one other); use group-test help to list commands\n"}
                (exec-group "echo" "wrong-level")))))

(deftest select-option-no-default
  (let [input-values #{:csv :json :yaml :edn}
        [short long desc & {:keys [parse-fn validate]}]
        (select-option "-f" "--format FORMAT" "Output format:" input-values)
        [validate-fn validate-msg] validate]
    (is (= "-f" short))
    (is (= "--format FORMAT" long))
    (is (= "Output format: csv, edn, json, yaml"
           desc))

    (is (= :yaml (parse-fn "y")))
    (is (= nil (parse-fn "x")))

    (is (= some? validate-fn))
    (is (= "Must be one of csv, edn, json, yaml" validate-msg))))

(deftest select-option-with-default
  (let [[_ _ _ & {:keys [default default-desc]}]
        (select-option "-f" "--format FORMAT" "Output format:"
                       #{:csv :json :yaml :edn}
                       :default :json)]
    (is (= :json default))
    (is (= "json" default-desc))))

(deftest select-option-passes-through-other-key-values
  (let [[_ _ _ & {:as kvs}]
        (select-option "-f" "--format FORMAT" "Output format:"
                       #{:csv :json :yaml :edn}
                       :default :json
                       :k1 :value-1
                       :k2 :value-2)]
    (is (= {:k1 :value-1
            :k2 :value-2}
           (dissoc kvs :parse-fn :validate :default :default-desc)))))

(deftest simple-ask-without-default
  (is (match? [true "Prompt? (yes/no) "]                    ; Note: no bold
              (with-result+err-str
                (with-input "y"
                            (cli-tools/ask "Prompt?" cli-tools/yes-or-no))))))

(deftest simple-ask-with-default
  (is (match? [true (compose "Prompt? (" [:bold "yes"] "/no) ")]
              (with-result+err-str
                (with-input ""
                            (cli-tools/ask "Prompt?" cli-tools/yes-or-no {:default true}))))))

(deftest simple-ask-with-false-default
  (is (match? [false (compose "Prompt? (yes/" [:bold "no"] ") ")]
              (with-result+err-str
                (with-input ""
                            (cli-tools/ask "Prompt?" cli-tools/yes-or-no {:default false}))))))

(deftest ask-with-keywords
  (let [expected (compose "Sort order? (name/address/phone) "
                          "Input '"
                          [:yellow "adz"]
                          "' not recognized; enter "
                          [:italic "name"]
                          ", "
                          [:italic "address"]
                          ", or "
                          [:italic "phone"]
                          "\n"
                          "Sort order? (name/address/phone) ")]
    (is (match? [:address expected]
                (with-result+err-str
                  (with-input ["adz" "add"]
                              (cli-tools/ask "Sort order?"
                                             [:name :address :phone])))))))

(deftest ask-invalid-just-two
  (let [expected (compose "Really? (" [:bold "yes"] "/no) "
                          "Input '"
                          [:yellow "x"]
                          "' not recognized; enter "
                          [:italic "yes"]
                          ", "
                          [:italic "no"]
                          ", or just enter for the default ("
                          [:bold "yes"]
                          ")\n"
                          "Really? (" [:bold "yes"] "/no) ")]
    (is (match? [true expected]
                (with-result+err-str
                  (with-input ["x" ""]
                              (cli-tools/ask "Really?"
                                             cli-tools/yes-or-no
                                             {:default true})))))))

(deftest ask-with-keywords-and-default
  (let [expected (compose "Sort order? (" [:bold "name"] "/address/phone) "
                          "Input '"
                          [:yellow "adz"]
                          "' not recognized; enter "
                          [:italic "name"]
                          ", "
                          [:italic "address"]
                          ", "
                          [:italic "phone"]
                          ", or just enter for the default ("
                          [:bold "name"]
                          ")\n"
                          "Sort order? (" [:bold "name"] "/address/phone) ")]
    (is (match? [:name expected]
                (with-result+err-str
                  (with-input ["adz" ""]
                              (cli-tools/ask "Sort order?"
                                             [:name :address :phone]
                                             {:default :name})))))))

(deftest ask-with-force
  (is (match? [:name ""]
              (with-result+err-str
                (cli-tools/ask "Sort order?"
                               [:name :address :phone]
                               {:default :name
                                :force?  true})))))

(deftest ask-with-force-but-no-default
  (when-let [e (is (thrown? Exception
                            (cli-tools/ask "Sort order?"
                                           [:name :address :phone]
                                           {:force? true})))]
    (is (= ":force? option is set, but no :default" (ex-message e)))
    (is (match? {:opts {:force? true}}
                (ex-data e)))))

(deftest ask-when-default-does-not-match-a-possible-response
  (when-let [e (is (thrown? Exception
                            (cli-tools/ask "Really?"
                                           cli-tools/yes-or-no
                                           {:default :maybe})))]
    (is (= ":default does not correspond to any value" (ex-message e)))
    (is (match? {:opts      {:default :maybe}
                 :responses cli-tools/yes-or-no}
                (ex-data e)))))

(deftest inject-command-new
  (is (match? {"foo"
               {:command      "foo"
                :command-path ["foo"]
                ::custom      1}}
              (inject-command nil ["foo"] {::custom 1}))))

(deftest inject-will-merge
  (is (match? {"foo"
               {:command      "foo"
                :command-path ["foo"]
                ::custom      2
                ::extra       3}}
              (-> nil
                  (inject-command ["foo"] {::custom 1})
                  (inject-command ["foo"] {::custom 2
                                           ::extra  3})))))

(deftest inject-builds-groups
  (is (match? {"foo"
               {:command      "foo"
                :command-path ["foo"]
                :subs         {"bar"  {:command      "bar"
                                       :command-path ["foo" "bar"]}
                               "bazz" {:command      "bazz"
                                       :command-path ["foo" "bazz"]}}}}
              (-> nil
                  (inject-command ["foo" "bar"] {})
                  (inject-command ["foo" "bazz"] {})))))
