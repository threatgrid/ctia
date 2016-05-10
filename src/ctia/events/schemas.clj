(ns ctia.events.schemas
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ModelEventBase
  {:owner s/Str
   (s/optional-key :timestamp) c/Time
   :entity {s/Any s/Any}
   :id s/Str
   (s/optional-key :http-params) {s/Any s/Any}})

(def CreateEventType "CreatedModel")

(s/defschema CreateEvent
  (st/merge
   ModelEventBase
   {:type (s/eq CreateEventType)
    :entity {s/Any s/Any}}))

(def UpdateEventType "UpdatedModel")

(s/defschema UpdateTriple
  [(s/one s/Keyword "field")
   (s/one s/Str "action")
   (s/one {s/Any s/Any} "metadata")])

(s/defschema UpdateEvent
  (st/merge
   ModelEventBase
   {:type (s/eq UpdateEventType)
    :fields [UpdateTriple]}))

(def DeleteEventType "DeletedModel")

(s/defschema DeleteEvent
  (st/merge
   ModelEventBase
   {:type (s/eq DeleteEventType)}))

(def VerdictChangeEventType "VerdictChange")

(s/defschema VerdictChangeEvent
  (st/merge
   ModelEventBase
   {:type (s/eq VerdictChangeEventType)
    :judgement_id s/Str
    :verdict v/Verdict}))

(s/defschema Event
  (s/conditional
   #(= CreateEventType        (:type %)) CreateEvent
   #(= UpdateEventType        (:type %)) UpdateEvent
   #(= DeleteEventType        (:type %)) DeleteEvent
   #(= VerdictChangeEventType (:type %)) VerdictChangeEvent))
