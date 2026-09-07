(ns net.lewisship.cli-tools.completions-test
  "Tests related to command completions."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]]
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

(deftest nested-group-completion
  (is (match? (expected "nested-group-completions.txt")
              (dispatch
                '{:tool-name  "nested"
                  :namespaces [net.lewisship.cli-tools.completions]
                  :groups
                  {"group" {:namespaces [net.lewisship.group-ns]
                            :doc        "Grouped commands"
                            :groups     {"nested" {:namespaces [net.lewisship.cli-tools.group-nested]
                                                   :doc        "Nested commands inside group"}}}}}))))

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

(deftest tool-options
  (is (match? (expected "tool-options.txt")
              (dispatch
                {:tool-name          "options"
                 :namespaces         '[net.lewisship.cli-tools.completions]
                 :extra-tool-options [["-d" "--debug" "Enable debug mode"
                                       :id :debug]
                                      ["-o" "--output-path FILE" "Write output to file, not stdout"
                                       :id :output-path
                                       :default "-"
                                       :parse-fn identity
                                       :validate [some? "Must be provided"]]]}))))

(deftest version-option
  (is (match? (expected "version-option.txt")
              (dispatch
                {:tool-name  "versioned"
                 :namespaces '[net.lewisship.cli-tools.completions]
                 :version    "1.2.3"}))))
