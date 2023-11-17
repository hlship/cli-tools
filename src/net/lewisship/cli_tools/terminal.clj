(ns net.lewisship.cli-tools.terminal
  "A wrapper around the `/usr/bin/tput` command, used to obtain terminal control sequences for moving the cursor,
   clearing lines, and so forth."
  {:added "0.11"}
  (:require [babashka.process :as p]))

(def ^:dynamic *terminal-type*
  (or (System/getenv "TERM")
      "xterm"))

(def ^:dynamic *tput-command* "/usr/bin/tput")

(defn- tput*
  [args]
  (-> (apply p/sh *tput-command*
             (str "-T" *terminal-type*)
             args)
      p/check
      :out))

(def tput
  "Runs the `tput` command to convert the opcodes and values to a terminal
   command string.  Results are memoized."
  (memoize (fn [& args] (tput* args))))
