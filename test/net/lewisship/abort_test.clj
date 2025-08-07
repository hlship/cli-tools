(ns net.lewisship.abort-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-commons.ansi :as ansi :refer [compose]]
            [net.lewisship.cli-tools :as cli :refer [abort]]
            [net.lewisship.cli-tools-test :refer [with-exit]]
            [net.lewisship.cli-tools.impl :as impl]))

(cli/set-prevent-exit! true)

(use-fixtures :once
              (fn [f]
                (binding [ansi/*color-enabled* false
                          impl/*options*       {:tool-name "tool"}
                          impl/*command-map*   {:command-path ["category" "command"]}]
                  (f))))

(deftest abort-with-message
  (binding [ansi/*color-enabled* true]
    (is (= (compose [:bold.green "tool category command"] [:red ": base message"] "\n")
           (with-exit 1
                      (abort "base message"))))))


(deftest abort-with-several-messages
  (is (= "tool category command: oops I did it again\n"
         (with-exit 1
                    (abort "oops" " I did it again")))))

(deftest leading-number-is-status
  (is (= "tool category command: utter failure\n"
         (with-exit 99
                    (abort 99 "utter failure")))))

(deftest when-no-command
  (binding [impl/*command-map* nil]
    (is (= "tool: main failure\n"
           (with-exit 1 (abort "main failure"))))))

(deftest replaces-exception-with-message
  (is (= "tool category command: failure: missing permissions\n"
         (with-exit 1 (abort "failure: " (RuntimeException. "missing permissions"))))))

(deftest replace-exception-with-nil-message-with-class-name
  (is (= "tool category command: failure: java.lang.NullPointerException\n"
         (with-exit 1 (abort "failure: " (NullPointerException.))))))

(deftest just-the-message-when-no-tool
  (binding [impl/*options* nil]
    (is (= (compose [:red "utter failure"] "\n")
           (with-exit 1 (abort "utter failure"))))))
