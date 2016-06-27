(ns ctia.http.routes.campaign-bench
  (:refer-clojure :exclude [get])
  (:require [perforate.core :refer :all]
            [ctia.init :refer [start-ctia!]]
            [ctia.http.server :as http-server]
            [ctia.flows.hooks :as hooks]
            [ctia.events :as events]
            [ctia.test-helpers.es :as esh]
            [ctia.test-helpers.core :refer [get post put delete] :as helpers]))

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

;; -----------------------------------------------------------------------------
(defgoal create-campaign "Create Campaign"
  :setup (fn []
           (let [http-port (helpers/available-port)]
             (println "Default: Launch CTIA on port" http-port)
             (helpers/fixture-properties:clean
              (fn []
                (helpers/with-properties ["ctia.http.enabled" true
                                          "ctia.http.port" http-port
                                          "ctia.http.show.port" http-port]
                  (start-ctia! :join? false))))))
  :cleanup (fn []
             (http-server/stop!)
             (hooks/shutdown!)
             (events/shutdown!)))

(defn play-big []
  (let [{:keys [status parsed_body]}
        (post "ctia/campaign"
              :body big-campaign
              :headers {"api_key" "45c1f5e3f05d0"})]
    (if (= 201 status)
      (delete (str "ctia/campaign" (:id parsed_body)))
      (prn "play-big: " status))))

(defcase create-campaign :big-campaign-atom-store []
  (helpers/fixture-properties:atom-store play-big))

(defcase create-campaign :big-campaign-es-store []
  (esh/fixture-properties:es-store play-big))

(defcase create-campaign :big-campaign-es-store-native []
  (esh/fixture-properties:es-store-native play-big))
