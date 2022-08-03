#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test:test-resources")

(def test-namespaces '[net.lewisship.bb.tasks-test
                       net.lewisship.bb.tasks.impl-test])

(defn test-results
  []
  (apply require test-namespaces)
  (apply t/run-tests test-namespaces))

(def failures-and-errors
  (let [{:keys [:fail :error]} (test-results)]
    (+ fail error)))

(System/exit failures-and-errors)