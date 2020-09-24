(ns ctia.version
  (:require [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.string :as st]
            [ctia.domain.entities :refer [schema-version]]))

(def version-file "ctia-version.txt")

(def current-version
  (memoize #(if-let [built-version (io/resource version-file)]
              (slurp built-version)
              (str (:out (shell/sh "git" "log" "-n" "1" "--pretty=format:%H "))
                   (:out (shell/sh "git" "symbolic-ref" "--short" "HEAD"))))))

(defn current-config-version [get-in-config]
  (get-in-config [:ctia :versions :config] ""))

(defn version-data [get-in-config]
  {:base "/ctia"
   :ctim-version schema-version
   :beta true
   :ctia-build (st/replace (current-version) #"\s\n" "")
   :ctia-config (current-config-version get-in-config)
   :ctia-supported_features []})


(defn version-headers [get-in-config]
  {"X-Ctia-Version" (st/replace (current-version) #"\n" "")
   "X-Ctia-Config" (current-config-version get-in-config)
   "X-Ctim-Version" schema-version})
