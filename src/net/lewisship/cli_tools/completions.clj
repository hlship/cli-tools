(ns net.lewisship.cli-tools.completions
  "Support for generating zsh command completion scripts for a command."
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [babashka.fs :as fs]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]
            [clojure.string :as string]
            selmer.util
            selmer.parser
            [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools.impl :as impl]))

(defn- simplify
  [s]
  (string/replace s #"[^a-zA-Z0-9]+" "_"))

(defn- category-command
  [command-name command]
  {:name    command-name
   :summary (:command-summary command)})

(defn- fail
  [exception]
  (perr [:red [:bold "ERROR:"] " " (ex-message exception)])
  (cli/exit 1))

(defn- top-level-command
  [tool-name _commands command-name command]
  (let [{:keys [group-category]} command
        category? (some? group-category)
        fn-name   (simplify (str "_" tool-name "_" command-name))]
    {:name        command-name
     :leaf?       (not category?)
     :summary     (if category?
                    (:label group-category)
                    (:command-summary command))
     :subcommands (when category?
                    (->> command
                         keys
                         (filter string?)
                         (map #(category-command % (get command %)))))
     :fn-name     fn-name}))

(defcommand completions
  "Generate zsh command completions."
  [pipe? ["-p" "--pipe" "Write to standard output instead of writing to a file"]]
  ;; commands is two-levels only at this time.
  (let [{:keys [commands tool-name]} impl/*options*
        ;; A mix of categories and true single level commands (e.g., "help")
        top-commands (->> commands
                          (map (fn [[command-name command]]
                                 (top-level-command tool-name commands command-name command))))
        output       (selmer.util/without-escaping
                       (selmer.parser/render-file "net/lewisship/cli_tools/tool.tpl"
                                                  {:tool     tool-name
                                                   :commands top-commands}))]
    (if pipe?
      (print output)
      (let [output-dir  (fs/expand-home "~/zsh-completions")
            _           (when-not (fs/exists? output-dir)
                          (perr [:faint "Creating " output-dir " ..."])
                          (try
                            (fs/create-dir output-dir)
                            (catch Throwable t
                              (fail t))))
            output-file (fs/file output-dir (str "_" tool-name))]
        (try
          (spit output-file output)
          (catch Throwable t
            (fail t)))
        (perr [:cyan "Wrote " output-file])))))


