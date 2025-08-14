(ns net.lewisship.cli-tools.custom-tool-handler-test
  (:require [clojure.test :refer [deftest is]]
            [net.lewisship.cli-tools :as cli-tools]
            [net.lewisship.cli-tools.alt-handler :as alt-handler]
            [net.lewisship.cli-tools.aux :refer [with-exit]]))

(cli-tools/set-prevent-exit! true)

(deftest can-provide-an-alternative-handler
  (is (= (slurp "test-resources/alt-tool-help.txt")
         (with-exit 0
                    (alt-handler/-main "-h")))))

(deftest can-implement-tool-level-options
  (is (= "Global state: Success!\n"
         (with-out-str (alt-handler/-main "-g" "Success!" "print")))))
