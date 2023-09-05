(ns scale-test
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [selmer.parser :as selmer]))

(def root-dir "scale-test/uber")

(def command-name-terms
  (str/split "gnip frob update nerd spit snip echo gnop cluster system node module user role acl server service deployment"
             #"\s+"))

(defn gen-single-command-name
  []
  (let [terms (repeatedly 3 #(rand-nth command-name-terms))]
    (str/join "-" terms)))

(def command-names
  (->> (repeatedly gen-single-command-name)
       distinct))


(defn render
  [from to context-map]
  (let [output      (selmer/render-file (str "templates/" from) context-map)
        output-path (str root-dir "/" to)]
    (println "Writing:" output-path)
    (spit output-path output)))

(defn write-ns
  [i command-names]
  (let [ns-name (str "commands.command-ns-" i)
        ns-file (-> ns-name
                    (str/replace "-" "_")
                    (str/replace "." "/")
                    )]
    (render "command-ns.edn"
            (str "src/" ns-file ".clj")
            {:ns            ns-name
             :command-names command-names})
    ns-name))

(defn setup
  {:org.babashka/cli {:coerce {:namespaces :long
                               :commands   :long}}}
  [opts]
  (fs/delete-tree root-dir)
  (fs/create-dirs (str root-dir "/src/commands"))
  (let [{:keys [commands namespaces]
         :or   {commands   5
                namespaces 10}} opts
        commands-per-ns (->> command-names
                             (partition commands)
                             (take namespaces))
        ns-names        (doall (map-indexed write-ns commands-per-ns))]
    (render "bb.edn" "bb.edn" {})
    (render "app.edn" "app" {:ns-names ns-names})
    (fs/set-posix-file-permissions (str root-dir "/app") "rwxrwxrwx")
    (printf "%,d namespaces, %,d commands/namespace, %,d total command%n"
            namespaces commands (* namespaces commands))))
