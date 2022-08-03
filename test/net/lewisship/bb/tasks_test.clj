float-array (ns net.lewisship.bb.tasks-test
              (:require [clojure.test :refer [deftest is]]
                        [net.lewisship.bb.tasks :as tasks :refer [deftask]]))

(tasks/set-prevent-exit! true)
(tasks/set-tool-name! "harness")

;; An example to test around

(deftask configure
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

(deftask collect
  "Collect key and value."
  [:args
   k ["KEY" "Key to set"]
   v ["VAL" "Value to set"]]
  [k v])

;; This is all that the code really needs; the task name is usually set inside
;; bb.edn, but doesn't have to match the function name.
(def task-data {:name "sys-configure"})

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
         (configure task-data ["-v" "http://myhost.com" "fred=flintstone"])))

  (is (= {:verbose nil
          :host "http://myhost.com"
          :key-values {:fred "flintstone"
                       :barney "rubble"}}
         (configure task-data ["http://myhost.com" "fred=flintstone" "barney=rubble"]))))

(deftest standard-help
  (is (= (slurp "test-resources/help.txt")
         (with-exit 0 (configure task-data ["-h"])))))

(deftest unknown-option
  (is (= (slurp "test-resources/unknown-option.txt")
         (with-exit 1 (configure task-data ["--debug"])))))

(deftest pos-arg-validation-failure
  (is (= (slurp "test-resources/pos-arg-validation-failure.txt")
         (with-exit 1 (configure task-data ["myhost.com" "fred=flinstone"])))))

(deftest insuffient-values
  (is (= (slurp "test-resources/insufficient-values.txt")
         (with-exit 1 (collect task-data ["just-key"])))))

(deftest excess-values
  (is (= (slurp "test-resources/excess-values.txt")
         (with-exit 1 (collect task-data ["the-key" "the-value" "the-extra"])))))


(deftask in-order
  ""
  [verbose ["-v" "--verbose"]
   :args
   command ["COMMAND" "Remote command to execute"]
   args ["ARGS" "Arguments to remote command"
         :optional true
         :repeatable true]
   ;; Putting this last is idiomatic
   :in-order true]
  {:command command
   :args args})

(deftest in-order-arguments
  (is (= {:command "ls", :args ["-lR"]}
         ;; Without :in-order true, the -lR is flagged as an error
         (in-order nil ["-v" "ls" "-lR"]))))
