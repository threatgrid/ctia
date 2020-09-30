(ns ctia.entity.campaign-bench
  (:require
   [ctim.examples.campaigns :refer [new-campaign-minimal
                                    new-campaign-maximal]]
   [ctia.test-helpers
    [benchmark :refer [cleanup-ctia!
                       setup-ctia-es-store!]]
    [core :as helpers :refer [POST]]]
   [perforate.core :refer :all]))

(def small-campaign
  new-campaign-minimal)

(def big-campaign
  (dissoc new-campaign-maximal :id))

(defgoal create-campaign "Create Campaign"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [{:keys [app]}] (cleanup-ctia! app)))

(defn play [app fixture]
  (POST app
        "ctia/campaign"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defcase create-campaign :big-campaign-es-store
  [{:keys [app]}] (play app big-campaign))

(defcase create-campaign :small-campaign-es-store
  [{:keys [app]}] (play app small-campaign))
