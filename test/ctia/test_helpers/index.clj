(ns ctia.test-helpers.index
  "ES Index test helpers"
  (:require [ctia.store :as store]
            [ctia.lib.es.index :as es-index]
            [ctia.stores.es.store :as es-store]
            [ctia.events.producers.es.producer :as es-producer]
            [ctia.test-helpers.core :as h]
            [ctia.properties :as properties]))

(def store-conn-state-fixture
  "for testing the same es conn for all ES Stores"
  (atom nil))

(def producer-conn-state-fixture
  "for testing the same es conn for all ES Stores"
  (atom nil))

(defn clean-index! [state-fixture]
  "delete and recreate the index"
  (es-index/delete! (:conn state-fixture)
                    (:index state-fixture))
  (es-index/create! (:conn state-fixture)
                    (:index state-fixture)
                    (:mapping state-fixture)))

(defn fixture-clean-store-index [f]
  (clean-index! @store-conn-state-fixture)
  (f))

(defn fixture-clean-producer-index [f]
  (clean-index! @producer-conn-state-fixture)
  (f))

(defn init-store-state [f]
  "spawn a store state
   with a conn index name and mapping as state"
  (fn []
    (f @store-conn-state-fixture)))

(defn init-producer-state [f]
  "spawn a producer state
   with a conn index name and mapping as state"
  (fn []
    (f @producer-conn-state-fixture)))

(def es-stores
  {store/actor-store          (init-store-state es-store/->ActorStore)
   store/judgement-store      (init-store-state es-store/->JudgementStore)
   store/feedback-store       (init-store-state es-store/->FeedbackStore)
   store/campaign-store       (init-store-state es-store/->CampaignStore)
   store/coa-store            (init-store-state es-store/->COAStore)
   store/exploit-target-store (init-store-state es-store/->ExploitTargetStore)
   store/incident-store       (init-store-state es-store/->IncidentStore)
   store/indicator-store      (init-store-state es-store/->IndicatorStore)
   store/ttp-store            (init-store-state es-store/->TTPStore)
   store/sighting-store       (init-store-state es-store/->SightingStore)
   store/identity-store       (init-store-state es-store/->IdentityStore)})

(def es-producer
  (init-producer-state es-producer/->EventProducer))

(def fixture-es-store
  (do
    (properties/init!)
    (reset! store-conn-state-fixture
            (es-index/init-store-conn))
    (h/fixture-store es-stores)))

(def fixture-es-producer
  (do
    (properties/init!)
    (reset! producer-conn-state-fixture
            (es-index/init-producer-conn))
    (h/fixture-producers [es-producer])))
