(ns ctia.schemas.event
  (:require [ctia.schemas.core :refer [stored-schema-lookup]]
            [ctim.events.schemas :refer :all]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema EventLikeMap
  {:type s/Str
   :entity {:type s/Str
            s/Keyword s/Any}
   s/Keyword s/Any})

(s/defn schema-for-event
  "Returns an event schema, based on an instance of an event.  The
  resulting schema will use the specific model schema (not the
  open-ended ones that are used in the CTIM event schemas).  The model
  specific event schema is useful, for example, when coercing an event
  map to use the correct field types."
  [{event-type :type
    {entity-type :type} :entity} :- EventLikeMap]
  (let [entity-schema (get stored-schema-lookup (keyword entity-type))]
    (condp = event-type
      CreateEventType (st/assoc CreateEvent :entity entity-schema)
      UpdateEventType (st/assoc UpdateEvent :entity entity-schema)
      DeleteEventType (st/assoc DeleteEvent :entity entity-schema)
      Event)))
