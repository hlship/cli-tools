(ns net.lewisship.cli-tools.completions-test
  "Tests related to command completions."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools.aux :refer [dispatch-with-result]]))

(defn- expected [file]
  (-> (str "expected/" file)
      io/resource
      slurp
      string/trim))

(defn- dispatch
  ([options]
   (-> options
       (assoc :arguments ["completions"])
       dispatch-with-result
       :out
       string/trim))
  ([to options]
   (let [result (dispatch options)]
     (-> (str "test-resources/expected/" to)
         io/file
         (spit result))
     (println result))))

(deftest simple-completions
  (is (match? (expected "simple-completions.txt")
              (dispatch '{:tool-name  "simple"
                          :namespaces [net.lewisship.cli-tools.colors
                                       net.lewisship.cli-tools.completions]}))))

(deftest subgroup-completions
  (is (match? (expected "subgroup-completions.txt")
              (dispatch
                '{:tool-name  "subgroup"
                  :namespaces [net.lewisship.cli-tools.completions]
                  :groups
                  {"subgroup" {:namespaces [net.lewisship.cli-tools.completion-group]}}}))))
