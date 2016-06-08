(ns ctia.version
  (:require [clojure.java
             [io :as io]
             [shell :as shell]]))

(def version-file "ctia-version.txt")

(def current-version
  (memoize #(if-let [built-version (io/resource version-file)]
              built-version
              (str (:out (shell/sh "git" "log" "-n" "1" "--pretty=format:%H "))
                   (:out (shell/sh "git" "symbolic-ref" "--short" "HEAD"))))))
