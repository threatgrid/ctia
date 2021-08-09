(ns ctia.entity.event
  (:require
   [clj-momo.lib.clj-time.core :as t]
   [ctia.domain.entities :as ent]
   [ctia.entity.event.schemas
    :refer
    [Event EventBucket PartialEvent PartialEventList]]
   [ctia.entity.event.store :refer [->EventStore]]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :refer [services->entity-crud-routes]]
   [ctia.lib.compojure.api.core :refer [GET routes]]
   [ctia.schemas.core :refer [APIHandlerServices]]
   [ctia.store :refer [list-all-pages list-events]]
   [ctia.stores.es.mapping :as em]
   [ring.util.http-response :refer [ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def event-mapping
  {"event"
   {:dynamic false
    :properties
    {:owner       em/token
     :groups      em/token
     :tlp         em/token
     :timestamp   em/ts
     :entity      {:type "object"
                   :properties {:id em/token
                                :source_ref em/token
                                :target_ref em/token}}
     :id          em/token
     :event_type  em/token
     :http-params {:enabled false
                   :type "object"}
     :type        em/token
     :fields      {:enabled false
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
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SearchableEntityParams
   EventFieldsParam))

(def EventGetParams EventFieldsParam)

(s/defn same-bucket? :- s/Bool
  [bucket :- EventBucket
   event :- Event
   get-in-config]
  (let [max-seconds (get-in-config [:ctia :http :events :timeline :max-seconds]
                                                5)
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
   event :- Event
   get-in-config]
  (let [[previous & remaining] timeline]
    (if (and (map? previous)
             (same-bucket? previous event get-in-config))
        (cons (bucket-append previous event)
              remaining)
        (cons (init-bucket event) timeline))))

(s/defn bucketize-events :- [EventBucket]
  [events :- [Event]
   get-in-config]
  (let [events (sort-by (juxt :owner :timestamp :event_type) events)
        buckets (reduce #(timeline-append %1 %2 get-in-config) [] events)]
    (reverse (sort-by :from buckets))))

(s/defn fetch-related-events [_id identity-map q services :- APIHandlerServices]
  (let [filters {:entity.id _id
                 :entity.source_ref _id
                 :entity.target_ref _id}]
    (some-> (list-all-pages :event
                            list-events
                            {:one-of filters}
                            identity-map
                            q
                            services)
            ent/un-store-all)))

(s/defn event-history-routes [{{:keys [get-in-config]} :ConfigService
                               :as services} :- APIHandlerServices]
  (routes
    (let [capabilities :search-event]
      (GET "/history/:entity_id" []
           :return [EventBucket]
           :path-params [entity_id :- s/Str]
           :summary "Timeline history of an entity"
           :description (routes.common/capabilities->description capabilities)
           :capabilities capabilities
           :auth-identity identity
           :identity-map identity-map
           (let [res (fetch-related-events entity_id
                                           identity-map
                                           {:sort_by :timestamp :sort_order :desc}
                                           services)
                 timeline (bucketize-events res get-in-config)]
             (ok timeline))))))

(s/defn event-routes [services :- APIHandlerServices]
  (routes
   (event-history-routes services)
   (services->entity-crud-routes
    services
    {:tags                    ["Event"]
     :entity                  :event
     :entity-schema           Event
     :get-schema              PartialEvent
     :get-params              EventGetParams
     :list-schema             PartialEventList
     :search-schema           PartialEventList
     :search-q-params         EventSearchParams
     :get-capabilities        :read-event
     :can-update?             false
     :can-patch?              false
     :can-post?               false
     :can-get-by-external-id? false
     :search-capabilities     :search-event
     :delete-capabilities     #{:delete-event :developer}
     :date-field              :timestamp
     :searchable-fields       (routes.common/searchable-fields
                               {:schema Event})})))

(def event-entity
  {:new-spec              map?
   :route-context         "/event"
   :tags                  ["Event"]
   :schema                Event
   :stored-schema         Event
   :partial-schema        PartialEvent
   :partial-stored-schema PartialEvent
   :partial-list-schema   PartialEventList
   :no-bulk?              true
   :entity                :event
   :plural                :events
   :es-store              ->EventStore
   :es-mapping            event-mapping
   :services->routes      (routes.common/reloadable-function
                           event-routes)})
