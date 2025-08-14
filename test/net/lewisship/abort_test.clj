(ns net.lewisship.abort-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-commons.ansi :as ansi :refer [compose]]
            [net.lewisship.cli-tools :as cli-tools :refer [abort command-path]]
            [net.lewisship.cli-tools.aux :refer [with-exit]]
            [net.lewisship.cli-tools.impl :as impl]))

(cli-tools/set-prevent-exit! true)

(use-fixtures :once
              (fn [f]
                (binding [ansi/*color-enabled* false
                          impl/*tool-options*  {:tool-name "tool"}
                          impl/*command-map*   {:command-path ["category" "command"]}]
                  (f))))

(deftest abort-with-message
  (binding [ansi/*color-enabled* true]
    (is (= (compose [:bold.green "tool category command"] ": base message" "\n")
           (with-exit 1
                      (abort (command-path) ": " "base message"))))))


(deftest abort-with-several-messages
  (is (= "oops I did it again\n"
         (with-exit 1
                    (abort "oops" " I did it again")))))

(deftest leading-number-is-status
  (is (= "utter failure\n"
         (with-exit 99
                    (abort 99 "utter failure")))))

(deftest just-the-message-when-no-tool
  (binding [impl/*tool-options* nil]
    (is (= (compose "utter failure\n")
           (with-exit 1 (abort (command-path) "utter failure"))))))
