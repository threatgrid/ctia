(ns actions.setup-env-test
  (:require [clojure.test :refer [deftest is testing]]
            [actions.test-helpers :as th]
            [actions.setup-env :as sut]))

(deftest setup-env-test
  (let [mk-utils+sh #(let [{:keys [state] :as m} (th/mk-utils %)]
                       (assoc-in m [:utils :sh]
                                 (fn [& args]
                                   (swap! state update :history conj
                                          {:op :sh
                                           :args (vec args)})
                                   {:exit 0})))
        {:keys [state grab-history utils]} (mk-utils+sh {"LOG_PATH" "foo/bar"})
        _ (sut/setup-env utils)
        _ (is (= (grab-history)
                 [{:op :sh
                   :args ["mkdir" "-p" "foo/bar"]}]))

        _ (is (thrown?
                AssertionError
                #"Actions only"
                (sut/setup-env (:utils (mk-utils+sh {"LOG_PATH" "foo/bar"
                                                     "TRAVIS_EVENT_TYPE" "push"})))))
        _ (is (thrown?
                AssertionError
                #"Failed to create LOG_PATH"
                (sut/setup-env (:utils (assoc-in (th/mk-utils {"LOG_PATH" "foo/bar"})
                                                 [:utils :sh]
                                                 (constantly {:exit 1}))))))]))
