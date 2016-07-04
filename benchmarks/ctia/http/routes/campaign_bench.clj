(ns ctia.http.routes.campaign-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-atom-store!
                                setup-ctia-es-store!
                                setup-ctia-es-store-native!]]
             [core :as helpers :refer [delete post]]]
            [perforate.core :refer :all]))

(def small-campaign
  {:title "campaign"
   :description "description"
   :tlp "red"
   :campaign_type "anything goes here"
   :intended_effect ["Theft"]
   :indicators [{:indicator_id "indicator-foo"}
                {:indicator_id "indicator-bar"}]
   :attribution [{:confidence "High"
                  :source "source"
                  :relationship "relationship"
                  :actor_id "actor-123"}]
   :related_incidents [{:confidence "High"
                        :source "source"
                        :relationship "relationship"
                        :incident_id "incident-222"}]
   :related_TTPs [{:confidence "High"
                   :source "source"
                   :relationship "relationship"
                   :ttp_id "ttp-999"}]
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                :end_time "2016-07-11T00:40:48.212-00:00"}})

(defn gen [n k pref]
  (map (fn [i] {k (str pref "-" i)}) (range n)))

(def big-campaign
  {:title "campaign"
   :description "description"
   :tlp "red"
   :campaign_type "anything goes here"
   :intended_effect ["Theft"]
   :indicators (gen 100 :indicator_id "indicator")
   :attribution [{:confidence "High"
                  :source "source"
                  :relationship "relationship"
                  :actor_id "actor-123"}]
   :related_incidents (gen 100 :incident_id "incident")
   :related_TTPs      (gen 100 :ttp_id "ttp")
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                :end_time "2016-07-11T00:40:48.212-00:00"}})

(defgoal create-campaign "Create Campaign"
  :setup (fn [] [true])
  :cleanup (fn [_]))

(defn play-big [port]
  (let [{:keys [status parsed_body]}
        (post "ctia/campaign"
              :port port
              :body big-campaign
              :headers {"api_key" "45c1f5e3f05d0"})]
    (if (= 201 status)
      (delete (str "ctia/campaign" (:id parsed_body)))
      (prn "play-big: " status))))

(defcase* create-campaign :big-campaign-atom-store
  (fn [_] (let [port (setup-ctia-atom-store!)]
           [#(play-big port) cleanup-ctia!])))

(defcase* create-campaign :big-campaign-es-store
  (fn [_] (let [port (setup-ctia-es-store!)]
           [#(play-big port) cleanup-ctia!])))

(defcase* create-campaign :big-campaign-es-store-native
  (fn [_] (let [port (setup-ctia-es-store-native!)]
           [#(play-big port) cleanup-ctia!])))
