(ns net.lewisship.cli-tools.colors
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [clj-commons.ansi :refer [pout]]
            [clojure.string :as string]
            [net.lewisship.cli-tools :refer [defcommand]]))

(def ^:private width 16)

(defcommand colors
  "Shows available foreground and background colors."
  []
  (doseq [foreground [:black :bright-black
                      :red :bright-red
                      :green :bright-green
                      :yellow :bright-yellow
                      :blue :bright-blue
                      :magenta :bright-magenta
                      :cyan :bright-cyan
                      :white :bright-white]]
    (pout [{:width width
            :pad   :both
            :font  foreground}
           (name foreground)]
          (for [background [:black-bg
                            :red-bg
                            :green-bg
                            :yellow-bg
                            :blue-bg
                            :magenta-bg
                            :cyan-bg
                            :white-bg]]
            (list " "
                  [{:width width
                    :pad   :both
                    :font  [foreground background]}
                   (-> (name background) (string/replace "-bg" ""))])))
    (pout [{:width width} " "]
          (for [background [:bright-black-bg
                            :bright-red-bg
                            :bright-green-bg
                            :bright-yellow-bg
                            :bright-blue-bg
                            :bright-magenta-bg
                            :bright-cyan-bg
                            :bright-white-bg]]
            (list " "
                  [{:width width
                    :pad   :both
                    :font  [foreground background]}
                   (-> (name background) (string/replace "-bg" ""))])))))
