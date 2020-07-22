(ns ctia.entity.event
  (:require
   [ctia.entity.event.store
    :refer [->EventStore]]
   [compojure.api.sweet :refer [GET routes]]
   [ring.util.http-response :refer [ok]]
   [schema-tools.core :as st]
   [schema.core :as s]
   [clj-momo.lib.clj-time.core :as t]
   [clj-momo.lib.es
    [schemas :refer [ESConnState]]
    [slice :refer [get-slice-props]]]
   [ctia.entity.event.schemas
    :refer [Event PartialEvent PartialEventList EventBucket]]
   [ctia.http.routes
    [common :refer [BaseEntityFilterParams PagingParams]]
    [crud :refer [entity-crud-routes]]]
   [ctia.lib.pagination :refer [list-response-schema]]
   [ctia.schemas.sorting :as sorting]
   [ctia.stores.es
    [crud :as crud]
    [mapping :as em]]
   [ctia.store :refer [read-store list-events list-all-pages]]
   [ctia.domain.entities :as ent]
   [ctia.properties :refer [get-global-properties]]
   [clojure.set :as set]))

(def event-mapping
  {"event"
   {:dynamic false
    :properties
    {:owner em/token
     :groups em/token
     :tlp em/token
     :timestamp em/ts
     :entity {:type "object"
              :properties {:id em/token
                           :source_ref em/token
                           :target_ref em/token}}
     :id em/token
     :event_type em/token
     :http-params {:enabled false
                   :type "object"}
     :type em/token
     :fields {:enabled false
              :type "object"}}}})

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
   {(s/optional-key :query) s/Str}
   PagingParams
   BaseEntityFilterParams
   EventFieldsParam))

(def EventGetParams EventFieldsParam)

(def EventTimelineParams PagingParams)

(s/defn same-bucket? :- s/Bool
  [bucket :- EventBucket
   event :- Event]
  (let [max-seconds (get-in @(get-global-properties) [:ctia :http :events :timeline :max-seconds] 5)
        from        (t/minus (:from bucket) (t/seconds max-seconds))
        to          (t/plus (:to bucket) (t/seconds max-seconds))]
    (and (= (:owner bucket) (:owner event))
         (t/before? from (:timestamp event))
         (t/before? (:timestamp event) to))))

(s/defn init-bucket :- EventBucket
  [event :- Event]
  {:count 1
   :owner (:owner event)
   :from (:timestamp event)
   :to (:timestamp event)
   :events (list event)})

(s/defn bucket-append :- EventBucket
  [bucket :- EventBucket
   event :- Event]
  (-> (update bucket :count inc)
      (update :from t/earliest (:timestamp event))
      (update :to t/latest (:timestamp event))
      (update :events conj event)))

(s/defn timeline-append :- [EventBucket]
  [timeline :- [EventBucket]
   event :- Event]
  (let [[previous & remaining] timeline]
    (if (and (map? previous)
             (same-bucket? previous event))
        (cons (bucket-append previous event)
              remaining)
        (cons (init-bucket event) timeline))))

(s/defn bucketize-events :- [EventBucket]
  [events :- [Event]]
  (let [events (sort-by (juxt :owner :timestamp :event_type) events)
        buckets (reduce timeline-append [] events)]
    (reverse (sort-by :from buckets))))

(defn fetch-related-events [_id identity-map q]
  (let [filters {:entity.id _id
                 :entity.source_ref _id
                 :entity.target_ref _id}]
    (some-> (list-all-pages :event
                            list-events
                            {:one-of filters}
                            identity-map
                            q)
            ent/un-store-all)))

(def event-history-routes
  (routes
   (GET "/history/:entity_id" []
        :return [EventBucket]
        :query [q EventTimelineParams]
        :path-params [entity_id :- s/Str]
        :summary "Timeline history of an entity"
        :capabilities :search-event
        :auth-identity identity
        :identity-map identity-map
        (let [res (fetch-related-events entity_id
                                        identity-map
                                        (into q {:sort_by :timestamp :sort_order :desc}))
              timeline (bucketize-events res)]
          (ok timeline)))))

(def event-routes
  (routes
   event-history-routes
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
     :can-get-by-external-id? false
     :search-capabilities :search-event
     :delete-capabilities #{:delete-event :developer}
     :date-field :timestamp})))

(def event-entity
  {:new-spec map?
   :route-context "/event"
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
