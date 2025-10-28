(ns net.lewisship.cli-tools.bb-test
  (:require [clj-commons.ansi :as ansi]
            [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools :as cli]
            [net.lewisship.cli-tools.test :refer [capture-result]]))

(cli/set-prevent-exit! true)

(defn- dispatch [& args]
  (binding [ansi/*color-enabled* false]
    (capture-result
      (cli/dispatch {:tool-name  "bb-cli"
                     :namespaces '[]
                     :groups     {"bb" {:namespaces '[net.lewisship.cli-tools.bb]
                                        :doc        "BB CLI commands"}}
                     :arguments  args
                     :cache-dir  nil}))))

(deftest can-find-cli-commands
  (is (match?
        {:status 0
         :out-lines ["bb-cli bb - BB CLI commands"
                     ""
                     "Commands:"
                     "  alpha: This command take a symbol and a long"
                     "   find: Find by id"]}
        (dispatch "bb" "-h"))))

(deftest can-invoke-command-with-default-name
  (is (match? {:status 0
               :out    ":in :alpha :args {:a fred, :b 99, :other \"abc\"}\n"}
              (dispatch "bb" "alpha" "-a" "fred" "-b" "99" "--other" "abc"))))

(deftest can-invoke-command-with-override-name
  (is (match? {:status 0
               :out    ":in :find-command :arg {:id 47}\n"}
              (dispatch "bb" "find" "--id" "47"))))

(deftest can-find-bb-commands-using-title

  (is (match? {:status 0
               :out    "One command matches finds:\n\nbb find: Find by id\n"}
              (dispatch "help" "finds"))))

(deftest handles-invalid-inputs

  ;; This is quite minimal and may be expanded in the future.
  (is (match? {:status 1,
               :err    "Error in bb find: Coerce failure: cannot transform input \"xyz\" to long (option :id, value \"xyz\")\n",}
              (dispatch "bb" "find" "--id" "xyz"))))
