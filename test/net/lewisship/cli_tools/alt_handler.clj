(ns net.lewisship.cli-tools.alt-handler
  (:require [net.lewisship.cli-tools :as cli-tools :refer [defcommand]]))

(def ^:dynamic *global-state* nil)

(defn- my-handler
  [{:keys [global]} _dispatch-options callback]
  (binding [*global-state* global]
    (callback)))

(defcommand print-state
  "Prints the global state."
  []
  (println "Global state:" *global-state*))

(defn -main [& args]
  (cli-tools/dispatch {:tool-name            "alt-handler"
                       :doc                  "Example of using a custom tool handler."
                       :namespaces           '[net.lewisship.cli-tools.alt-handler]
                       :extra-tool-options   [["-g" "--global VALUE" "Sets global state"]]
                       :tool-options-handler my-handler
                       :arguments            args}))
