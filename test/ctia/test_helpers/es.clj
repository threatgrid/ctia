(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [ctia.events.producers.es.producer :as esp]
            [ctia.lib.es.index :as es-index]
            [ctia.store :as store]
            [ctia.test-helpers.core :as h]))

(defn recreate-state-index [{:keys [conn index config]}]
  (when conn
    (es-index/delete! conn index)
    
    (es-index/create! conn index config)))

(defn close-client [{:keys [conn]}]
  "if the connection is native, close the client"
  (when (instance? org.elasticsearch.client.transport.TransportClient conn)
    (.close conn)))

(defn fixture-recreate-store-indexes [test]
  "walk through all the es stores delete and recreate each store index"
  (doseq [store-impls (vals @store/stores)
          {:keys [state]} store-impls]
    (recreate-state-index state))
  (test)
  (doseq [store-impls (vals @store/stores)
          {:keys [state]} store-impls]
    (close-client state)))

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
  (h/with-properties ["ctia.store.es.default.transport" "http"
                      "ctia.store.es.default.refresh" true
                      "ctia.store.es.default.port" "9200"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.actor" "es"
                      "ctia.store.campaign" "es"
                      "ctia.store.coa" "es"
                      "ctia.store.data-table" "es"
                      "ctia.store.exploit-target" "es"
                      "ctia.store.feedback" "es"
                      "ctia.store.identity" "es"
                      "ctia.store.incident" "es"
                      "ctia.store.indicator" "es"
                      "ctia.store.judgement" "es"
                      "ctia.store.relationship" "es"
                      "ctia.store.verdict" "es"
                      "ctia.store.sighting" "es"
                      "ctia.store.ttp" "es"
                      "ctia.store.bundle" "es"]
    (test)))


(defn fixture-properties:es-store-native [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.es.default.transport" "native"
                      "ctia.store.es.default.refresh" true
                      "ctia.store.es.default.port" "9300"
                      "ctia.store.es.default.clustername" "elasticsearch"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.actor" "es"
                      "ctia.store.campaign" "es"
                      "ctia.store.coa" "es"
                      "ctia.store.data-table" "es"
                      "ctia.store.exploit-target" "es"
                      "ctia.store.feedback" "es"
                      "ctia.store.identity" "es"
                      "ctia.store.incident" "es"
                      "ctia.store.indicator" "es"
                      "ctia.store.judgement" "es"
                      "ctia.store.relationship" "es"
                      "ctia.store.verdict" "es"
                      "ctia.store.sighting" "es"
                      "ctia.store.ttp" "es"
                      "ctia.store.bundle" "es"]
    (test)))


(defn fixture-properties:es-hook [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.transport" "http"
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"]
    (test)))

(defn fixture-properties:es-hook:aliased-index [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.transport" "http"
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "aliased-index"
                      "ctia.hook.es.slicing.granularity" "week"]
    (test)))

(defn fixture-properties:es-hook:filtered-alias [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.transport" "http"
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "filtered-alias"
                      "ctia.hook.es.slicing.granularity" "hour"]
    (test)))
