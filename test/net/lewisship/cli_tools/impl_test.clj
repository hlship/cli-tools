(ns net.lewisship.cli-tools.impl-test
  (:require [clojure.test :refer [deftest is are use-fixtures]]
            [clj-commons.ansi :as ansi]
            [net.lewisship.cli-tools.test :as test]
            [net.lewisship.cli-tools.impl :as impl :refer [compile-interface]]))

(use-fixtures :once test/disable-ansi)

(deftest no-options
  (is (= {:option-symbols  []
          :let-forms       []
          :command-options [["-h" "--help" "This command summary" :id :help]]
          :command-args    []
          :validate-cases  []}
         (compile-interface []))))

(deftest symbol-name-becomes-option-id
  (is (= [["-x" "--execute" :id :execute?]]
         (-> (compile-interface '[execute? ["-x" "--execute"]])
             :command-options
             butlast))))

(deftest let-requires-a-vector
  (when-let [e (is (thrown? Exception
                            (compile-interface '[:let :foo])))]
    (is (= "Expected a vector of symbol/expression pairs" (ex-message e)))
    (is (= :foo (-> e ex-data :form)))))

(deftest let-requires-even-vector
  (is (thrown-with-msg? Exception #"Expected a vector of symbol/expression pairs"
                        (compile-interface '[:let [:a :b :c]]))))

(deftest let-success
  (is (= '[a :a
           b :b]
         (-> (compile-interface '[:let [a :a
                                        b :b]])
             :let-forms))))

(deftest let-forms-accumulate
  (is (= '[a :a
           b :b
           c :c]
         (-> (compile-interface '[:let [a :a
                                        b :b]
                                  :let [c :c]])
             :let-forms))))

(deftest validate-must-be-vector-with-even-count
  (is (thrown-with-msg? Exception #"Expected a vector of test/message pairs"
                        (compile-interface '[:validate foo])))
  (is (thrown-with-msg? Exception #"Expected even number of tests and messages"
                        (compile-interface '[:validate [1 2 3]]))))

(deftest validate-forms-collect
  (is (= '[1 2 a b]
         (-> (compile-interface '[:validate [1 2]
                                  :validate [a b]])
             :validate-cases))))

(deftest docstring-becomes-doc
  (is (= [["-x" "--execute" "Command to execute" :id :execute?]]
         (-> (compile-interface '[execute? ["-x" "--execute" "Command to execute"]])
             :command-options
             butlast))))

(deftest option-symbols-must-be-unique
  (when-let [e (is (thrown? Exception
                            (compile-interface '[switch ["-s" "--switch"]
                                                 verbose ["-v" "--verbose"]
                                                 :args
                                                 switch ["SWITCH" "Switch to operate on"]])))]
    (is (= "Option and argument symbols must be unique" (ex-message e))))


  (when-let [e (is (thrown? Exception
                            (compile-interface '[switch ["-s" "--switch"]
                                                 switch ["-v" "--verbose"]])))]
    (is (= "Option and argument symbols must be unique" (ex-message e)))))

(deftest missing-argument-after-arg-symbol
  (when-let [e (is (thrown? Exception
                            (compile-interface '[:args
                                                 arg-symbol])))]
    (is (= "Missing data in interface definitions" (ex-message e)))))

(deftest missing-option-after-option-symbol
  (when-let [e (is (thrown? Exception
                            (compile-interface '[option-symbol])))]
    (is (= "Missing data in interface definitions" (ex-message e)))))

(deftest not-a-valid-option-def
  (when-let [e (is (thrown? Exception
                            (compile-interface '[my-option "not valid"])))]
    (is (= "Expected option definition" (ex-message e)))))

(deftest not-a-valid-arg-def
  (when-let [e (is (thrown? Exception
                            (compile-interface '[:args my-arg "not valid"])))]
    (is (= "Expected argument definition" (ex-message e)))))

(deftest unexpected-keyword
  (when-let [e (is (thrown? Exception
                            (compile-interface '[:left-field true])))]
    (is (= "Unexpected keyword" (ex-message e)))))

(deftest in-order-option
  (is (= {:in-order true}
         (:parse-opts-options (compile-interface '[:in-order true])))))

(deftest in-order-must-be-boolean
  (when-let [e (is (thrown? Exception
                            (compile-interface '[:in-order in-order])))]
    (is (= "Expected boolean after :in-order" (ex-message e)))))

(deftest can-override-command-nameD
  (is (= "overridden"
         (:command-name (compile-interface '[:command "overridden"])))))

(def *help-args (atom nil))

(defn capture-help
  [& args]
  (reset! *help-args args))

(deftest command-not-provided
  (let [*message* (atom nil)]
    (with-redefs [impl/abort (fn [& inputs]
                               (reset! *message* (apply ansi/compose inputs)))]
      (impl/dispatch {:tool-name    "loco"
                      :command-root {"help" {:fn ::placeholder}}
                      :arguments    ["-no-such-option"]})
      (is (= "loco: no command provided, use loco --help (or -h) to list commands"
             @*message*))

      (reset! *message* nil)

      (impl/dispatch {:tool-name    "bravo"
                      :command-root {"help" {:fn ::placeholder}}
                      :arguments    ["no-such-command"]})

      (is (= "bravo: no-such-command is not a command, expected help; use bravo --help (or -h) to list commands"
             @*message*)))))

(deftest compose-list-tests
  (let [base-terms ["alpha" "bravo" "charlie" "delta" "echo" "foxtrot"]]
    (are [terms opts expected]
      (= expected
         (impl/compose-list terms opts))

      base-terms
      nil
      '([:bold.green "alpha"] ", "
        [:bold.green "bravo"] ", "
        [:bold.green "charlie"] " (or three others)")

      nil
      nil
      nil

      (take 1 base-terms)
      nil
      [:bold.green "alpha"]

      (take 2 base-terms)
      nil
      '([:bold.green "alpha"] " or "
        [:bold.green "bravo"])

      (take 3 base-terms)
      nil
      '([:bold.green "alpha"] ", "
        [:bold.green "bravo"] ", or "
        [:bold.green "charlie"])

      base-terms
      {:max-terms 1
       :font      :cyan}
      '([:cyan "alpha"]
        " (or five others)")

      (take 4 base-terms)
      {:font :cyan}
      '([:cyan "alpha"] ", "
        [:cyan "bravo"] ", "
        [:cyan "charlie"] " (or one other)"))))

(deftest first-sentence
  (are [s expected] (= expected (impl/first-sentence s))

    "  Quick, Simple " "Quick, Simple"

    "\n\n The first.\nThe second." "The first"

    "In namespace x.y.z, we do this thing." "In namespace x.y.z, we do this thing"

    nil nil

    "" nil
    ))
