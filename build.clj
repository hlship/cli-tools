;; clj -T:build <var>

(ns build
  (:require [clojure.tools.build.api :as build]
            [net.lewisship.build :as b]))

(def lib 'io.github.hlship/cli-tools)
(def version "0.8")

(def jar-params {:project-name lib
                 :version version})

(defn clean
  [_params]
  (build/delete {:path "target"}))

(defn jar
  [_params]
  (b/create-jar jar-params))

(defn deploy
  [_params]
  (clean nil)
  (-> jar-params
      b/create-jar
      (assoc :sign-artifacts? false)
      b/deploy-jar))

(defn codox
  [_params]
  (b/generate-codox jar-params))
