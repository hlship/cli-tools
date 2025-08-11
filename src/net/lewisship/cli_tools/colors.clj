(ns net.lewisship.cli-tools.colors
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [clj-commons.ansi :refer [pout]]
            [clojure.string :as string]
            [net.lewisship.cli-tools :refer [defcommand]]))


(defcommand colors
  "Shows available foreground and background colors."
  []
  (let [all-colors         [:black :bright-black
                            :red :bright-red
                            :green :bright-green
                            :yellow :bright-yellow
                            :blue :bright-blue
                            :magenta :bright-magenta
                            :cyan :bright-cyan
                            :white :bright-white]
        normal-backgrounds [:black-bg
                            :red-bg
                            :green-bg
                            :yellow-bg
                            :blue-bg
                            :magenta-bg
                            :cyan-bg
                            :white-bg]
        bright-backgrounds [:bright-black-bg
                            :bright-red-bg
                            :bright-green-bg
                            :bright-yellow-bg
                            :bright-blue-bg
                            :bright-magenta-bg
                            :bright-cyan-bg
                            :bright-white-bg]
        width              (->> all-colors (map name) (map count) (reduce max 0))]
    (doseq [foreground all-colors]
      (pout [{:width width
              :align :center
              :font  foreground}
             (name foreground)]
            (for [background normal-backgrounds]
              (list " "
                    [{:width width
                      :align :center
                      :font  [foreground background]}
                     (-> (name background) (string/replace "-bg" ""))])))
      (pout [{:width width} " "]
            (for [background bright-backgrounds]
              (list " "
                    [{:width width
                      :align :center
                      :font  [foreground background]}
                     (-> (name background) (string/replace "-bg" ""))]))))))
