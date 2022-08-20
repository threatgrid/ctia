(ns ctia.flows.hooks.firehose-event-hook-test
  (:require
   [clj-momo.test-helpers.core :as mth]
   [clj-momo.test-helpers.http :as http]
   [clojure.instant :as instant]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.domain.entities :refer [schema-version]]
   [ctia.lib.firehose :as lf]
   [ctim.domain.id :as id]
   [cheshire.core :as json]
   [ctia.test-helpers.core :as test-helpers]
   [ctia.test-helpers.es :as es-helpers])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :once mth/fixture-schema-validation)

(use-fixtures :each
  (join-fixtures [es-helpers/fixture-properties:es-store
                  test-helpers/fixture-properties:firehose-hook
                  test-helpers/fixture-properties:events-enabled
                  test-helpers/fixture-allow-all-auth
                  test-helpers/fixture-ctia]))

(defn make-judgement [opts]
  (merge
   {:observable {:value "1.2.3.4"
                 :type "ip"}
    :disposition 3
    :source "source"
    :tlp "green"
    :priority 100
    :severity "High"
    :confidence "Low"
    :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}}
   opts))

(defn call-ctia
  [app opts]
  (let [body (make-judgement opts)
        created-judgement (test-helpers/assert-post app "ctia/judgement" body)]
    {:owner "Unknown"
     :groups ["Administrators"]
     :type "event"
     :tlp "green"
     :entity created-judgement
     :event_type "record-created"} ))

(defn get-firehose-destination
  ([]
   (get-firehose-destination ""))
  ([path]
   (:body
    (http/get (str "firehose-destination" "/" path) 4566))))

(defn update-instant
  [entity key-paths]
  (update-in entity
             key-paths
             (fnil instant/read-instant-timestamp nil)))

(defn clean-entity
  [entity]
  (-> entity
      (dissoc :created :modified)
      (update-instant [:valid_time :start_time])
      (update-instant [:valid_time :end_time])
      (update-instant [:timestamp])))

(defn clean-event
  [event]
  (let [parsed-event (json/decode event true)
        entity (:entity parsed-event)]
    (-> parsed-event
        (dissoc :id :timestamp)
        (update :entity clean-entity))))

(defn get-objects
  "Would I normally be regexing for something in a xml blob? No.
  Are we doing that here?!? Maybe?
  Is it fine for this one test case? probably.

  Localstack has a limitation of not returning json. Doing all
  the xml shenanigans when we have a known response payload seems
  unnecessary for a 'this is all fake anyways' check."
  []
  (->> (get-firehose-destination)
       (re-seq #"<Key>(test-output[\/\d+]+test-ctia-firehose-local[A-Za-z0-9-]+)</Key>")
       (map second)
       (reverse)
       (take 3)
       (map get-firehose-destination)
       (mapv clean-event)))


(deftest ^:integration test-events-created
  (testing "Events are put on firehose"
    (let [app (test-helpers/get-current-app)
          {:keys [get-in-config]} (test-helpers/get-service-map app :ConfigService)
          firehose-props (get-in-config [:ctia :hook :firehose])
          judgement-event-1 (call-ctia app {:disposition 1})
          judgement-event-2 (call-ctia app {:disposition 2})
          judgement-event-3 (call-ctia app {:disposition 3})]
      (is (= [judgement-event-3 judgement-event-2 judgement-event-1]
             (get-objects))))))
