#!/usr/bin/env bb

;; https://book.babashka.org/#_running_tests
(ns test-runner
  (:require [clojure.test :as t]))

(def test-namespaces
  '[actions.print-matrix-test])

(apply require test-namespaces)                  

(def test-results
  (apply t/run-tests test-namespaces))           

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))                                 

(System/exit failures-and-errors)                    
