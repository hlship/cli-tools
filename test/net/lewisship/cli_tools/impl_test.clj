(ns net.lewisship.cli-tools.impl-test
  (:require [clojure.test :refer [deftest is]])
  (:require [net.lewisship.cli-tools.impl :refer [compile-interface]]))

(deftest no-options
  (is (= {:option-symbols []
          :arg-symbols []
          :command-options [["-h" "--help" "This command summary" :id :help]]
          :command-args []
          :command-doc "<DOC>"}
         (compile-interface "<DOC>" []))))

(deftest symbol-name-becomes-option-id
  (is (= [["-x" "--execute" :id :execute?]]
         (-> (compile-interface nil '[execute? ["-x" "--execute"]])
             :command-options
             butlast))))

(deftest docstring-becomes-doc
  (is (= [["-x" "--execute" "Command to execute" :id :execute?]]
         (-> (compile-interface nil '[execute? ["-x" "--execute" "Command to execute"]])
             :command-options
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

(deftest can-provide-command-map-symbol-name
  (is (= 'my-command-map
         (:command-map-symbol (compile-interface nil '[:as my-command-map])))))

(deftest missing-map-symbol-after-as-keyword
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:as])))]
    (is (= "Missing data in interface definitions" (ex-message e)))))

(deftest not-a-symbol-after-as-keyword
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:as "my-command-map-string"])))]
    (is (= "Expected command-map symbol" (ex-message e)))))

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
    (is (= "Unexpected keyword" (ex-message e)))))

(deftest in-order-option
  (is (= {:in-order true}
         (:parse-opts-options (compile-interface nil '[:in-order true])))))

(deftest in-order-must-be-boolean
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:in-order in-order])))]
    (is (= "Expected boolean after :in-order" (ex-message e)))))

(deftest can-override-command-name
  (is (= "overridden"
         (:command-name (compile-interface nil '[:command "overridden"])))))

