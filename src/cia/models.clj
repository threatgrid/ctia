(ns cia.models
  (:require [cia.schemas.vocabularies :refer :all]
            [ring.swagger.schema :refer [coerce!]]
            [schema.core :as s]))


(def CIAFeature
  (s/enum "Judgements" "Verdicts"
          "Threats" "Relations" "Feeds"
          "Feedback" "COAs" "ExploitTargets"))

(s/defschema VersionInfo
  {:id Long
   :base URI
   :version String
   :beta Boolean
   :supported_features [s/Str]})

(def default-version-info
  {:id "local-cia"
   :base "http://localhost:3000"
   :version "0.1"
   :supported_features ["Judgements" "Verdicts" "JudgementIndicators"]})

(def SpecificationType
  "Types of Indicator we support Currently only Judgement indicators,
  which contain a list of Judgements associated with this indicator."
  (s/enum "Judgement" "ThreatBrain" "SIOC" "Snort" "OpenIOC"))


(defonce id-seq (atom 0))
(defonce judgements (atom (array-map)))

(defn get-judgement [id] (@judgements id))
(defn get-judgements [] (-> judgements deref vals reverse))
(defn find-judgements
  ([kind]
   (filter #(= kind (:observable_type %))
           (get-judgements)))
  ([kind val]
   (filter #(and (= kind (:observable_type %))
                 (= val (:observable %)))
           (get-judgements))))


(defn current-verdict [kind val]
  (first (find-judgements kind val)))

(defn delete! [id] (swap! judgements dissoc id) nil)

(defn add! [new-judgement]
  (let [id (swap! id-seq inc)
        disp (coerce! Judgement (assoc new-judgement :id id))]
    (swap! judgements assoc id disp)
    disp))
