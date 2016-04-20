(ns ctia.flows.autoload-test
  (:require [ctia.flows.autoload :as auto]
            [ctia.flows.hooks :as h]
            [clojure.test :as t]))


(def obj {:x "x" :y 0 :z {:foo "bar"}})

;; ## Test Autoload
(t/deftest check-autoloaded-hooks
  (do
    (h/reset-hooks!)
    (auto/autoload-hooks! [:test])
    (h/init-hooks!)
    (t/is (= (h/apply-hooks :type-name "foo"
                            :realized-object obj
                            :hook-type :before-create)
             (into obj
                   {"autoloaded1 - initialized" "passed-from-autoloaded-jar1"
                    "autoloaded2 - initialized" "passed-from-autoloaded-jar2"
                    "HookExample1" "Passed inHookExample1"
                    "HookExample2" "Passed inHookExample2"})))
    (t/is (= (h/apply-hooks :type-name "foo"
                            :realized-object obj
                            :hook-type :after-create)
             obj))
    (h/reset-hooks!)))
