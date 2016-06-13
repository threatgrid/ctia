(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [ctia.events.producers.es.producer :as esp]
            [ctia.lib.es.index :as es-index]
            [ctia.store :as store]
            [ctia.test-helpers.core :as h]))

(defn recreate-state-index [{:keys [conn index mapping]}]
  (when conn
    (es-index/delete! conn
                      index)

    (es-index/create! conn
                      index
                      mapping)))

(defn fixture-recreate-store-indexes [test]
  "walk through all the es stores delete and recreate each store index"

  (doseq [store-impls (vals @store/stores)]
    (doseq [{:keys [state]} store-impls]
      (recreate-state-index state)))
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
  (h/with-properties ["ctia.store.es.default.refresh" true
                      "ctia.store.es.default.uri" "http://192.168.99.100:9200"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.actor" "es"
                      "ctia.store.campaign" "es"
                      "ctia.store.coa" "es"
                      "ctia.store.exploit-target" "es"
                      "ctia.store.feedback" "es"
                      "ctia.store.identity" "es"
                      "ctia.store.incident" "es"
                      "ctia.store.indicator" "es"
                      "ctia.store.judgement" "es"
                      "ctia.store.verdict" "es"
                      "ctia.store.sighting" "es"
                      "ctia.store.ttp" "es"]
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
