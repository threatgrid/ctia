#!/usr/bin/env bb

(ns actions.setup-env
  (:require [actions.actions-helpers :refer [add-env getenv]]))

(-> (getenv "LOG_PATH") File. .mkdirs assert)

(assert (not (getenv "TRAVIS_EVENT_TYPE")) "Actions only")
