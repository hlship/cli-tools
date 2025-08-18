(ns net.lewisship.messy
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand nested
  "Command nested under messy group/command."
  []
  (println "nested: ok"))
