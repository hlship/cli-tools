(ns net.lewisship.cli-tools.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [net.lewisship.cli-tools :as cli-tools])
  (:import (java.nio.file Path)))

(s/def ::dispatch-options (s/keys :req-un [::namespaces]
                                  :opt-un [::tool-name
                                           ::doc
                                           ::arguments
                                           ::groups
                                           ::transformer
                                           ::tool-options-handler
                                           ::extra-tool-options
                                           ::source-dirs
                                           ::cache-dir]))

(s/def ::non-blank-string (s/and string?
                                 #(not (str/blank? %))))

(s/def ::tool-name ::non-blank-string)

(s/def ::doc string?)

(s/def ::arguments (s/coll-of string?))

(s/def ::namespaces (s/coll-of simple-symbol?))

(s/def ::groups (s/map-of string? ::group))

(s/def ::group (s/keys :req-un [::namespaces]
                       :opt-un [::doc ::groups]))

(s/def ::cache-dir (s/nilable #(instance? Path %)))

(s/def ::transformer fn?)

(s/def ::extra-tool-options vector?)

(s/def ::tool-options-handler fn?)

(s/def ::source-dirs (s/coll-of string?))

;; dispatch doesn't return
(s/fdef cli-tools/dispatch :args (s/cat :options ::dispatch-options))
