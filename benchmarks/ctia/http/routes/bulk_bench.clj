(ns ctia.http.routes.bulk-bench
  "Benchmark could be launched by `lein bench bulk`"
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-atom-store!
                                setup-ctia-es-store!
                                setup-ctia-es-store-native!]]
             [core :as helpers :refer [delete post]]]
            [clj-momo.lib.time :refer [now]]
            [criterium.core :as crit]
            [clojure.test.check.generators :as tcg]
            [ctim.generators.schemas :as gen]
            [ctia.schemas.bulk :refer [NewBulk]]
            [schema.core :as s]
            [perforate.core :refer :all]
            [clojure.tools.logging :as log]))

(s/defn bulk :- NewBulk
  "Generate a bulk with n sub entities of each type"
  [n :- s/Num]
  {:actors (tcg/sample (gen/gen-entity :new-actor) n)
   :campaigns (tcg/sample (gen/gen-entity :new-campaign) n)
   :coas (tcg/sample (gen/gen-entity :new-coa) n)
   :exploit-targets (tcg/sample (gen/gen-entity :new-exploit-target) n)
   :feedbacks (tcg/sample (gen/gen-entity :new-feedback) n)
   :incidents (tcg/sample (gen/gen-entity :new-incident) n)
   :indicators (tcg/sample (gen/gen-entity :new-indicator) n)
   :judgements (tcg/sample (gen/gen-entity :new-judgement) n)
   ;; :sightings (tcg/sample (gen/gen-entity :new-sighting) n)
   :ttps (tcg/sample (gen/gen-entity :new-ttp) n)})

(defgoal create-bulk "Create Bulk"
  :setup (fn [] [true])
  :cleanup (fn [_]))

(defn play-create-bulk [port]
  (try
    (let [nb-entities 20
          b (bulk nb-entities)]
      (log/infof  "[%s] Creating a bulk with %d entities"
                  (now)
                  (* (count (keys b)) nb-entities))
      (let [{:keys [status parsed_body]}
            (post "ctia/bulk"
                  :body b
                  :port port
                  :socket-timeout 120000
                  :conn-timeout 120000
                  :headers {"api_key" "45c1f5e3f05d0"})]
        (when-not (= 201 status)
          (prn "play-create-bulk: " status))))
    (catch Exception e
      (prn e)
      nil)))

(defcase* create-bulk :bulk-atom-store
  (fn [_] (let [port (setup-ctia-atom-store!)]
           [#(play-create-bulk port) cleanup-ctia!])))

(defcase* create-bulk :bulk-es-store
  (fn [_] (let [port (setup-ctia-es-store!)]
           [#(play-create-bulk port) cleanup-ctia!])))

(defcase* create-bulk :bulk-es-store-native
  (fn [_] (let [port (setup-ctia-es-store-native!)]
           [#(play-create-bulk port) cleanup-ctia!])))


(defn fmap
  [f m]
  (into (empty m)
        (for [[k v] m]
          [k (f v)])))

(defn fast-timing
  []
  (fmap (fn [setup-fn]
          (let [port (setup-fn)
                duration (with-out-str (time (play-create-bulk port)))]
            (cleanup-ctia! nil)
            duration))
        {:atom-store setup-ctia-atom-store!
         :es-store setup-ctia-es-store!
         :es-native-store setup-ctia-es-store-native!}))
