(ns net.lewisship.messy-commands
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand simple
  "Simple command."
  []
  (println "simple: ok"))

(defcommand messy
  "Messy command."
  [:args
   name-arg ["NAME" "Name to print"]]
  (println "messy:" name-arg "ok"))


