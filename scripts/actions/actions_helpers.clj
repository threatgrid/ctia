#!/usr/bin/env bb

(ns actions.actions-helpers
  (:require [cheshire.core :as json]
            [clojure.java.shell :as sh]))

(defn append-file-var
  "Add entry k=v to file pointed to by environment variable file-var.
  
  eg., useful for:
    echo '{name}={value}' >> $GITHUB_STATE"
  [{:keys [getenv]} file-var k v]
  {:pre [(string? file-var)]}
  (println (str "Adding file var " file-var ": " k "=" v))
  (let [f (getenv file-var)]
    (assert f (str "append-file-var: file is null! " file-var))
    (spit f
          (str k "=" v "\n")
          :append true)))

(defn add-env
  "Add env var k=v to future GitHub Actions steps in this job"
  [utils k v]
  (println (str "Adding env var: " k "=" v))
  (append-file-var utils "GITHUB_ENV" k v))

(defn getenv ^String [^String s] (System/getenv s))

(defn set-json-output
  "Create JSON 'output' `n` for this Actions step, accessible with ${{ fromJSON(<stepid>.outputs.<n>) }}."
  [{:keys [set-output] :as utils} n v]
  (set-output utils n (json/generate-string v {:pretty false})))

(defn set-output
  "Create 'output' `n` for this Actions step, accessible with ${{ <stepid>.outputs.<n> }}."
  [utils n s]
  {:pre [(string? s)]}
  ;; Actions does not print ::set-output commands to the build output
  (println (format "DEBUG: Setting output: %s %s" n s))
  (append-file-var utils "GITHUB_OUTPUT" n s))

(defn sh [& args]
  (apply sh/sh args))

(def utils
  "Stateful things we'd want to stub in tests."
  {:sh sh
   :getenv getenv
   :add-env add-env
   :set-output set-output
   :set-json-output set-json-output})
