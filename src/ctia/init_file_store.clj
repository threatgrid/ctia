(ns ctia.init-file-store
  (:require [ctia.store :as store]
            [ctia.stores.file.actor :as actor]
            [ctia.stores.file.campaign :as campaign]
            [ctia.stores.file.coa :as coa]
            [ctia.stores.file.exploit-target :as target]
            [ctia.stores.file.feedback :as feedback]
            [ctia.stores.file.identity :as identity]
            [ctia.stores.file.incident :as incident]
            [ctia.stores.file.indicator :as indicator]
            [ctia.stores.file.judgement :as judgement]
            [ctia.stores.file.sighting :as sighting]
            [ctia.stores.file.ttp :as ttp]
            [alandipert.enduro :as e]))

(defn init-file-store! []
  (let [store-impls {:actor [store/actor-store actor/->ActorStore]
                     :campaign [store/campaign-store campaign/->CampaignStore]
                     :coa [store/coa-store coa/->COAStore]
                     :exploit-target [store/exploit-target-store
                                      target/->ExploitTargetStore]
                     :feedback [store/feedback-store feedback/->FeedbackStore]
                     :identity [store/identity-store identity/->IdentityStore]
                     :indicator [store/indicator-store
                                 indicator/->IndicatorStore]
                     :judgement [store/judgement-store
                                 judgement/->JudgementStore]
                     :sighting [store/sighting-store sighting/->SightingStore]
                     :ttp [store/ttp-store ttp/->TTPStore]}]
    (doseq [[key values] store-impls]
      (let [[store impl-fn] values]
        (reset! store (impl-fn (e/file-atom {}
                                            (str "/tmp/ctia/" (name key))
                                            :pending-dir "/tmp/ctia")))
        ))))

(comment
  (init-file-store!))
