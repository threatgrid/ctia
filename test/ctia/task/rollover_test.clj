(ns ctia.task.rollover-test
  (:require [clj-momo.lib.es.index :as es-index]
            [clojure.string :as string]
            [clojure.test :refer [deftest is join-fixtures use-fixtures]]
            [ctia.stores.es.init :as init]
            [ctia.task.rollover :as sut]
            [ctia.test-helpers.core :as helpers :refer [post-bulk]]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]))

(use-fixtures :once
  (join-fixtures [whoami-helpers/fixture-server
                  whoami-helpers/fixture-reset-state
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store]))

(use-fixtures :each
  (join-fixtures [helpers/fixture-ctia
                  es-helpers/fixture-delete-store-indexes]))

(def examples (fixt/bundle 100 false))

(deftest rollover-aliased-test
  (let [get-in-config (helpers/current-get-in-config-fn)
        props-not-aliased {:entity :malware
                           :indexname "ctia_malware"
                           :host "localhost"
                           :port 9200}
        state-not-aliased (init/init-es-conn! props-not-aliased get-in-config)
        rollover-not-aliased (sut/rollover-store state-not-aliased)
        props-aliased {:entity :sighting
                       :indexname "ctia_sighting"
                       :host "localhost"
                       :port 9200
                       :aliased true
                       :rollover {:max_docs 3}
                       :refresh "true"}
        state-aliased (init/init-es-conn! props-aliased get-in-config)
        rollover-aliased (sut/rollover-store state-aliased)

        count-index #(count (es-index/get (:conn state-aliased)
                                          (str (:index state-aliased) "*")))]
    (is (nil? rollover-not-aliased))
    (is (seq rollover-aliased))
    (is (false? (:rolled_over rollover-aliased)))
    (is (= 1 (count-index)))
    (post-bulk examples)
    (es-index/refresh! (:conn state-aliased))
    (is (nil? (sut/rollover-store state-not-aliased)))
    (is (= 1 (count-index)))
    (is (true? (:rolled_over (sut/rollover-store state-aliased))))
    (is (= 2 (count-index)))
    (post-bulk examples)
    (es-index/refresh! (:conn state-aliased))
    (is (true? (:rolled_over (sut/rollover-store state-aliased))))
    (is (= 3 (count-index)))))

(deftest rollover-stores-error-test
  (with-redefs [es-index/rollover! (fn [_ alias _]
                                     (if (string/starts-with? alias "ok_index")
                                       {:rolled_over (rand-nth [true false])}
                                       (throw (ex-info "that's baaaaaaaddd"
                                                       {:code :unhappy}))))]
    (let [app (helpers/get-current-app)
          get-in-config (helpers/current-get-in-config-fn app)
          services {:ConfigService {:get-in-config get-in-config}}
          ok-state (init/init-store-conn {:entity "sighting"
                                          :indexname "ok_index"
                                          :rollover {:max_docs 3}
                                          :aliased true}
                                         services)
          ko-state (init/init-store-conn {:entity "sighting"
                                          :indexname "bbaaaaadddd_index"
                                          :rollover {:max_docs 2}
                                          :aliased true}
                                         services)
          stores {:ok-type-1 [{:state ok-state}]
                  :ok-type-2 [{:state ok-state}]
                  :ok-type-3 [{:state ok-state}]
                  :ko-type-1 [{:state ko-state}]
                  :ko-type-2 [{:state ko-state}]}
          {:keys [nb-errors
                  ok-type-1
                  ok-type-2
                  ok-type-3
                  ko-type-1
                  ko-type-2]} (sut/rollover-stores stores)]
      (is (= 2 nb-errors))
      (is (every? nil? [ko-type-1 ko-type-2]))
      (is (every? #(some? (:rolled_over %))
                  [ok-type-1 ok-type-2 ok-type-3])))))
