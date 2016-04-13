(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [ctia.store :as store]
            [ctia.events.producer :refer [event-producers]]
            [ctia.lib.es.index :as es-index]
            [ctia.stores.es.store :as es-store]
            [ctia.test-helpers.core :as h]
            [ctia.properties :as properties]))


(defn recreate-state-index [state]
  (when (:conn state)
    (es-index/delete! (:conn state)
                      (:index state))

    (es-index/create! (:conn state)
                      (:index state)
                      (:mapping state))))

(defn fixture-recreate-store-indexes [test]
  "walk through all the stores delete and recreate each store index"
  (dorun
   (map (fn [[store-name store-state]]
          (recreate-state-index
           (:state @store-state)))
        store/stores))
  (test))

(defn fixture-recreate-producer-indexes [test]
  "walk through all the producers delete and recreate each producer index"
  (dorun
   (map #(recreate-state-index (:state %)) @event-producers))
  (test))


(defn fixture-properties:es-store [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.type" "es"
                      "ctia.store.es.uri" "http://192.168.99.100:9200"
                      "ctia.store.es.port" 9300
                      "ctia.store.es.clustername" "elasticsearch"
                      "ctia.store.es.indexname" "test_ctia"]
    (test)))


(defn fixture-properties:es-producer [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.producer.es.uri" "http://192.168.99.100:9200"
                      "ctia.producer.es.indexname" "test_ctia_events"]
    (test)))
