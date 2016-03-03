(ns cia.test-helpers.index
  (:require [cia.store :as store]
            [cia.stores.es.index :as es-index :refer [es-conn]]
            [cia.stores.es.mapping :as mapping]
            [cia.stores.es.store :as es-store]
            [cia.test-helpers.core :as h]
            [clojure.java.io :as io]))

(defn clean-index! []
  (es-index/delete! es-conn)
  (es-index/create! es-conn))

(defn fixture-init-index [test]
  (try
    (es-index/init!)
    (clean-index!)
    (test)
    (es-index/delete! es-conn)
    (catch org.elasticsearch.client.transport.NoNodeAvailableException e
      (do  (println "ES instance Unavailable")
           (test)))))

(defn fixture-clean-index [f]
  (clean-index!)
  (f))

(defn init-state [f]
  (fn []
    (f {:conn es-conn})))

(def es-stores
  {store/actor-store          (init-state es-store/->ActorStore)
   store/judgement-store      (init-state es-store/->JudgementStore)
   store/feedback-store       (init-state es-store/->FeedbackStore)
   store/campaign-store       (init-state es-store/->CampaignStore)
   store/coa-store            (init-state es-store/->COAStore)
   store/exploit-target-store (init-state es-store/->ExploitTargetStore)
   store/incident-store       (init-state es-store/->IncidentStore)
   store/indicator-store      (init-state es-store/->IndicatorStore)
   store/ttp-store            (init-state es-store/->TTPStore)})


(def fixture-es-store
  (h/fixture-store es-stores))
