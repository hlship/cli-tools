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
      string/split-lines))

(defn- dispatch
  ([options]
   (-> options
       (assoc :arguments ["completions"])
       dispatch-with-result
       :out-lines))
  ([to options]
   (let [result (-> options
                    (assoc :arguments ["completions"])
                    dispatch-with-result
                    :out)]
     (-> (str "test-resources/expected/" to)
         io/file
         (spit result))
     (println result))))

(deftest simple-completions
  (is (match? (expected "simple-completions.txt")
              (dispatch '{:tool-name  "simple"
                          :namespaces [net.lewisship.cli-tools.colors
                                       net.lewisship.cli-tools.completions]}))))

(deftest subgroup-completion
  (is (match? (expected "subgroup-completions.txt")
              (dispatch
                '{:tool-name  "subgroup"
                  :namespaces [net.lewisship.cli-tools.completions]
                  :groups
                  {"subgroup" {:namespaces [net.lewisship.cli-tools.completion-group]}}}))))

(deftest messy-completions
  ;; where command name and group name collide
  ;; Not sure the current behavior is correct
  (is (match? (expected "messy-completions.txt")
              (dispatch
                {:tool-name  "messy"
                 :namespaces '[net.lewisship.cli-tools.completions
                               net.lewisship.messy-commands]
                 :groups     {"messy" {:namespaces '[net.lewisship.messy]
                                       :doc        "Messy command and group at same time"}}}))))
