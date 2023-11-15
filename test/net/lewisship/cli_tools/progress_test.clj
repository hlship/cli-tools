(ns net.lewisship.cli-tools.progress-test
  (:require [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools.progress :as p]))

(deftest bar-at-zero
  (is (= "░░░░"
         (p/bar 4 0.0))))

(deftest bar-defaults-to-30-wide
  (is (= "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░"
         (p/bar 0.5))))

(deftest bar-at-full
  (is (= "▓▓▓"
         (p/bar 3 1.0))))

(deftest progress-without-current
  (is (= "░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░"
         (p/block-progress-formatter nil 100))))

(deftest mid-progress
  (is (= "▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░  25% - 25/100"
         (p/block-progress-formatter 25 100))))

(deftest full-progress
  (is (= "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ 100% - 97/97"
         (p/block-progress-formatter 97 97))))
