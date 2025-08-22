(ns net.lewisship.cli-tools.bb-cli
  "Support for Babashka CLI functions as commands."
  {:added  "0.16.0"
   :no-doc true}
  (:require [net.lewisship.cli-tools.impl :as impl]
            [clojure.string :as string]
            [babashka.cli.exec :as cli-exec])
  (:import (clojure.lang ExceptionInfo)))

(defn- wrapper
  [& args]
  (if impl/*introspection-mode*
    ;; TODO: Might be able to look at the meta on the var to identify the cli spec
    ;; and options from that.
    (select-keys impl/*command-map* :title)
    (let [{:org.babashka.cli/keys [cli-fn]
           :keys                  [command-path]} impl/*command-map*]
      (try
        (binding [babashka.cli.exec/*basis* {}]
          (apply cli-exec/main (str cli-fn) args))
        (catch ExceptionInfo e
          (let [{:keys [type msg option value]} (ex-data e)]
            (when-not (= :org.babashka/cli type)
              (throw e))
            ;; Really need to expand this in the future
            (impl/abort [:red "Error in "
                    (string/join " " command-path)
                    ": "
                    msg
                    " (option "
                    option
                    ", value "
                    (pr-str value)
                    ")"])))))))
