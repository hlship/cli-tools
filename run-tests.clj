#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test:test-resources")

(require 'net.lewisship.bb.tasks-test)

(def test-results
  (t/run-tests 'net.lewisship.bb.tasks-test))

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))

(System/exit failures-and-errors)