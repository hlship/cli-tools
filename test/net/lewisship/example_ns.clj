(ns net.lewisship.example-ns
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand default
  "Default command summary.  Up to the period."
  [])

(defcommand explicit
  "Docstring for explicit function."
  [:title "Explicit command summary"])
