(ns net.lewisship.cli-tools.builtins
  "Built-in commands, available to any tool."
  {:command-category       "Built-in"
   :command-category-order 100}
  (:require [net.lewisship.cli-tools.impl :as impl]
            [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand help
  "List available commands"
  [flat ["-f" "--flat" "Ignore categories and show a simple list of commands"]]
  ;; dispatch binds *options* for us
  (impl/show-tool-help (cond-> impl/*options*
                               flat (assoc :flat true))))

