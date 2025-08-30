(ns net.lewisship.cli-tools.aux
  (:require [clojure.test :refer [is]]
            [net.lewisship.cli-tools.test :refer [with-exit]]))

(defmacro with-exit-errors
  [expected-errors & body]
  `(let [*errors# (atom nil)]
     (with-redefs [cli-tools/print-errors (fn [errors#]
                                            (reset! *errors# errors#))]
       (with-exit 1 ~@body))
     (is (= @*errors# ~expected-errors))))
