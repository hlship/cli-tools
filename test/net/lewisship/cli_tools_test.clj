(ns net.lewisship.cli-tools-test
  (:require [clj-commons.ansi :as ansi :refer [compose]]
            [clojure.string :as string]
            [clojure.test :refer [deftest is use-fixtures are]]
            [net.lewisship.cli-tools :as cli :refer [defcommand dispatch select-option]]
            net.lewisship.cli-tools.builtins
            net.lewisship.group-ns
            net.lewisship.conflict
            matcher-combinators.clj-test                    ;; to enable (is (match? ..))
            [net.lewisship.cli-tools.impl :as impl]
            [clojure.repl :as repl])
  (:import
    (java.io BufferedReader StringReader StringWriter)))

(cli/set-prevent-exit! true)

(use-fixtures
  :once
  (fn [f]
    (binding [impl/*tool-options* {:tool-name "harness"}]
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

(defn with-err-str*
  [f]
  (let [w (StringWriter.)]
    (binding [*err* w]
      (f))
    (str w)))

(defmacro with-err-str
  [& body]
  `(with-err-str* (fn [] ~@body)))

(defmacro with-exit
  [expected & body]
  `(with-err-str
     (when-let [e# (is (~'thrown? Exception ~@body))]
       (is (= "Exit" (ex-message e#)))
       (is (= {:status ~expected} (ex-data e#))))))

(defmacro with-exit-errors
  [expected-errors & body]
  `(let [*errors# (atom nil)]
     (with-redefs [cli/print-errors (fn [errors#]
                                      (reset! *errors# errors#))]
       (with-exit 1 ~@body))
     (is (= @*errors# ~expected-errors))))

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
  (with-exit 999
             (cli/exit 999)))


(defn invoke-command
  [& args]
  (dispatch {:tool-name  "harness"
             :namespaces ['net.lewisship.cli-tools-test]
             :arguments  args}))

(deftest standard-help
  (is (= (slurp "test-resources/help.txt")
         (with-exit 0 (invoke-command "configure" "-h")))))

(deftest unknown-option
  (is (= (slurp "test-resources/unknown-option.txt")
         (with-exit 1 (invoke-command "configure" "--debug")))))

(deftest pos-arg-validation-failure
  (is (= (slurp "test-resources/pos-arg-validation-failure.txt")
         (with-exit 1 (invoke-command "configure" "myhost.com" "fred=flinstone")))))

(deftest insuffient-values
  (is (= (slurp "test-resources/insufficient-values.txt")
         (with-exit 1 (invoke-command "collect" "just-key")))))

(deftest excess-values
  (is (= (slurp "test-resources/excess-values.txt")
         (with-exit 1 (invoke-command "collect" "the-key" "the-value" "the-extra")))))


(defcommand default-variants
  "Different option defaults."
  [foo [nil "--foo FOO" "Foo option" :default "abc"]
   bar [nil "--bar BAR" "Bar option" :default-fn (constantly "xyz")]
   bazz [nil "--bazz BAZZ" "Bazz option" :default :bazz :default-desc "Bazzy"]]
  [foo bar bazz])

(deftest option-defaults
  (is (= (slurp "test-resources/option-defaults.txt")
         (with-exit 0 (invoke-command "default-variants" "-h")))))

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
  (is (= (slurp "test-resources/tool-help.txt")
         (with-exit 0
                    (dispatch {:tool-name  "test-harness"
                               :doc   "Example commands as part of unit test suite.

  Even this docstring is part of the test."
                               :namespaces '[net.lewisship.example-ns]
                               :arguments  ["help"]})))))

(deftest group-help-defaults-from-first-ns-meta
  (is (= (slurp "test-resources/tool-help-group-default.txt")
         (with-exit 0
                    (dispatch {:tool-name  "test-harness"
                               :groups
                               {"group" {:namespaces '[net.lewisship.group-default-ns
                                                       net.lewisship.example-ns]}}
                               :arguments  ["help" "--full"]})))))

(deftest help-with-search-term
  (is (= (slurp "test-resources/tool-help-search.txt")
         (with-exit 0
                    (dispatch {:tool-name  "test-harness"
                               :namespaces '[net.lewisship.example-ns]
                               :arguments  ["help" "EXP"]})))))

(deftest help-with-search-term-no-match
  (is (= (slurp "test-resources/tool-help-search-no-match.txt")
         (with-exit 0
                    (dispatch {:tool-name  "test-harness"
                               :namespaces '[net.lewisship.example-ns]
                               :arguments  ["help" "Xyzzyx"]})))))

(deftest use-of-command-ns-meta
  (is (= (slurp "test-resources/combo-help.txt")
         (with-exit 0
                    (dispatch {:tool-name  "combo"
                               :namespaces '[net.lewisship.cli-tools.colors]
                               :arguments  ["-h"]})))))

(defcommand set-mode
  "Sets the execution mode"
  [mode ["-m" "--mode MODE" (str "Execution mode, one of " mode-names)
         :parse-fn keyword
         :validate [allowed-modes (str "Must be one of " mode-names)]]
   :let [allowed-modes #{:batch :async :real-time}
         mode-names (->> allowed-modes (map name) sort (string/join ", "))]]
  {:mode mode})


(deftest let-directive
  (is (= (slurp "test-resources/let-directive.txt")
         (with-exit 1
                    (invoke-command "set-mode" "-m" "unknown")))))

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
    (are [input values expected] (= expected (cli/best-match input values))

      "r" colors :red                                        ; multiple matches
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
         (cli/sorted-name-list [:whiskey :tango :foxtrot]))))

(defn exec-group [& args]
  (dispatch {:tool-name  "group-test"
             :namespaces '[net.lewisship.example-ns]
             :groups {"group" {:namespaces '[net.lewisship.group-ns]
                              :doc  "Grouped commands"}}
             :arguments  args}))

(deftest help-with-default-and-explicit-summary-grouped
  (is (= (slurp "test-resources/tool-help-grouped.txt")
         (with-exit 0
                    (exec-group "help" "-f")))))

(deftest help-for-group
  (let [expected (slurp "test-resources/sub-group-help.txt")]
    (is (= expected
           (with-exit 0
                      (exec-group "group" "-h"))))

    (is (= expected
           (with-exit 0
                      (exec-group "gr" "--help"))))))

(deftest can-find-a-grouped-command
  (is (= "echo: fancy\n"
         (with-out-str
           (exec-group "group" "echo" "fancy")))))

(deftest suggest-help-when-name-incomplete
  (is (= (slurp "test-resources/help-incomplete.txt")
         (with-exit 1
                    (exec-group "gr")))))


(deftest can-use-group-abbreviations
  (is (= "echo: abbreviated\n"
         (with-out-str
           (exec-group "g" "ec" "abbreviated")))))

(defmacro with-abort
  [& body]
  `(with-redefs [impl/abort (fn [& inputs#] (throw (Exception. (apply ansi/compose inputs#))))]
     (binding [ansi/*color-enabled* false]
       (when-let [e# (is (~'thrown? Exception (do ~@body)))]
         (ex-message e#)))))

(deftest reports-group-match-failure
  (is (= "group-test: group e matches echo and edit; use group-test group --help (or -h) to list commands"
         (with-abort (exec-group "g" "e" "multiple"))))

  (is (= "group-test: echo is not a command, expected default, explicit, group (or one other); use group-test help to list commands"
         (with-abort (exec-group "echo" "wrong-level")))))

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
                            (cli/ask "Prompt?" cli/yes-or-no))))))

(deftest simple-ask-with-default
  (is (match? [true (compose "Prompt? (" [:bold "yes"] "/no) ")]
              (with-result+err-str
                (with-input ""
                            (cli/ask "Prompt?" cli/yes-or-no {:default true}))))))

(deftest simple-ask-with-false-default
  (is (match? [false (compose "Prompt? (yes/" [:bold "no"] ") ")]
              (with-result+err-str
                (with-input ""
                            (cli/ask "Prompt?" cli/yes-or-no {:default false}))))))

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
                              (cli/ask "Sort order?"
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
                          "Really? (" [:bold "yes"] "/no) ")
        ]
    (is (match? [true expected]
                (with-result+err-str
                  (with-input ["x" ""]
                              (cli/ask "Really?"
                                       cli/yes-or-no
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
                              (cli/ask "Sort order?"
                                       [:name :address :phone]
                                       {:default :name})))))))

(deftest ask-with-force
  (is (match? [:name ""]
              (with-result+err-str
                (cli/ask "Sort order?"
                         [:name :address :phone]
                         {:default :name
                          :force?  true})))))

(deftest ask-with-force-but-no-default
  (when-let [e (is (thrown? Exception
                            (cli/ask "Sort order?"
                                     [:name :address :phone]
                                     {:force? true})))]
    (is (= ":force? option is set, but no :default" (ex-message e)))
    (is (match? {:opts {:force? true}}
                (ex-data e)))))

(deftest ask-when-default-does-not-match-a-possible-response
  (when-let [e (is (thrown? Exception
                            (cli/ask "Really?"
                                     cli/yes-or-no
                                     {:default :maybe})))]
    (is (= ":default does not correspond to any value" (ex-message e)))
    (is (match? {:opts      {:default :maybe}
                 :responses cli/yes-or-no}
                (ex-data e)))))
