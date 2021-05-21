#!/usr/bin/env bb

;; Example:
;; LOG_PATH=$(mktemp -d) ./scripts/actions/setup-env.clj

(ns actions.setup-env
  (:require [actions.actions-helpers :refer [getenv]]
            [clojure.java.shell :as sh]))

(defn -main [& _args]
  (let [log-path (getenv "LOG_PATH")]
    (assert log-path)
    (-> (sh/sh "mkdir" "-p" log-path)
        :exit
        #{0}
        assert))

  (assert (not (getenv "TRAVIS_EVENT_TYPE")) "Actions only"))

(when (= *file* (System/getProperty "babashka.file")) (-main))
