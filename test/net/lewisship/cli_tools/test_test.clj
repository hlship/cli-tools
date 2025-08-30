(ns net.lewisship.cli-tools.test-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [net.lewisship.cli-tools :as cli]
            [net.lewisship.cli-tools.test :refer [with-split-out
                                                  with-split-err
                                                  capture-result
                                                  disable-ansi]]))

(use-fixtures :once disable-ansi)

(deftest with-split-out-test
  (is (match?
        ["Hello,"
         "I must be going."]
        (with-split-out (println "Hello,\nI must be going.\n")))))

(deftest with-split-err-test
  (is (match?
        ["Hello,"
         "I must be going."]
        (with-split-err
          (binding [*out* *err*]
            (println "Hello,\nI must be going.\n"))))))

(deftest capture-result-test

 (is (match?
       {:status 99
        :out "Hello\nThis is a test.\n"
        :out-lines ["Hello"
                    "This is a test."]
        :err "We have failed.\nTerribly.\n"
        :err-lines ["We have failed."
                    "Terribly."]}
       (capture-result
         (println "Hello")
         (println "This is a test.")
         (cli/abort 99 "We have failed.\nTerribly.")))))
