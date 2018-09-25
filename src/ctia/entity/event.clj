(ns ctia.entity.event
  (:require
   [ctia.entity.event.store
    :refer [->EventStore]]
   [clj-momo.lib.es
    [document :as d]
    [schemas :refer [ESConnState SliceProperties]]
    [slice :refer [get-slice-props]]]
   [ctia.entity.event.schemas
    :refer [Event PartialEvent PartialEventList]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.lib.pagination :refer [list-response-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es
    [crud :as crud]
    [mapping :as em]]
   [ctim.events.schemas :as event-schemas]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def event-mapping
  {"event"
   {:dynamic false
    :properties
    {:owner em/token
     :groups em/token
     :timestamp em/ts
     :entity {:enabled false
              :type "object"}
     :id em/token
     :http-params {:enabled false
                   :type "object"}
     :type em/token
     :fields {:enabled false
              :type "object"}
     :judgement_id em/token}}})

(def event-fields
  [:owner
   :groups
   :timestamp
   :id
   :entity.id
   :entity.type
   :event_type])

(def event-sort-fields
  (apply s/enum event-fields))

(s/defschema EventFieldsParam
  {(s/optional-key :fields) [event-sort-fields]})

(s/defschema EventSearchParams
  (st/merge
   {:query s/Str}
   PagingParams
   BaseEntityFilterParams
   EventFieldsParam))

(def EventGetParams EventFieldsParam)

(def event-routes
  (entity-crud-routes
   {:tags ["Event"]
    :entity :event
    :entity-schema Event
    :get-schema PartialEvent
    :get-params EventGetParams
    :list-schema PartialEventList
    :search-schema PartialEventList
    :search-q-params EventSearchParams
    :get-capabilities :read-event
    :can-update? false
    :can-patch? false
    :can-post? false
    :search-capabilities :search-event}))

(def event-entity
  {:route-context "/event"
   :tags ["Event"]
   :schema Event
   :stored-schema Event
   :partial-schema PartialEvent
   :partial-stored-schema PartialEvent
   :partial-list-schema PartialEventList
   :no-bulk? true
   :entity :event
   :plural :events
   :es-store ->EventStore
   :es-mapping event-mapping
   :routes event-routes})
