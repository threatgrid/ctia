(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [ctia.lib.es.index :as es-index]
            [ctia.properties :refer [properties]]
            [ctia.store :as store]
            [ctia.stores.es.store :as es-store]
            [ctia.test-helpers.core :as h]))

(defn delete-state-indexes [{:keys [conn index config]}]
  (when conn
    (es-index/delete! conn (str index "*"))))

(defn fixture-delete-store-indexes [test]
  "walk through all the es stores delete each store indexes"
  (doseq [store-impls (vals @store/stores)
          {:keys [state]} store-impls]
    (delete-state-indexes state))
  (test))

(defn purge-event-indexes []
  (let [{:keys [conn index]} (es-store/init-store-conn
                              (merge
                               (get-in @properties [:ctia :store :es :default])
                               (get-in @properties [:ctia :store :es :event])))]
    (when conn
      (es-index/delete! conn (str index "*")))))

(defn fixture-purge-event-indexes [test]
  "walk through all producers and delete their index"
  (purge-event-indexes)
  (test)
  (purge-event-indexes))

(defn fixture-properties:es-store [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.store.es.default.shards" 1
                      "ctia.store.es.default.replicas" 1
                      "ctia.store.es.default.refresh" "wait_for"
                      "ctia.store.es.default.port" "9200"
                      "ctia.store.es.default.indexname" "test_ctia"
                      "ctia.store.es.actor.indexname" "ctia_actor"
                      "ctia.store.actor" "es"
                      "ctia.store.campaign" "es"
                      "ctia.store.coa" "es"
                      "ctia.store.data-table" "es"
                      "ctia.store.event" "es"
                      "ctia.store.exploit-target" "es"
                      "ctia.store.feedback" "es"
                      "ctia.store.identity" "es"
                      "ctia.store.incident" "es"
                      "ctia.store.indicator" "es"
                      "ctia.store.judgement" "es"
                      "ctia.store.relationship" "es"
                      "ctia.store.verdict" "es"
                      "ctia.store.sighting" "es"
                      "ctia.store.ttp" "es"]
    (test)))

(defn fixture-properties:es-hook [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"]
    (test)))

(defn fixture-properties:es-hook:aliased-index [test]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.port" 9200
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "aliased-index"
                      "ctia.hook.es.slicing.granularity" "week"]
    (test)))
