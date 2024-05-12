(ns net.lewisship.cli-tools.progress
  "Part of the job status subsystem, progress reporting converts a progress state into a progress message, which can be
  a simple string, or a composable string."
  {:added "0.11"})

(def ^:private char-bar
  (memoize (fn [c n]
             (let [b (StringBuilder. (int n))]
               (dotimes [_ n]
                 (.append b c))
               (str b)))))

(defn bar
  "Returns a string representation of a bar, used when formatting progress.
  The default bar length (number of characters) is 30.

  The bar is generated using ANSI block characters.

  The ratio should be between 0.0 and 1.0."
  ([completed-ratio]
   (bar 30 completed-ratio))
  ([bar-length completed-ratio]
   (let [completed-length (int (* completed-ratio bar-length))
         remaining-length (- bar-length completed-length)
         completed        (char-bar \u2593 completed-length)]
     ;; https://en.wikipedia.org/wiki/Block_Elements
     (if (zero? remaining-length)
       completed
       (str completed (char-bar \u2591 remaining-length))))))

(defn block-progress-formatter
  "Basic function for displaying progress. Returns a 30 character bar,
  followed by the completion ratio, and the current/target values
  (which must be integers)."
  {:added "0.1.7"}
  [current target]
  (let [displayable     (and (some? current)
                             (pos? target)
                             (pos? current))
        completed-ratio (if displayable (/ current target) 0)
        completed-bar   (bar completed-ratio)]
    (if displayable
      (format "%s %3d%% - %d/%d"
              completed-bar
              (int (* 100 completed-ratio))
              current
              target)
      completed-bar)))
