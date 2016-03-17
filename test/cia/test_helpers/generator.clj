(ns cia.test-helpers.generator
  (:require
   [cia.schemas.actor :refer [Actor]]
   [cia.schemas.campaign :refer [Campaign]]
   [cia.schemas.coa :refer [COA]]
   [cia.schemas.exploit-target :refer [ExploitTarget]]
   [cia.schemas.feedback :refer [Feedback]]
   [cia.schemas.identity :refer [Identity]]
   [cia.schemas.incident :refer [Incident]]
   [cia.schemas.indicator :refer [Indicator]]
   [cia.schemas.judgement :refer [Judgement]]
   [cia.schemas.sighting :refer [Sighting]]
   [cia.schemas.ttp :refer [TTP]]
   [cia.schemas.verdict :refer [Verdict]]
   [schema.experimental.generators :as g]
   [schema.core :as s]
   [clj-time.core :as t]
   [clojure.test.check.generators :as gen]))


(def default-complexity 20)


(def leaf-generators
  {org.joda.time.DateTime
   ;; very simplistic randomized date
   (gen/fmap #(-> (t/now)
                  (t/plus (t/weeks %))) gen/int)})

(def schema-mapping
  "a map to easily request a schema"
  {:actor Actor
   :campaign Campaign
   :coa COA
   :exploit-target ExploitTarget
   :feedback Feedback
   :identity Identity
   :incident Incident
   :indicator Indicator
   :judgement Judgement
   :sighting Sighting
   :ttp TTP
   :verdict Verdict})

(defn schema-kw->schema [schema-kw]
  "get a schema from a schema keyword"
  (get schema-mapping schema-kw))

(defn generate [num schema]
  "generate num records of a schema"
  (gen/sample
   (g/generator schema leaf-generators) num))

(defn generate-by-kw [num schema-kw]
  "generate num records of a schema-kw"
  (generate num (schema-kw->schema schema-kw)))

(defn generate-random-model-record []
  (let [model (->> schema-mapping
                   (map val)
                   rand-nth)]

    (last (generate default-complexity model))))

(defn random-records []
  "use this to generate many random model records
   --> (take 10 (random-records))"
  (repeatedly #(generate-random-model-record)))
