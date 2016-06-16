(ns ctia.http.routes.actor-bench
  (:refer-clojure :exclude [get])
  (:require [perforate.core :refer :all]
            [ctia.init :refer [start-ctia!]]
            [ctia.http.server :as http-server]
            [ctia.flows.hooks :as hooks]
            [ctia.events :as events]
            [ctia.test-helpers.es :as esh]
            [ctia.test-helpers.core :refer [get post put delete] :as helpers]))

(def small-actor
  {:title "actor"
   :description "description"
   :actor_type "Hacker"
   :source "a source"
   :confidence "High"
   :associated_actors [{:actor_id "actor-123"}
                       {:actor_id "actor-456"}]
   :associated_campaigns [{:campaign_id "campaign-444"}
                          {:campaign_id "campaign-555"}]
   :observed_TTPs [{:ttp_id "ttp-333"}
                   {:ttp_id "ttp-999"}]
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                :end_time "2016-07-11T00:40:48.212-00:00"}})

(defn gen [n k pref]
  (map (fn [i] {k (str pref "-" i)}) (range n)))

(def big-actor
  {:title "actor"
   :description "description"
   :actor_type "Hacker"
   :source "a source"
   :confidence "High"
   :associated_actors (gen 100 :actor_id "actor")
   :associated_campaigns (gen 100 :campaign_id "campaign")
   :observed_TTPs (gen 100 :ttp_id "ttp")
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                :end_time "2016-07-11T00:40:48.212-00:00"}})

;; -----------------------------------------------------------------------------
(defgoal create-actor "Create Actor"
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
        (post "ctia/actor"
              :body big-actor
              :headers {"api_key" "45c1f5e3f05d0"})]
    (if (= 201 status)
      (delete (str "ctia/actor" (:id parsed_body)))
      (prn "play-big: " status))))

(defcase create-actor :big-actor-atom-store []
  (helpers/fixture-properties:atom-store play-big))

(defcase create-actor :big-actor-es-store []
  (esh/fixture-properties:es-store play-big))

(defcase create-actor :big-actor-es-store-native []
  (esh/fixture-properties:es-store-native play-big))
