(ns net.lewisship.cli-tools.messy-test
  (:require [clj-commons.ansi :as ansi]
            [clojure.string :as string]
            [net.lewisship.cli-tools :as cli]
            [net.lewisship.cli-tools.aux :refer [capture-result]]
            [clojure.test :refer [deftest is]]))

(cli/set-prevent-exit! true)

(defn- dispatch [& args]
  (binding [ansi/*color-enabled* false]
    (capture-result
      (cli/dispatch {:tool-name  "bigmess"
                     :namespaces '[net.lewisship.messy-commands]
                     :groups     {"messy" {:namespaces '[net.lewisship.messy]
                                           #_ #_
                                           :doc "Messy command and group at same time"}}
                     :arguments  args
                     :cache-dir  nil
                     :messy?     true}))))

(deftest full-help
  (is (match? {:status 0
               :out    (slurp "test-resources/messy-full-help.txt")}
              (dispatch "help" "--full"))))

(deftest simple-commands-work
  (is (match? {:status 0
               :out    "simple: ok\n"}
              (dispatch "simp")))

  (is (match? {:status 0
               :out    "messy: kiwi ok\n"}
              (dispatch "messy" "kiwi"))))

(deftest missing-positional-in-nested
  (is (match? {:status 1
               :err    "Error in bigmess messy: No value for required argument NAME\n"}
              (dispatch "messy"))))

(deftest commands-nested-inside-commands
  (is (match? {:out "nested: ok\n"}
              (dispatch "mess" "nest"))))

(comment

 (defn capture [f & args]
   (let [{:keys [out err]} (binding [ansi/*color-enabled* false]
                             (apply dispatch args))
         captured (if (string/blank? out)
                    err)
         out-path (str "test-resources/" f ".txt")]
     (spit out-path captured)
     (println (str out-path ":"))
     (print captured)))

 (dispatch "messy" )

 (capture "messy-full-help" "help" "--full")

 (capture "messy-simple" "simple")

 (capture "messy-nested-fail" "mess" "nomatch")
 )


