(ns net.lewisship.cli-tools.completions-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools :as cli]
            [net.lewisship.cli-tools.test :as test]))

(def standard-app
  '{:tool-name  "standard"
   :doc        "A standard tool"
    :namespaces [net.lewisship.cli-tools.completions
                 net.lewisship.cli-tools.colors]
    :groups     {"group-a" {:namespaces [net.lewisship.group-ns]}}})


(def bb-app
  '{:tool-name  "bb"
    :doc        "A tool with bababashka-cli commands"
    :namespaces [net.lewisship.cli-tools.completions]
    :groups     {"bb-cli" {:namespaces [net.lewisship.cli-tools.bb]}}})

(defn completions-for
  [dispatch-options]
  (test/capture-result
    (cli/dispatch (assoc dispatch-options
                         :arguments ["completions"]))))

(deftest standard-completions
  (is (match? {:status 0
               :out    (-> "completions-standard.txt" io/resource slurp)}
              (completions-for standard-app))))

(deftest bb-completions
  (is (match? {:status 0
               :out    (-> "completions-bb.txt" io/resource slurp)}
              (completions-for bb-app))))


(comment

  (->> (completions-for bb-app)
       :out
       (spit "test-resources/completions-bb.txt")
       )

  )
