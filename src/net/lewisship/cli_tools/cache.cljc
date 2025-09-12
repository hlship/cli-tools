(ns net.lewisship.cli-tools.cache
  "Use a cache between executions to avoid excess namespace loading when executing."
  {:no-doc true
   :added  "0.12.0"}
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-commons.ansi :refer [perr]]
            #?(:bb [babashka.classpath :as cp]))
  (:import [java.io File]
           [java.security MessageDigest]))

(defn- get-classpath
  []
  #?(
     :bb  (cp/get-classpath)
     :clj (System/getProperty "java.class.path")))

(defn- get-split-classpath
  []
  (let [sep   fs/path-separator
        paths (-> (get-classpath)
                  (str/split (re-pattern sep)))]

    (->> paths
         sort
         (mapv io/file))))

;; Bababashka does not have DigestOutputStream

(defn- update-digest-from-string
  [^MessageDigest digest ^String value]
  (let [bytes (.getBytes value "UTF-8")]
    (.update digest bytes)))

(defn- update-digest-from-file-contents
  [^MessageDigest digest f]
  (let [f'     (fs/file f)]
    ;; Would be better to digest the 8 raw bytes, but this is easier.
    (update-digest-from-string digest (Long/toHexString (.lastModified f')))))

(defn- update-digest-recursively
  [digest ^File root]
  (let [paths (fs/glob root "**")]
    (run! #(update-digest-from-file-contents digest %) paths)))

(defn- update-digest
  [digest source]
  (let [f (fs/file source)]
    (if (.isFile f)
      ;; The assumption is that files are .jar files and the name will change if
      ;; the contents change.
      (update-digest-from-string digest (.getCanonicalPath f))
      ;; But for a source directory, find all the sources (and digest their contents).
      (update-digest-recursively digest f))))

(defn- hex-string [^bytes input]
  (let [sb (StringBuilder.)]
    (run! #(.append sb (format "%X" %)) input)
    (str sb)))

(defn classpath-digest
  "Passed the tool options, return a hex string of the SHA-1 digest of the files from the classpath and
  extra source directories."
  ^String [tool-options]
  (let [{:keys [source-dirs]} tool-options
        digest       (MessageDigest/getInstance "SHA-1")
        digest-bytes (do
                       (run! #(update-digest digest %) (get-split-classpath))
                       (->> source-dirs
                            (map fs/file)
                            (run! #(update-digest-recursively digest %)))
                       (.digest digest))]
    (hex-string digest-bytes)))

(defn read-from-cache
  [cache-root tool-name digest]
  (let [cache-file (fs/file cache-root tool-name (str digest ".edn"))]
    (when (fs/exists? cache-file)
      (try
        (-> cache-file slurp edn/read-string)
        (catch Exception e
          (perr [:yellow "Exception reading from cache: " (ex-message e)])
          nil)))))

(defn write-to-cache
  [cache-root tool-name digest cache-data]
  (let [cache-file (fs/file cache-root tool-name (str digest ".edn"))
        dir        (fs/parent cache-file)]
    (when-not (fs/exists? dir)
      (fs/create-dirs dir))
    (spit cache-file (pr-str cache-data))))
