(ns net.lewisship.example-ns
  "Example commands as part of unit test suite.

  Even this docstring is part of the test."
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand default
  "Default command summary.  Up to the period."
  [])

(defcommand explicit
  "Docstring for explicit function."
  [:summary "Explicit command summary"])
