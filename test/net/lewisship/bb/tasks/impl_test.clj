(ns net.lewisship.bb.tasks.impl-test
  (:require [clojure.test :refer [deftest is]])
  (:require [net.lewisship.bb.tasks.impl :refer [compile-interface]]))

(deftest no-options
  (is (= {:option-symbols []
          :arg-symbols []
          :task-options [["-h" "--help" "This command summary" :id :help]]
          :task-args []
          :task-doc "<DOC>"}
         (compile-interface "<DOC>" []))))

(deftest symbol-name-becomes-option-id
  (is (= [["-x" "--execute" :id :execute?]]
         (-> (compile-interface nil '[execute? ["-x" "--execute"]])
             :task-options
             butlast))))

(deftest docstring-becomes-doc
  (is (= [["-x" "--execute" "Command to execute" :id :execute?]]
         (-> (compile-interface nil '[execute? ["-x" "--execute" "Command to execute"]])
             :task-options
             butlast))))

(deftest missing-argument-after-arg-symbol
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:args
                                                     arg-symbol])))]
    (is (= "Missing data in interface definitions" (ex-message e)))))

(deftest missing-option-after-option-symbol
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[option-symbol])))]
    (is (= "Missing data in interface definitions" (ex-message e)))))

(deftest can-provide-task-map-symbol-name
  (is (= 'my-task-map
         (:task-map-symbol (compile-interface nil '[:as my-task-map])))))

(deftest missing-map-symbol-after-as-keyword
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:as])))]
    (is (= "Missing data in interface definitions" (ex-message e)))))

(deftest not-a-symbol-after-as-keyword
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:as "my-task-map-string"])))]
    (is (= "Expected task-map symbol" (ex-message e)))))

(deftest not-a-valid-option-def
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[my-option "not valid"])))]
    (is (= "Expected option definition" (ex-message e)))))

(deftest not-a-valid-arg-def
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:args my-arg "not valid"])))]
    (is (= "Expected argument definition" (ex-message e)))))

(deftest unexpected-keyword
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:left-field true])))]
    (is (= "Unexpected interface form" (ex-message e)))))


