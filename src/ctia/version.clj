(ns ctia.version
  (:require [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.string :as st]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [properties]]
            [pandect.algo.sha1 :refer [sha1]]))

(def version-file "ctia-version.txt")

(def current-version
  (memoize #(if-let [built-version (io/resource version-file)]
              (slurp built-version)
              (str (:out (shell/sh "git" "log" "-n" "1" "--pretty=format:%H "))
                   (:out (shell/sh "git" "symbolic-ref" "--short" "HEAD"))))))

(def current-config-hash
  (memoize #(-> @properties
                set
                str
                .getBytes
                sha1)))

(defn version-data []
  {:base "/ctia"
   :ctim-version schema-version
   :beta true
   :ctia-build (st/replace (current-version) #"\s\n" "")
   :ctia-config (current-config-hash)
   :ctia-supported_features []})


(defn version-headers []
  {"X-Ctia-Version" (st/replace (current-version) #"\n" "")
   "X-Ctia-Config" (current-config-hash)
   "X-Ctim-Version" schema-version})
