(ns user
  (:require [net.lewisship.trace :as trace]))

(trace/setup-default)

(trace/trace :enabled? true)
