(ns ctia.flows.hooks.redismq-event-hook-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.lib.redis :as lr]
            [redismq.core :as rmq]
            [ctia.flows.hooks.event-hooks :as hooks]
            [ctia.flows.hooks.event-hooks :as eh]
            
            [ctia.properties :refer [properties]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [atom :as at-helpers]
             [core :as test-helpers :refer [post]]])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(defn fixture-properties:redismq-hook [f]
  (test-helpers/with-properties ["ctia.hook.redismq.enabled" true
                                 "ctia.hook.redismq.queue-name" "test-ctim-event-queue"]
    (f)))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    at-helpers/fixture-properties:atom-memory-store
                                    fixture-properties:redismq-hook
                                    test-helpers/fixture-ctia
                                    test-helpers/fixture-allow-all-auth]))

(deftest ^:integration test-redismq
  (testing "Events are published to redismq queue"
    (let [queue (:queue (eh/redismq-publisher))]
      (rmq/flush-queue queue)
      (post "ctia/judgement"
            :body {:observable {:value "1.2.3.4"
                                :type "ip"}
                   :disposition 1
                   :source "source"
                   :tlp "green"
                   :priority 100
                   :severity 100
                   :confidence "Low"
                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
      (post "ctia/judgement"
            :body {:observable {:value "1.2.3.4"
                                :type "ip"}
                   :disposition 2
                   :source "source"
                   :tlp "green"
                   :priority 100
                   :severity 100
                   :confidence "Low"
                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
      (post "ctia/judgement"
            :body {:observable {:value "1.2.3.4"
                                :type "ip"}
                   :disposition 3
                   :source "source"
                   :tlp "green"
                   :priority 100
                   :severity 100
                   :confidence "Low"
                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
      (is (= 3 (rmq/current-depth queue)))
      )))
