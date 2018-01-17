(ns redismq-java-client.example-test
  (:require [cheshire.core :as json]
            [clj-momo.properties :as mp]
            [clojure.spec.alpha :as cs]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [ctim.events.schemas :refer [CreateEventType]]
            [ctim.generators.id :as gen-id]
            [ctim.schemas
             [actor :refer [StoredActor]]
             [coa :refer [StoredCOA] :as coa]]
            [flanders.spec :as fs]
            [redismq.core :as rmq]
            [schema.core :as s]
            [taoensso.carmine :as car])
  (:import [client Example]))

(cs/def ::actor (fs/->spec StoredActor "stored-actor"))
(cs/def ::coa (fs/->spec StoredCOA "stored-coa"))

(defn read-config []
  (->> (mp/read-property-files ["default-example.properties"
                                "example.properties"])
       mp/transform
       (mp/coerce-properties {:redis {:host s/Str
                                      :port s/Int
                                      :timeout-ms s/Int
                                      :queue-name s/Str}})))

(defn build-queue [{{:keys [host port queue-name timeout-ms]} :redis
                    :as _config_}]
  (rmq/make-queue queue-name
                  {:host host
                   :port port
                   :timeout-ms timeout-ms}
                  {:max-depth 100}))

(def gen-actor-create-event
  (gen/fmap (fn [[actor event-id]]
              {:entity actor
               :type CreateEventType
               :id event-id})
            (gen/tuple (cs/gen ::actor)
                       (gen-id/gen-short-id-of-type :actor))))

(def gen-coa-create-event
  (gen/fmap (fn [[coa event-id]]
              {:entity coa
               :type CreateEventType
               :id event-id})
            (gen/tuple (cs/gen ::coa)
                       (gen-id/gen-short-id-of-type :event))))

(def gen-some-events
  (gen/vector (gen/frequency [[2 gen-actor-create-event]
                              [1 gen-coa-create-event]])
              10))

(defn property:can-count-coa-events [config queue]
  (for-all
   [events gen-some-events]

   (let [{{:keys [host port queue-name]} :redis} config]

     ;; Empty the queue
     (car/wcar queue
               (car/ltrim queue-name 0 -1))

     ;; Load the queue
     (doseq [event events]
       (rmq/enqueue queue event))

     ;; Count COA events
     (let [expected-count (count (filter #(= coa/type-identifier
                                             (get-in % [:entity :type]))
                                         events))
           actual-count (Example/countCoaEvents host port queue-name)]
       (= expected-count actual-count)))))

(defspec example-spec
  (let [config (read-config)
        queue (build-queue config)]
    (property:can-count-coa-events config queue)))
