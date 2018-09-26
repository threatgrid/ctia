(ns ctia.entity.event.schemas
  (:require [ctim.schemas.verdict :as v]
            [flanders.schema :as fs]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def CreateEventType :record-created)
(def UpdateEventType :record-updated)
(def DeleteEventType :record-deleted)

(def event-types
  [CreateEventType
   UpdateEventType
   DeleteEventType])

(s/defschema Update
  {:field s/Keyword
   :action s/Str
   :change {:before s/Any
            :after s/Any}})

(s/defschema Event
  (st/merge
   {:owner s/Str
    :groups [s/Str]
    :timestamp s/Inst
    :entity {s/Any s/Any}
    :id s/Str
    :type (s/enum "event")
    :event_type (apply s/enum event-types)}
   (st/optional-keys
    {:fields [Update]})))

(s/defschema PartialEvent
  (st/optional-keys Event))

(s/defschema PartialEventList
  [Event])
