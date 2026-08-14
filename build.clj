;; clj -T:build <var>

(ns build
  (:require [clojure.tools.build.api :as build]
            [net.lewisship.build :as b]))

(def lib 'io.github.hlship/cli-tools)

(defn- resolve-version
  "Version comes from :version, or the exact git tag on HEAD."
  [{:keys [version]}]
  (or version
      (build/git-process {:git-args ["describe" "--tags" "--exact-match"]})
      (throw (ex-info "No version: pass :version or check out a tagged commit" {}))))

(defn- jar-params
  [params]
  {:project-name lib
   :version      (resolve-version params)
   :aliases      [:pom]})

(defn clean
  [_params]
  (build/delete {:path "target"}))

(defn jar
  [params]
  (b/create-jar (jar-params params)))

(defn install
  [params]
  (b/install-jar (jar params)))

(defn deploy
  [params]
  (clean nil)
  (-> (jar-params params)
      b/create-jar
      (assoc :sign-artifacts? false)
      b/deploy-jar))

(defn codox
  [params]
  (b/generate-codox (jar-params params)))
