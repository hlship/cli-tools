(ns net.lewisship.cli-tools.aux
  (:require [clojure.test :refer [is]]
            [net.lewisship.cli-tools :as cli-tools])
  (:import (java.io StringWriter)))

(defn with-err-str*
  [f]
  (let [w (StringWriter.)]
    (binding [*err* w]
      (f))
    (str w)))

(defmacro with-err-str
  [& body]
  `(with-err-str* (fn [] ~@body)))

(defmacro with-exit
  "Runs the body capturing and returning error output. Asserts that the exit code matches the expected value."
  [expected & body]
  `(with-err-str
     (when-let [e# (is (~'thrown? Exception ~@body))]
       (is (= "Exit" (ex-message e#)))
       (is (= {:status ~expected} (ex-data e#))))))

(defmacro with-exit-errors
  [expected-errors & body]
  `(let [*errors# (atom nil)]
     (with-redefs [cli-tools/print-errors (fn [errors#]
                                            (reset! *errors# errors#))]
       (with-exit 1 ~@body))
     (is (= @*errors# ~expected-errors))))

(defmacro capture-result
  "Executes body and capture standard out, standard error, and exit status.
  :out :err and :status."
  [& body]
  `(let [out#    (StringWriter.)
         err#    (StringWriter.)
         status# (binding [*out* out#
                           *err* err#]
                   (try
                     (do ~@body)
                     ;; Normal exit is a 0
                     0
                     (catch Throwable t#
                       (when-not (= "Exit" (ex-message t#))
                         (throw t#))
                       (-> t# ex-data :status))))]
     {:out    (str out#)
      :err    (str err#)
      :status status#}))
