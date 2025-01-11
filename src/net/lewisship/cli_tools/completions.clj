(ns net.lewisship.cli-tools.completions
  "Support for generating zsh command completion scripts for a command."
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [net.lewisship.cli-tools :refer [defcommand]]
            [clojure.string :as string]
            [selmer.parser :as selmer]
            [net.lewisship.cli-tools.impl :as impl]))

(defn- simplify
  [s]
  (string/replace s #"[^a-zA-Z0-9]+" "_"))

(defn- category-command
  [command-name command]
  {:name    command-name
   :summary (:command-summary command)})

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
  []
  ;; commands is two-levels only at this time.
  (let [{:keys [commands tool-name]} impl/*options*
        ;; A mix of categories and true single level commands (e.g., "help")
        top-commands (->> commands
                          (map (fn [[command-name command]]
                                 (top-level-command tool-name commands command-name command))))
        output       (selmer/render-file "net/lewisship/cli_tools/tool.tpl"
                                         {:tool     tool-name
                                          :commands top-commands})]
    (spit (str "/Users/howard.lewisship/zsh-completions/_" tool-name) output)))


