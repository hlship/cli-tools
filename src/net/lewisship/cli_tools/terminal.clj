(ns net.lewisship.cli-tools.terminal
  "A wrapper around the `tput` command, used to obtain terminal codes."
  (:require [babashka.process :as p]))

(def ^:dynamic *terminal-type*
  (or (System/getenv "TERM")
      "xterm"))

(defn- tput*
  [args]
  (-> (apply p/sh "tput"
             (str "-T" *terminal-type*)
             (map str args))
      p/check
      :out))

(def tput
  "Runs the `tput` command to convert the opcodes and values to a terminal
   command string.  Results are memoized."
  (memoize (fn [& args] (tput* args))))
