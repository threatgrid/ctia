(ns ctia.flows.autoload-test
  (:require [ctia.flows.autoload :as auto]
            [ctia.flows.hooks :as h]
            [ctia.test-helpers.core :as helpers]
            [clojure.test :as t]))

(t/use-fixtures :once (t/join-fixtures [helpers/fixture-properties:clean
                                        helpers/fixture-ctia-fast]))


(def obj {:x "x" :y 0 :z {:foo "bar"}})

(t/deftest check-autoloaded-hooks
  (do
    (h/reset-hooks!)
    (auto/autoload-hooks! [:test])
    (h/init-hooks!)
    (t/is (= (h/apply-hooks :entity obj
                            :hook-type :before-create)
             (into obj
                   {"autoloaded1 - initialized" "passed-from-autoloaded-jar1"
                    "autoloaded2 - initialized" "passed-from-autoloaded-jar2"
                    "HookExample1" "Passed in HookExample1"
                    "HookExample2" "Passed in HookExample2"})))
    (t/is (= (h/apply-hooks :entity obj
                            :hook-type :after-create
                            :read-only? true)
             obj))
    (h/reset-hooks!)))
