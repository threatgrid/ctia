(ns ctia.events.schemas
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ModelEventBase
  {:owner s/Str
   (s/optional-key :timestamp) c/Time
   :model (s/if string? s/Str {s/Any s/Any})
   :id s/Str
   (s/optional-key :http-params) {s/Any s/Any}})

(def CreateEventType "CreatedModel")

(s/defschema CreateEvent
  (st/merge
   ModelEventBase
   {:type (s/eq CreateEventType)
    :model {s/Any s/Any}}))

(def UpdateEventType "UpdatedModel")

(s/defschema UpdateTriple
  [(s/one s/Keyword "field")
   (s/one s/Str "action")
   (s/one {s/Str s/Str} "metadata")])

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
   {:type (s/eq VerdictChangeEvent)
    :judgement_id s/Str
    :verdict v/Verdict}))

(s/defschema Event
  (s/conditional
   #(= CreateEventType        (:type %)) CreateEvent
   #(= UpdateEventType        (:type %)) UpdateEvent
   #(= DeleteEventType        (:type %)) DeleteEvent
   #(= VerdictChangeEventType (:type %)) DeleteEvent))
