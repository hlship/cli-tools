(ns net.lewisship.cli-tools.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [net.lewisship.cli-tools :as cli-tools]))

(s/def ::dispatch-options (s/keys :req-un [::namespaces]
                                  :opt-un [::tool-name ::doc ::arguments ::groups]))
(s/def ::non-blank-string (s/and string?
                                 #(not (str/blank? %))))
(s/def ::tool-name ::non-blank-string)
(s/def ::doc string?)
(s/def ::arguments (s/coll-of string?))
(s/def ::namespaces (s/coll-of simple-symbol?))

(s/def ::groups (s/map-of string? ::group))

(s/def ::group (s/keys :req-un [::namespaces]
                       :opt-un [::doc ::groups]))

;; dispatch doesn't return
(s/fdef cli-tools/dispatch :args (s/cat :options ::dispatch-options))
