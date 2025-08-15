(ns net.lewisship.cli-tools.custom-tool-handler-test
  (:require [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools :as cli-tools]
            [net.lewisship.cli-tools.alt-handler :as alt-handler]
            [net.lewisship.cli-tools.aux :refer [capture-result]]))

(cli-tools/set-prevent-exit! true)

(deftest can-provide-an-alternative-handler
  (is (match? {:status 0
               :out    (slurp "test-resources/alt-tool-help.txt")}
              (capture-result
                    (alt-handler/-main "-h")))))

(deftest can-implement-tool-level-options
  (is (= "Global state: Success!\n"
         (with-out-str (alt-handler/-main "-g" "Success!" "print")))))
