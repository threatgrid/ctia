(ns ctia.http.routes.actor-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-es-store!]]
             [core :as helpers :refer [DELETE POST]]]
            [perforate.core :refer :all]))

(def small-actor
  {:title "actor"
   :description "description"
   :actor_type "Hacker"
   :source "a source"
   :confidence "High"
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
   :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"
                :end_time "2016-07-11T00:40:48.212-00:00"}})

(defgoal create-actor "Create Actor"
  :setup (fn [] [true])
  :cleanup (fn [_]))

(defn play-big [app port]
  (let [{:keys [status parsed_body]}
        (POST app
              "ctia/actor"
              :body big-actor
              :port port
              :headers {"Authorization" "45c1f5e3f05d0"})]
    (if (= 201 status)
      (DELETE app (str "ctia/actor" (:id parsed_body)))
      (prn "play-big: " status))))

(defcase* create-actor :big-actor-es-store
  (fn [_] (let [{:keys [port app]} (setup-ctia-es-store!)]
           [#(play-big app port) #(cleanup-ctia! app)])))
