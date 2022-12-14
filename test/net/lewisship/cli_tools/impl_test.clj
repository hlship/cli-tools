(ns net.lewisship.cli-tools.impl-test
  (:require [clojure.test :refer [deftest is]]
            [io.aviso.ansi :as ansi]
            [net.lewisship.cli-tools.impl :refer [compile-interface]]
            [net.lewisship.cli-tools.impl :as impl]))

(deftest no-options
  (is (= {:option-symbols []
          :let-forms []
          :command-options [["-h" "--help" "This command summary" :id :help]]
          :command-args []
          :validate-cases []
          :command-doc "<DOC>"}
         (compile-interface "<DOC>" []))))

(deftest symbol-name-becomes-option-id
  (is (= [["-x" "--execute" :id :execute?]]
         (-> (compile-interface nil '[execute? ["-x" "--execute"]])
             :command-options
             butlast))))

(deftest let-requires-a-vector
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[:let :foo])))]
    (is (= "Expected a vector of symbol/expression pairs" (ex-message e)))
    (is (= :foo (-> e ex-data :form)))))

(deftest let-requires-even-vector
  (is (thrown-with-msg? Exception #"Expected a vector of symbol/expression pairs"
               (compile-interface nil '[:let [:a :b :c]]))))

(deftest let-success
  (is (= '[a :a
           b :b]
         (-> (compile-interface nil '[:let [a :a
                                            b :b]])
             :let-forms))))

(deftest let-forms-accumulate
  (is (= '[a :a
           b :b
           c :c]
         (-> (compile-interface nil '[:let [a :a
                                            b :b]
                                      :let [c :c]])
             :let-forms))))

(deftest validate-must-be-vector-with-even-count
  (is (thrown-with-msg? Exception #"Expected a vector of test/message pairs"
                        (compile-interface nil '[:validate foo])))
  (is (thrown-with-msg? Exception #"Expected even number of tests and messages"
                        (compile-interface nil '[:validate [1 2 3]]))))

(deftest validate-forms-collect
  (is (= '[1 2 a b]
         (-> (compile-interface nil '[:validate [1 2]
                                     :validate [a b]])
             :validate-cases))))

(deftest docstring-becomes-doc
  (is (= [["-x" "--execute" "Command to execute" :id :execute?]]
         (-> (compile-interface nil '[execute? ["-x" "--execute" "Command to execute"]])
             :command-options
             butlast))))

(deftest option-symbols-must-be-unique
  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[switch ["-s" "--switch"]
                                                     verbose ["-v" "--verbose"]
                                                     :args
                                                     switch ["SWITCH" "Switch to operate on"]])))]
    (is (= "Option and argument symbols must be unique" (ex-message e))))


  (when-let [e (is (thrown? Exception
                            (compile-interface nil '[switch ["-s" "--switch"]
                                                     switch ["-v" "--verbose"]])))]
    (is (= "Option and argument symbols must be unique" (ex-message e)))))

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

(deftest omits-tool-help-when-help-not-available
  (is (= ", use this-tool help to list commands."
         (ansi/strip-ansi
           (impl/use-help-message "this-tool" {"help" nil
                                               "some-command" nil}))))
  (is (= ""
         (impl/use-help-message "this-tool" {"some-command" nil}))))

(deftest provides-help-with-h-or-help
  (let [*help-args* (atom nil)
        commands {"help" #(reset! *help-args* %)}
        options {:tool-name "test-tool"
                 :commands commands}]
    (doseq [arg ["-h" "--help"]
            :let [extra-arg (str "extra" arg)]]
      (reset! *help-args* nil)
      (impl/dispatch (assoc options :arguments [arg extra-arg]))
      (is (= [extra-arg]
             @*help-args*)))))

(deftest command-not-provided
  (let [*message* (atom nil)]
    (with-redefs [impl/abort #(reset! *message* %)]
      (impl/dispatch {:tool-name "loco"
                      :commands {"help" nil}
                      :arguments ["-not-such-option"]})
      (is (= "loco: no command provided, use loco help to list commands."
             (ansi/strip-ansi @*message*)))

      (reset! *message* nil)

      (impl/dispatch {:tool-name "bravo"
                      :commands {"help" nil}
                      :arguments ["no-such-command"]})

      (is (= "bravo: no-such-command is not a command, use bravo help to list commands."
             (ansi/strip-ansi @*message*))))))
