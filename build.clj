;; clj -T:build <var>

(ns build
  (:require [clojure.tools.build.api :as b]
            [net.lewisship.build :refer [requiring-invoke] :as bt]))

(def lib 'io.github.hlship/cli-tools)
(def version "0.7")

(def jar-params {:project-name lib
                 :version version})

(defn clean
  [_params]
  (b/delete {:path "target"}))

(defn jar
  [_params]
  (bt/create-jar jar-params))

(defn deploy
  [_params]
  (clean nil)
  (-> jar-params
      bt/create-jar
      bt/deploy-jar))

(defn codox
  [_params]
  (bt/generate-codox jar-params))
