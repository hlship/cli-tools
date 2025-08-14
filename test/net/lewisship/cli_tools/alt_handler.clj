(ns net.lewisship.cli-tools.alt-handler
  (:require [clojure.tools.cli :as cli]
            [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools :as cli-tools :refer [defcommand]]))

(def ^:dynamic *global-state* nil)

(defn- my-handler
  [dispatch-options]
  (let [{:keys [options arguments summary]}
                (cli/parse-opts (:arguments dispatch-options)
                                [["-g" "--global VALUE" "Sets global state"]
                                 ["-h" "--help" "Help for the alt-handler tool"]]
                                :in-order true
                                :summary-fn cli-tools/summarize-specs)
        {:keys [global help]} options]
    (binding [*global-state* global]
      (cli-tools/dispatch* (-> dispatch-options
                               (assoc :arguments arguments
                                      :tool-summary summary))
                           nil help))))

(defcommand print-state
  "Prints the global state."
  []
  (println "Global state:" *global-state*))

(defn -main [& args]
  (cli-tools/dispatch {:cache-dir  nil
                       :tool-name  "alt-handler"
                       :doc        "Example of using a custom tool handler."
                       :namespaces '[net.lewisship.cli-tools.alt-handler]
                       :handler    my-handler
                       :arguments  args}))
