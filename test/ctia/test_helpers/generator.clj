(ns ctia.test-helpers.generator
  (:require [ctia.lib.time :as time]
            [ctia.schemas.actor :refer [Actor]]
            [ctia.schemas.campaign :refer [Campaign]]
            [ctia.schemas.coa :refer [COA]]
            [ctia.schemas.exploit-target :refer [ExploitTarget]]
            [ctia.schemas.feedback :refer [Feedback]]
            [ctia.schemas.identity :refer [Identity]]
            [ctia.schemas.incident :refer [Incident]]
            [ctia.schemas.indicator :refer [Indicator]]
            [ctia.schemas.judgement :refer [Judgement]]
            [ctia.schemas.sighting :refer [Sighting]]
            [ctia.schemas.ttp :refer [TTP]]
            [ctia.schemas.verdict :refer [Verdict]]
            [schema.experimental.generators :as g]
            [schema.core :as s]
            [clojure.test.check.generators :as gen]))


(def default-complexity 20)


(def leaf-generators
  {java.util.Date
   ;; very simplistic randomized date
   (gen/fmap #(-> (time/now)
                  time/plus-n-weeks) gen/int)})

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
