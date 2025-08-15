(ns user
  (:require [net.lewisship.trace :as trace]
    ;; Enable (is (match? ..)):
            matcher-combinators.clj-test))

(trace/setup-default)

(trace/trace :enabled? true)
