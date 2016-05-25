(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [ctia.events.producers.es.producer :as esp]
            [ctia.lib.es.index :as es-index]
            [ctia.properties :as properties]
            [ctia.store :as store]
            [ctia.test-helpers.core :as h]))

(defn recreate-state-index [state]
  (when (:conn state)
    (es-index/delete! (:conn state)
                      (:index state))

    (es-index/create! (:conn state)
                      (:index state)
                      (:mapping state))))

(defn fixture-recreate-store-indexes [test]
  "walk through all the stores delete and recreate each store index"

  (doseq [store (vals store/stores)]
    (recreate-state-index (:state @store)))
  (test))

(defn purge-producer-indexes []
  (let [{:keys [conn index]} (esp/init-producer-conn)]
    (when conn
      ((es-index/index-delete-fn conn) conn (str index "*")))))

(defn fixture-purge-producer-indexes [test]
  "walk through all producers and delete their index"
  (purge-producer-indexes)
  (test)
  (purge-producer-indexes))

(defn fixture-properties:es-store [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.default.type" "es"
                      "ctia.store.default.refresh" true
                      "ctia.store.default.uri" "http://192.168.99.100:9200"
                      "ctia.store.default.indexname" "test_ctia"
                      "ctia.store.actor.indexname" "ctia_actor"]
    (test)))

(defn fixture-properties:es-hook [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.uri" "http://192.168.99.100:9200"
                      "ctia.hook.es.indexname" "test_ctia_events"]
    (test)))

(defn fixture-properties:es-hook:aliased-index [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.uri" "http://192.168.99.100:9200"
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "aliased-index"
                      "ctia.hook.es.slicing.granularity" "week"]
    (test)))

(defn fixture-properties:es-hook:filtered-alias [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.uri" "http://192.168.99.100:9200"
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "filtered-alias"
                      "ctia.hook.es.slicing.granularity" "hour"]
    (test)))
