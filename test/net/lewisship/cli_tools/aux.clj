(ns net.lewisship.cli-tools.aux
  (:require [clojure.test :refer [is]]
            [net.lewisship.cli-tools :as cli-tools]
            [net.lewisship.cli-tools.test :refer [with-exit capture-result]]))

(defmacro with-exit-errors
  [expected-errors & body]
  `(let [*errors# (atom nil)]
     (with-redefs [cli-tools/print-errors (fn [errors#]
                                            (reset! *errors# errors#))]
       (with-exit 1 ~@body))
     (is (= @*errors# ~expected-errors))))

(defn dispatch-with-result
  [options]
  (capture-result
    (cli-tools/dispatch (assoc options :cache-dir nil))))
