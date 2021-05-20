#!/usr/bin/env bb

(ns actions.actions-helpers
  (:require [cheshire.core :as json]))

(defn getenv ^String [^String s] (System/getenv s))

(defn add-env
  "Add env var k=v to future GitHub Actions steps in this job"
  [k v]
  (println (str "Adding env var: " k "=" v))
  (spit (getenv "GITHUB_ENV")
        (str k "=" v "\n")
        :append true))

(defn set-output
  "Create 'output' `n` for this Actions step, accessible with ${{ <stepid>.outputs.<n> }}."
  [n s]
  ;; Actions does not print ::set-output commands to the build output
  (println (format "DEBUG: Setting output: %s %s" n s))
  (println (format "::set-output name=%s::%s" n s)))

(defn set-json-output
  "Create JSON 'output' `n` for this Actions step, accessible with ${{ fromJSON(<stepid>.outputs.<n>) }}."
  [{:keys [set-output]} n v]
  (set-output
    n
    (json/generate-string v {:pretty false})))

(def utils
  {:getenv getenv
   :add-env add-env
   :set-output set-output
   :set-json-output set-json-output})
