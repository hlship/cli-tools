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
    (update-digest-from-string digest (.getCanonicalPath f'))
    ;; Would be better to digest the 8 raw bytes, but this is easier.
    (update-digest-from-string digest (Long/toHexString (.lastModified f')))))

(defn- update-digest-recursively
  [digest ^File root]
  (let [paths (fs/glob root "**.clj*")]
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
  "Passed digest options, return a hex string of the SHA-1 digest of the opts and the classpath."
  ^String [digest-options]
  (let [{:keys [source-dirs]} digest-options
        digest       (MessageDigest/getInstance "SHA-1")
        ;; The options determine critical things such as the namespaces that
        ;; will be scanned for defcommands.
        digest-bytes (do
                       (update-digest-from-string digest (pr-str digest-options))
                       (run! #(update-digest digest %) (get-split-classpath))
                       (->> source-dirs
                            (map fs/file)
                            (run! #(update-digest-recursively digest %)))
                       (.digest digest))]
    (hex-string digest-bytes)))

(defn read-from-cache
  [cache-root digest]
  (let [f (fs/file cache-root (str digest ".edn"))]
    (when (fs/exists? f)
      (try
        (-> f slurp edn/read-string)
        (catch Exception e
          (perr [:yellow "Exception reading from cache: " (ex-message e)])
          nil)))))

(defn write-to-cache
  [cache-root digest cache-data]
  (let [_ (when-not (fs/exists? cache-root)
            (fs/create-dir cache-root))
        f (fs/file cache-root (str digest ".edn"))]
    (spit f (pr-str cache-data))))

(comment
  (hash-classpath {:foo 1})

  (-> (System/getProperties) keys sort)

  (fs/glob (fs/file "target") "**.clj*")

  (get-classpath)
  (get-split-classpath)
  )

