(ns net.lewisship.cli-tools.builtins
  "Built-in commands, available to any tool."
  {:command-category       "Built-in"
   :command-category-order 100}
  (:require [net.lewisship.cli-tools.impl :as impl]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]))

(defcommand help
  "List available commands.

   If a search term is provided, the --commands option is ignored."
  [output-level (cli/select-option "-c" "--commands FILTER" "Print commands: " #{:none :root :all}
                                   :default :default)
   :args
   search-term ["SEARCH" "Filter shown commands to those that match this term"
         :optional true]]
  (if search-term
    (impl/print-search-results search-term)
    (impl/print-tool-help output-level)))

