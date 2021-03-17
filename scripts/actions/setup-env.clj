#!/usr/bin/env bb

(require '[clojure.java.io :as io])

(defn getenv ^String [^String s] (System/getenv s))

(defn add-env
  "Add env var k=v to future GitHub Actions steps in this job"
  [k v]
  (println (str "Adding env var: " k "=" v))
  (spit (getenv "GITHUB_ENV")
        (str k "=" v "\n")
        :append true))

(io/make-parents (getenv "LOG_PATH"))

(assert (not (getenv "TRAVIS_EVENT_TYPE"))
        "Actions only")

(add-env "CTIA_TEST_SUITE"
         (if (= (getenv "GITHUB_EVENT_NAME")
                "schedule")
           "cron"
           "ci"))
