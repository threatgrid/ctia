(ns ctia.test-helpers.index
  "ES Index test helpers"
  (:require [ctia.store :as store]
            [ctia.lib.es.index :as es-index]
            [ctia.stores.es.store :as es-store]
            [ctia.events.producers.es.producer :as es-producer]
            [ctia.test-helpers.core :as h]
            [ctia.properties :as properties]))

;; (def store-conn-state-fixture
;;   "for testing the same es conn for all ES Stores"
;;   (atom nil))

;; (def producer-conn-state-fixture
;;   "for testing the same es conn for all ES Stores"
;;   (atom nil))

(defn clean-index! [state-fixture]
  "delete and recreate the index"
  (es-index/delete! (:conn state-fixture)
                    (:index state-fixture))
  (es-index/create! (:conn state-fixture)
                    (:index state-fixture)
                    (:mapping state-fixture)))

(defn fixture-clean-store-index [f]
  ;;(clean-index! @store-conn-state-fixture)
  (f))

(defn fixture-clean-producer-index [f]
  ;;(clean-index! @producer-conn-state-fixture)
  (f))

(defn init-store-state [f]
  "spawn a store state
   with a conn index name and mapping as state"
  (fn []
    ;;(f @store-conn-state-fixture)
    ))

(defn init-producer-state [f]
  "spawn a producer state
   with a conn index name and mapping as state"
  (fn []
    ;;(f @producer-conn-state-fixture)
    ))

(defn fixture-properties:es-store [test]
  ;; Note: These properties may be overwritten by ENV variables (like for CI)
  (h/with-properties ["ctia.store.type" "es"
                      "ctia.store.es.uri" "http://192.168.99.100:9200"
                      "ctia.producer.es.uri" "http://192.168.99.100:9200"
                      ;;"ctia.store.es.host" ""
                      ;;"cita.producer.es.host" ""
                      "ctia.store.es.port" 9300
                      "ctia.producer.es.port" 9300
                      "ctia.store.es.clustername" "elasticsearch"
                      "ctia.producer.es.clustername" "elasticsearch"
                      "ctia.store.es.indexname" "test_ctia"
                      "ctia.producer.es.indexname" "test_ctia_events"]
    (test)))

;; (def es-producer
;;   (init-producer-state es-producer/->EventProducer))

;; (defn fixture-es-store []
;;   (do
;;     (properties/init!)
;;     (reset! store-conn-state-fixture
;;             (es-index/init-store-conn))
;;     (h/fixture-store es-stores)))

;; (defn fixture-es-producer []
;;   (do
;;     (properties/init!)
;;     (reset! producer-conn-state-fixture
;;             (es-index/init-producer-conn))
;;     (h/fixture-producers [es-producer])))
