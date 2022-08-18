(ns net.lewisship.cli-tools-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [net.lewisship.cli-tools :as cli :refer [defcommand dispatch]]
            [net.lewisship.cli-tools.impl :as impl]
            [clojure.repl :as repl]
            [clojure.string :as str]))

(cli/set-prevent-exit! true)

(use-fixtures
  :once
  (fn [f]
    (binding [impl/*options* {:tool-name "harness"}]
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

(defmacro with-exit
  [expected & body]
  `(with-out-str
     (when-let [e# (is (~'thrown? Exception ~@body))]
       (is (= "Exit" (ex-message e#)))
       (is (= {:status ~expected} (ex-data e#))))))

(deftest success
  (is (= {:verbose true
          :host "http://myhost.com"
          :key-values {:fred "flintstone"}}
         (configure "-v" "http://myhost.com" "fred=flintstone")))

  (is (= {:verbose nil
          :host "http://myhost.com"
          :key-values {:fred "flintstone"
                       :barney "rubble"}}
         (configure "http://myhost.com" "fred=flintstone" "barney=rubble"))))

(deftest standard-help
  (is (= (slurp "test-resources/help.txt")
         (with-exit 0 (configure "-h")))))

(deftest unknown-option
  (is (= (slurp "test-resources/unknown-option.txt")
         (with-exit 1 (configure "--debug")))))

(deftest pos-arg-validation-failure
  (is (= (slurp "test-resources/pos-arg-validation-failure.txt")
         (with-exit 1 (configure "myhost.com" "fred=flinstone")))))

(deftest insuffient-values
  (is (= (slurp "test-resources/insufficient-values.txt")
         (with-exit 1 (collect "just-key")))))

(deftest excess-values
  (is (= (slurp "test-resources/excess-values.txt")
         (with-exit 1 (collect "the-key" "the-value" "the-extra")))))


(defcommand in-order
  ""
  [verbose ["-v" "--verbose"]
   :args
   command ["COMMAND" "Remote command to execute"]
   args ["ARGS" "Arguments to remote command"
         :optional true
         :repeatable true]
   :in-order true
   :summary "Execute remote command"]
  {:command command
   :args args})

(deftest in-order-arguments
  (is (= {:command "ls", :args ["-lR"]}
         ;; Without :in-order true, the -lR is flagged as an error
         (in-order "-v" "ls" "-lR"))))

(defcommand help
           "Conflicts with built-in help"
  [])

(deftest detects-command-name-conflicts
  (when-let [e (is (thrown? RuntimeException
                            (cli/locate-commands ['net.lewisship.cli-tools
                                                  'net.lewisship.cli-tools-test])))]
    (is (= "command help defined by net.lewisship.cli-tools-test/help conflicts with net.lewisship.cli-tools/help"
           (ex-message e)))))

(deftest rejects-undefined-namespace
  (when-let [e (is (thrown? RuntimeException
                            (cli/locate-commands ['does.not.exist])))]
    (is (= "namespace does.not.exist not found (it may need to be required)"
           (ex-message e)))))

(deftest help-with-default-and-explicit-summary
  (is (= (slurp "test-resources/tool-help.txt")
         (with-exit 0
                    (dispatch {:tool-name "test-harness"
                               :namespaces '[net.lewisship.example-ns]
                               :arguments ["help"]})))))

(defcommand set-mode
  "Sets the execution mode"
  [mode ["-m" "--mode MODE" (str "Execution mode, one of " mode-names)
         :parse-fn keyword
         :validate [allowed-modes (str "Must be one of " mode-names)]]
   :let [allowed-modes #{:batch :async :real-time}
         mode-names (->> allowed-modes (map name) sort (str/join ", "))]]
  {:mode mode})


(deftest let-directive
  (is (= (slurp "test-resources/let-directive.txt")
         (with-exit 1
                    (set-mode "-m" "unknown")))))

(deftest generate-correct-meta
  (is (= (slurp "test-resources/set-mode-doc.txt")
          (with-out-str
            (repl/doc set-mode)))))
