#!/usr/bin/env bb

;; Just execute this script to run test under Babashka

(require '[clojure.test :as t]
         '[clojure.java.shell :refer [sh]]
         '[clojure.string :as str]
         '[babashka.classpath :as cp])

(def cp (-> (sh "clojure" "-Spath" "-A:dev")
            :out
            str/trim))
(cp/add-classpath cp)

(def test-namespaces '[net.lewisship.cli-tools-test
                       net.lewisship.cli-tools.impl-test])

(defn test-results
  []
  (apply require test-namespaces)
  (apply t/run-tests test-namespaces))

(def failures-and-errors
  (let [{:keys [:fail :error]} (test-results)]
    (+ fail error)))

(System/exit failures-and-errors)