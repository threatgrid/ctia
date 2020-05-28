(ns ctia.entity.event.schemas
  (:require
   [ctia.schemas.core :refer [TLP]]
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
  ;; simplified schema for Swagger UI
  {:field s/Keyword
   :action (s/enum "modified" "added" "deleted")
   :change (st/optional-keys
             {:before s/Any
              :after s/Any})}
  ;; actual schema
  #_
  (s/conditional
    map?
    (s/conditional
      (comp #{"modified"} :action)
      {:field s/Keyword
       :action s/Str
       :change {:before s/Any
                :after s/Any}}

      (comp #{"added"} :action)
      {:field s/Keyword
       :action s/Str
       :change {:after s/Any}}

      (comp #{"deleted"} :action)
      {:field s/Keyword
       :action s/Str
       :change {:before s/Any}})))

(s/defschema Event
  (st/merge
   {:owner s/Str
    :groups [s/Str]
    :tlp TLP
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
  [PartialEvent])

(s/defschema EventBucket
  {:count s/Int
   :from s/Inst
   :to s/Inst
   :owner s/Str
   :events PartialEventList})
