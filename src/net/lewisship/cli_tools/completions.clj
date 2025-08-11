(ns net.lewisship.cli-tools.completions
  "Support for generating zsh command completion scripts for a command."
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [net.lewisship.cli-tools :refer [defcommand abort command-path]]
            [clojure.string :as string]
            selmer.util
            selmer.parser
            [clj-commons.ansi :as ansi :refer [perr]]
            [net.lewisship.cli-tools.impl :as impl]))

(defn- simplify
  [& s]
  (-> (string/join "_" s)
      (string/replace #"[^a-zA-Z0-9_]+" "_")))

(defn- render
  [file-name context]
  (print (selmer.parser/render-file (str "net/lewisship/cli_tools/" file-name ".tpl")
                                    context)))

(defn- escape
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
  [command-map]
  (let [{:keys [fn]} command-map
        callable (requiring-resolve fn)
        {:keys [command-options]} (callable)]
    (for [[short-option long-option summary] command-options]
      (to-opt short-option long-option summary))))

(defn- extract-command
  [fn-prefix [command-name command-map]]
  (let [{:keys [fn]} command-map
        title   (binding [ansi/*color-enabled* false]
                  (impl/extract-command-title command-map))
        fn-name (simplify fn-prefix command-name)]
    (if fn
      {:name    command-name
       :fn-name fn-name
       :summary title
       :options (options command-map)}
      {:name    (->> command-map
                     :command-path
                     (string/join " "))
       :summary title
       :fn-name fn-name
       :subs    (map #(extract-command fn-name %) (:subs command-map))})))

(defn- render-commands
  [tool-name commands]
  (doseq [command commands]
    (if (:subs command)
      (do
        (render "group" {:tool  tool-name
                         :group command})
        (render-commands tool-name (:subs command)))
      (render "command" {:tools   tool-name
                         :command command}))))

(defn- print-tool
  [tool-name command-root _groups]
  (let [prefix   (str "_" tool-name)
        commands (->> command-root
                      (keep #(extract-command prefix %)))]
    (selmer.util/without-escaping
      (render "top-level" {:tool     tool-name
                           :commands commands})
      (render-commands tool-name commands))))

(defcommand ^{:added "0.15"} completions
  "Generate zsh command completions.  Completions can be written
  to a file or to standard output."
  [:args
   output-path ["PATH" "File to write completions to."
                :optional true]]
  (binding [impl/*introspection-mode* true]
    (let [{:keys [command-root tool-name groups]} impl/*options*]
      (if output-path
        (do
          (with-open [w (-> output-path
                            fs/file
                            io/output-stream
                            io/writer)]
            (try
              (binding [*out* w]
                (print-tool tool-name command-root groups))
              (catch Throwable t
                (abort 1 [:red
                          (command-path) ": "
                          (or (ex-message t)
                              (class t))]))))
          (perr [:cyan "Wrote " output-path]))
        ;; Just write to standard output
        (print-tool tool-name command-root groups)))))

