#!/usr/bin/env bb

;; warm deps cache for all permutations of the build

(require '[clojure.java.shell :as sh])

(defn sh [& args]
  (let [{:keys [exit out err] :as res} (apply sh/sh args)] 
    (assert (= 0 exit) exit)
    (some-> err not-empty println)
    (some-> out not-empty println)
    res))

(def all-ci-profiles
  (-> (sh "lein" "all-ci-profiles")
      :out
      read-string))
(assert (map? all-ci-profiles))

(doseq [p (vals all-ci-profiles)]
  (println (str "CI profile: " p))
  ;; combining with-profile calls with `lein do with-profile .., with-profile ..` does not seem to isolate
  ;; the profiles in each call, using separate lein calls seems more reliable.
  (sh "lein" "with-profile" p "do" "deps" ":tree," "deps" ":plugin-tree"))
