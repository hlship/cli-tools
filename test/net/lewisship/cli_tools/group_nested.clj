(ns net.lewisship.cli-tools.group-nested
  (:require [net.lewisship.cli-tools :refer [defcommand]]))

(defcommand leaf
  "Nested command leaf."
  [])

(defcommand butterfly
  "Nested command butterfly."
  [])

