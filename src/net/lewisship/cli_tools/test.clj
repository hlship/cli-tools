(ns net.lewisship.cli-tools.test
  "Utilities to assist with writing tests for commands."
  {:added "0.16.0"}
  (:require [clj-commons.ansi :as ansi]
            [clojure.string :as string]
            [net.lewisship.cli-tools :as cli]
            [clojure.test :refer [is]])
  (:import (clojure.lang ExceptionInfo)
           (java.io StringWriter)))

(defn with-err-str*
  [f]
  (let [w (StringWriter.)]
    (binding [*err* w]
      (f))
    (str w)))

(defmacro with-err-str
  "Executes the body, capturing any output to *err*."
  [& body]
  `(with-err-str* (fn [] ~@body)))

(defmacro with-split-err
  [& body]
  `(string/split-lines (with-err-str ~@body)))

(defmacro with-split-out
  [& body]
  `(string/split-lines (with-out-str ~@body)))

(defn disable-ansi
  "Fixture to disable ANSI colors."
  [f]
  (binding [ansi/*color-enabled* false]
    (f)))

(defmacro with-exit
  "Asserts that net.lewisship.cli-tools/exit is invoked with the specified
  status code."
  [status-code & body]
  `(do
     (cli/set-prevent-exit! true)
     (when-let [e# (is (~'thrown-with-msg? ExceptionInfo #"Exit"
                         (do ~@body)))]
       (is (= ~status-code
              (-> e# ex-data :status))))))

(defmacro capture-result
  "Executes body and capture standard out, standard error, and exit status."
  [& body]
  `(let [out#    (StringWriter.)
         err#    (StringWriter.)
         status# (binding [*out* out#
                           *err* err#]
                   (cli/set-prevent-exit! true)
                   (try
                     (do ~@body)
                     ;; Normal exit is a 0
                     0
                     (catch Throwable t#
                       (when-not (= "Exit" (ex-message t#))
                         (throw t#))
                       (-> t# ex-data :status))))]
     {:out       (str out#)
      :out-lines (-> out# str string/split-lines)
      :err       (str err#)
      :err-lines (-> err# str string/split-lines)
      :status    status#}))
