(ns ctia.flows.hooks.redismq-event-hook-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.lib.redis :as lr]
            [redismq.core :as rmq]
            [ctia.flows.hooks.event-hooks :as hooks]
            [ctia.flows.hooks.event-hooks :as eh]

            [ctim.domain.id :as id]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [core :as test-helpers :refer [POST]]
             [es :as es-helpers]])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(defn fixture-properties:redismq-hook [f]
  (test-helpers/with-properties ["ctia.hook.redismq.enabled" true
                                 "ctia.hook.redismq.queue-name" "test-ctim-event-queue"]
    (f)))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each (join-fixtures [es-helpers/fixture-properties:es-store
                                    fixture-properties:redismq-hook
                                    test-helpers/fixture-properties:events-enabled
                                    test-helpers/fixture-allow-all-auth
                                    test-helpers/fixture-ctia]))

(deftest ^:integration test-redismq
  (testing "Events are published to redismq queue"
    (let [app (test-helpers/get-current-app)
          get-in-config (test-helpers/current-get-in-config-fn app)
          queue (:queue (eh/redismq-publisher get-in-config))]
      (rmq/flush-queue queue)
      (POST app
            "ctia/judgement"
            :body {:observable {:value "1.2.3.4"
                                :type "ip"}
                   :disposition 1
                   :source "source"
                   :tlp "green"
                   :priority 100
                   :severity "High"
                   :confidence "Low"
                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
      (POST app
            "ctia/judgement"
            :body {:observable {:value "1.2.3.4"
                                :type "ip"}
                   :disposition 2
                   :source "source"
                   :tlp "green"
                   :priority 100
                   :severity "High"
                   :confidence "Low"
                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
      (POST app
            "ctia/judgement"
            :body {:observable {:value "1.2.3.4"
                                :type "ip"}
                   :disposition 3
                   :source "source"
                   :tlp "green"
                   :priority 100
                   :severity "High"
                   :confidence "Low"
                   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})
      (is (= 3 (rmq/current-depth queue))))))
