#!/usr/bin/env bb

;; Example:
;; LOG_PATH=$(mktemp -d) ./scripts/actions/setup-env.clj

(ns actions.setup-env
  (:require [actions.actions-helpers :as h]))

(defn setup-env [{:keys [sh getenv]}]
  (let [log-path (getenv "LOG_PATH")]
    (assert log-path)
    (-> (sh "mkdir" "-p" log-path)
        :exit
        #{0}
        (assert "Failed to create LOG_PATH")))
  (assert (not (getenv "TRAVIS_EVENT_TYPE")) "Actions only"))

(defn -main [& _args]
  (setup-env h/utils))

(when (= *file* (System/getProperty "babashka.file")) (-main))
