#!/usr/bin/env bb

(ns actions.actions-helpers)

(defn getenv ^String [^String s] (System/getenv s))

(defn add-env
  "Add env var k=v to future GitHub Actions steps in this job"
  [k v]
  (println (str "Adding env var: " k "=" v))
  (spit (getenv "GITHUB_ENV")
        (str k "=" v "\n")
        :append true))
