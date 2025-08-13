(ns net.lewisship.cli-tools.builtins
  "Built-in commands, available to any tool."
  {:command-category       "Built-in"
   :command-category-order 100}
  (:require [net.lewisship.cli-tools.impl :as impl]
            [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand help
  "List available commands"
  [full? ["-f" "--full" "Provide help for all commands, not just top-level"]
   :args
   search-term ["SEARCH" "Filter shown commands to those that match this term"
         :optional true]]
  ;; dispatch binds *options* for us
  (impl/print-tool-help impl/*tool-options* search-term full?))

