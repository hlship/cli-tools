(ns net.lewisship.cli-tools.completions
  "Support for generating zsh command completion scripts for a command."
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [net.lewisship.cli-tools :refer [defcommand abort]]
            [clojure.string :as string]
            selmer.util
            selmer.parser
            [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools.impl :as impl]))

(defn- simplify
  [& s]
  (-> (string/join "_" s)
      (string/replace #"[^a-zA-Z0-9_]+" "_")))


(defn- render
  [file-name context]
  (print (selmer.parser/render-file (str "net/lewisship/cli_tools/" file-name ".tpl")
                                    context)))

(defn escape
  [s]
  (string/replace s "'" "\\'"))

(defn- to-opt
  [short-option long-option summary]
  (let [both (and long-option short-option)
        [long-option' option-name] (when long-option
                                     (string/split long-option #"\s+"))]
    (apply str
           ;; Make the short and long exclusive of each other
           (when both
             (str
               "'(" short-option " " long-option' ")'{"))
           short-option
           (when both ",")
           long-option'
           (when both "}")
           ;; The $' (instead of just ') allows for \' in the string to work
           ;; correctly.
           "$'["
           (escape summary)
           "]'"
           (when option-name
             ;; TODO: meta data on the option to say what completion kind it is.
             (str ":" option-name)))))

(defn- options
  [command]
  (let [{:keys [var]} command
        callable (requiring-resolve var)
        {:keys [command-options]} (callable)]
    (for [[short-option long-option summary] command-options]
      (to-opt short-option long-option summary))))

(defn- extract-command
  [fn-prefix [k command]]
  (when (string? k)
    (let [{:keys [group-category command-name command-summary]} command
          category? (some? group-category)]
      (if category?
        (let [command-name (:command-group group-category)
              fn-name      (simplify fn-prefix command-name)]
          {:name    command-name
           :summary (:label group-category)
           :fn-name fn-name
           :subs    (keep #(extract-command fn-name %) command)})
        (let [fn-name (simplify fn-prefix command-name)]
          {:name    command-name
           :summary command-summary
           :fn-name fn-name
           :options (options command)})))))

(defn- render-commands
  [tool-name commands]
  (doseq [command commands]
    (if (:subs command)
      (do
        (render "category" {:tool     tool-name
                            :category command})
        (render-commands tool-name (:subs command)))
      (render "command" {:tools   tool-name
                         :command command}))))

(defn print-tool
  [tool-name raw-commands]
  (let [commands (keep #(extract-command (str "_" tool-name) %) raw-commands)]
    (selmer.util/without-escaping
      (render "top-level" {:tool     tool-name
                           :commands commands})
      (render-commands tool-name commands))))

(defcommand completions
  "Generate zsh command completions.  Completions can be written
  to a file or to standard output."
  [:args
   output-path ["PATH" "File to write completions to."
                :optional true]]
  (binding [impl/*introspection-mode* true]
    (let [{:keys [commands tool-name]} impl/*options*]
      (if output-path
        (do
          (with-open [w (-> output-path
                            fs/file
                            io/output-stream
                            io/writer)]
            (try
              (binding [*out* w]
                (print-tool tool-name commands))
              (catch Throwable t
                (abort 1 t))))
          (perr [:cyan "Wrote " output-path]))
        ;; Just write to standard output
        (print-tool tool-name commands)))))

