(ns net.lewisship.abort-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-commons.ansi :as ansi :refer [compose]]
            [net.lewisship.cli-tools :as cli-tools :refer [abort command-path]]
            [net.lewisship.cli-tools.aux :refer [capture-result]]
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
    (is (match? {:status 1
                 :err    (compose [:bold.green "tool category command"] ": base message" "\n")}
                (capture-result
                      (abort (command-path) ": " "base message"))))))


(deftest abort-with-several-messages
  (is (match? {:status 1
               :err    "oops I did it again\n"}
              (capture-result
                    (abort "oops" " I did it again")))))

(deftest leading-number-is-status
  (is (match? {:status 99
               :err    "utter failure\n"}
              (capture-result
                    (abort 99 "utter failure")))))

(deftest just-the-message-when-no-tool
  (binding [impl/*tool-options* nil]
    (is (match? {:status 1
                 :err    (compose "utter failure\n")}
                (capture-result
                  (abort (command-path) "utter failure"))))))
