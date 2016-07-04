(ns ctia.http.routes.actor-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-atom-store!
                                setup-ctia-es-store!
                                setup-ctia-es-store-native!]]
             [core :as helpers :refer [delete post]]]
            [perforate.core :refer :all]))

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

(defgoal create-actor "Create Actor"
  :setup (fn [] [true])
  :cleanup (fn [_]))

(defn play-big [port]
  (let [{:keys [status parsed_body]}
        (post "ctia/actor"
              :body big-actor
              :port port
              :headers {"api_key" "45c1f5e3f05d0"})]
    (if (= 201 status)
      (delete (str "ctia/actor" (:id parsed_body)))
      (prn "play-big: " status))))

(defcase* create-actor :big-actor-atom-store
  (fn [_] (let [port (setup-ctia-atom-store!)]
           [#(play-big port) cleanup-ctia!])))

(defcase* create-actor :big-actor-es-store
  (fn [_] (let [port (setup-ctia-es-store!)]
           [#(play-big port) cleanup-ctia!])))

(defcase* create-actor :big-actor-es-store-native
  (fn [_] (let [port (setup-ctia-es-store-native!)]
           [#(play-big port) cleanup-ctia!])))
