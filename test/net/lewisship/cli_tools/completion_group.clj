(ns net.lewisship.cli-tools.completion-group
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand example
  "Does something interesting."
  [verbose? ["-v" "--verbose" "Extra output"]
   limit ["-l" "--limit NUMBER" "Maximum globnars to frobnicate"]])
