(ns net.lewisship.cli-tools.completions-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools :as cli]
            [net.lewisship.cli-tools.test :as test]))

(def standard-app
  {:tool-name  "standard"
   :doc        "A standard tool"
   :namespaces '[net.lewisship.cli-tools.completions
                 net.lewisship.cli-tools.colors]
   :groups     {"group-a" '{:namespaces [net.lewisship.group-ns]}}})

(defn completions-for
  [dispatch-options]
  (test/capture-result
    (cli/dispatch (assoc dispatch-options
                         :arguments ["completions"]))))

(deftest standard-completions
  (is (match? {:status 0
               :out    (-> "completions-standard.txt" io/resource slurp)}
              (completions-for standard-app))))


(comment

  (->> (completions-for standard-app)
       :out
       (spit "test-resources/completions-standard.txt")
       )

  )
