(ns ctia.flows.autoload-test
  (:require [clj-momo.test-helpers
             [core :as mth]]
            [clojure.core.async :as a]
            [ctia.flows.autoload :as auto]
            [ctia.flows.hooks :as h]
            [ctia.lib.async :as la]
            [ctia.test-helpers.atom :as ath]
            [ctia.test-helpers.core :as th]
            [clojure.test :refer [deftest is join-fixtures use-fixtures]]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each (join-fixtures [th/fixture-properties:clean
                                    ath/fixture-properties:atom-memory-store
                                    th/fixture-properties:hook-classes
                                    th/fixture-ctia-fast]))

;; -----------------------------------------------------------------------------
;; Test autoloaded hooks

(def obj {:x "x" :y 0 :z {:foo "bar"}})

(deftest check-autoloaded-hooks
  (is (= (-> (h/apply-hooks :entity-chan (la/onto-chan (a/chan) [obj])
                            :hook-type :before-create)
             la/drain-timed
             th/only)
         (assoc obj
               "autoloaded1 - initialized" "passed-from-autoloaded-jar1"
               "autoloaded2 - initialized" "passed-from-autoloaded-jar2"
               "HookExample1" "Passed in HookExample1"
               "HookExample2" "Passed in HookExample2")))
  (is (= (-> (h/apply-hooks :entity-chan (la/onto-chan (a/chan) [obj])
                            :hook-type :after-create
                            :read-only? true)
             la/drain-timed
             th/only)
         obj)))
