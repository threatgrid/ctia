#!/usr/bin/env bb

(defn getenv ^String [^String s] (System/getenv ^String s))

(defn add-env
  "Add env var k=v to future GitHub Actions steps in this job"
  [k v]
  (println (str "Adding env var: " k "=" v))
  (spit (getenv "GITHUB_ENV")
        (str k "=" v "\n")
        :append true))

(-> (getenv "LOG_PATH") v File. .mkdirs)

(assert (not (System/getenv "TRAVIS_EVENT_TYPE"))
        "Actions only")

(add-env "CTIA_TEST_SUITE"
         (if (= (System/getenv "GITHUB_EVENT_NAME")
                "schedule")
           "cron"
           "ci"))
