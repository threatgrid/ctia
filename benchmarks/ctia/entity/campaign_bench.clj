(ns ctia.entity.campaign-bench
  (:require
   [ctim.examples.campaigns :refer [new-campaign-minimal
                                    new-campaign-maximal]]
   [ctia.test-helpers
    [benchmark :refer [cleanup-ctia!
                       setup-ctia-es-store!]]
    [core :as helpers :refer [delete post]]]
   [perforate.core :refer :all]))

(def small-campaign
  new-campaign-minimal)

(def big-campaign
  (dissoc new-campaign-maximal :id))

(defgoal create-campaign "Create Campaign"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [_] cleanup-ctia!))

(defn play [fixture]
  (post "ctia/campaign"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defcase create-campaign :big-campaign-es-store
  [_] (play big-campaign))

(defcase create-campaign :small-campaign-es-store
  [_] (play small-campaign))
