(ns net.lewisship.group-ns
  "Group example namespace"
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand echo
  "Echo a string"
  [:args
   s ["TEXT" "Text to echo"]]
  (println "echo:" s))

(defcommand edit
  "Edit a whatever"
  [])
