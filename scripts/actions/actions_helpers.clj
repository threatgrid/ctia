#!/usr/bin/env bb

(ns actions.actions-helpers)

(def )

(defn getenv ^String [^String s] (System/getenv s))

(defn add-env
  "Add env var k=v to future GitHub Actions steps in this job"
  [k v]
  (println (str "Adding env var: " k "=" v))
  (spit (getenv "GITHUB_ENV")
        (str k "=" v "\n")
        :append true))

(defn print-set-output
  "Create 'output' `n` for this Actions step, accessible with ${{ <stepid>.outputs.<n> }}."
  [n v]
  ;; Actions does not print ::set-output commands to the build output
  (println (format "DEBUG: Setting output: %s %s" n v))
  (println (format "::set-output name=%s::%s" n v)))
