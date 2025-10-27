(ns ctia.entity.incident
  (:require
   [clj-momo.lib.clj-time.core :as time]
   [clojure.string :as str]
   [ctia.domain.entities
    :refer [default-realize-fn with-long-id]]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship-graphql]
   [ctia.flows.crud :as flows]
   [ctia.flows.schemas :refer [with-error]]
   [ctia.graphql.delayed :as delayed]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :as routes.crud]
   [ctia.lib.compojure.api.core :refer [POST routes]]
   [ctia.schemas.core :refer [APIHandlerServices
                              GraphQLRuntimeContext
                              RealizeFnResult
                              SortExtensionDefinitions
                              CTIAEntity
                              def-stored-schema
                              lift-realize-fn-with-context]]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.common :as gc]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as graphql-sorting]
   [ctia.schemas.sorting :refer [default-entity-sort-fields describable-entity-sort-fields sourcable-entity-sort-fields]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.incident :as is]
   [ctim.schemas.vocabularies :as vocs]
   [flanders.schema :as f-schema]
   [flanders.spec :as f-spec]
   [flanders.utils :as fu]
   [java-time.api :as jt]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response :refer [not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))


(s/defschema Incident
  (st/merge
   (f-schema/->schema
    (fu/replace-either-with-any
     is/Incident))
   CTIAEntity))

(f-spec/->spec is/Incident "incident")

(s/defschema PartialIncident
  (st/merge CTIAEntity
            (f-schema/->schema
             (fu/optionalize-all
              (fu/replace-either-with-any
               is/Incident)))))

(s/defschema PartialIncidentList
  [PartialIncident])

(s/defschema NewIncident
  (st/merge
   (f-schema/->schema
    (fu/replace-either-with-any
     is/NewIncident))
   CTIAEntity))

(f-spec/->spec is/NewIncident "new-incident")

;;NOTE: changing this requires a ES mapping refresh
(def incident-intervals
  [:new_to_opened
   :opened_to_closed
   :new_to_contained])

(def-stored-schema StoredIncident Incident)

(def-stored-schema ESStoredIncident
  (st/assoc StoredIncident
            (s/optional-key :intervals) (st/optional-keys
                                          (zipmap incident-intervals (repeat (s/pred nat-int?))))))

(s/defschema PartialNewIncident
  (st/optional-keys-schema NewIncident))

(s/defschema PartialStoredIncident
  (st/optional-keys-schema StoredIncident))

(s/defschema ESPartialStoredIncident
  (st/optional-keys-schema ESStoredIncident))

(def incident-default-realize
  (default-realize-fn "incident" NewIncident StoredIncident))

(s/defschema IncidentStatus
  (f-schema/->schema vocs/Status))

(s/defschema IncidentStatusUpdate
  {:status IncidentStatus})

(defn new-status? [status]
   (some? (re-matches #"New(: .+)?" status)))

(defn hold-status? [status]
   (some? (re-matches #"Hold(: .+)?" status)))

(defn open-status? [status]
   (some? (re-matches #"Open(: .+)?" status)))

(defn contained-status? [status]
  (= status "Open: Contained"))

(defn closed-status? [status]
  (some? (re-matches #"Closed(: .+)?" status)))

(defn make-status-update
  [{:keys [status]}]
  (let [t (time/internal-now)
        verbs (case status
                "New" nil
                "Stalled" nil
                "Hold" nil
                ;; Note: GitHub syntax highlighting doesn't like lists with strings
                "Containment Achieved" [:remediated]
                "Restoration Achieved" [:remediated]
                "Rejected" [:rejected]
                "Incident Reported" [:reported]
                "Open: Contained" [:opened :contained]
                (cond
                  (open-status? status) [:opened]
                  (closed-status? status) [:closed]))]
    (cond-> {:status status}
      verbs (assoc :incident_time (zipmap verbs (repeat t))))))

(s/defn apply-status-update-logic
  "Applies status change logic when status changes are detected.
  If the status has changed, applies make-status-update to set appropriate incident_time fields."
  [new-obj :- {s/Keyword s/Any}
   prev-obj :- (s/maybe {s/Keyword s/Any})]
  (if (and prev-obj
           (:status new-obj)
           (not= (:status new-obj) (:status prev-obj)))
    ;; Status has changed, apply status update logic
    (let [status-update (make-status-update {:status (:status new-obj)})
          ;; Merge the incident_time updates from status change logic
          ;; Only update incident_time fields that aren't already explicitly set
          incident-time-updates (get status-update :incident_time {})
          current-incident-time (get new-obj :incident_time {})
          merged-incident-time (merge incident-time-updates current-incident-time)]
      (cond-> new-obj
        (seq merged-incident-time) (assoc :incident_time merged-incident-time)))
    ;; No status change or no previous object, return as-is
    new-obj))

(s/defn realize-incident
  :- (RealizeFnResult (with-error StoredIncident))
  [new-obj id tempids ident-map & [prev-obj]]
  (delayed/fn :- (with-error StoredIncident)
    [rt-ctx :- GraphQLRuntimeContext]
    (let [rfn (lift-realize-fn-with-context
               incident-default-realize rt-ctx)
          now (time/internal-now)
          ;; Apply status update logic before realization
          new-obj-with-status-logic (apply-status-update-logic new-obj prev-obj)]
      (-> (rfn new-obj-with-status-logic id tempids ident-map prev-obj)
          (assoc :timestamp
                 (or (:timestamp new-obj-with-status-logic)
                     (:timestamp prev-obj)
                     now))))))

(s/defn ^:private update-interval :- ESStoredIncident
  [{:keys [intervals] :as incident} :- ESStoredIncident
   interval :- (apply s/enum incident-intervals)
   earlier :- (s/maybe s/Inst)
   later :- (s/maybe s/Inst)]
  (cond-> incident
    (and (not (get intervals interval)) ;; don't clobber existing interval
         earlier later
         (jt/not-after? (jt/instant earlier) (jt/instant later)))
    (assoc-in [:intervals interval]
              (jt/time-between (jt/instant earlier) (jt/instant later) :seconds))))

(s/defn compute-intervals :- ESStoredIncident
  "Given the currently stored (raw) incident and the incident to update it to, return a new update
  that also computes any relevant intervals that are missing from the updated incident."
  [{old-status :status :as prev} :- ESStoredIncident
   {new-status :status :as incident} :- StoredIncident]
  (let [incident (into incident (select-keys prev [:intervals]))]
    ;; note: incident_time.opened is a required field, so its presence is meaningless.
    ;; note: intervals are independent. they can be triggered in any order and only one can be calculated per change.
    ;; e.g., :opened_to_closed does not backfill :new_to_opened, nor prevents :new_to_opened from being filled later.
    ;; note: each interval is calculated at most once per incident.
    (cond-> incident
      ;; the duration between the time at which the incident changed from New to Open and the incident creation time
      ;; https://github.com/advthreat/iroh/issues/7622#issuecomment-1496374419
      (and (new-status? old-status)
           (open-status? new-status))
      (update-interval :new_to_opened
                       (:created prev)
                       (get-in incident [:incident_time :opened]))

      (and (or (open-status? old-status) (hold-status? old-status))
           (closed-status? new-status))
      (update-interval :opened_to_closed
                       ;; we assume this was updated by the status route on Open. will be garbage if status was updated
                       ;; in any other way.
                       (get-in prev [:incident_time :opened])
                       (get-in incident [:incident_time :closed]))

      (and (or (open-status? old-status) (new-status? old-status))
           (contained-status? new-status))
      (update-interval :new_to_contained
                       (:created prev)
                       (get-in incident [:incident_time :contained])))))

(s/defn un-store-incident :- PartialStoredIncident
  [{:keys [doc]} :- {:doc ESPartialStoredIncident
                     s/Keyword s/Any}]
  (dissoc doc :intervals))

(s/defn incident-additional-routes [{{:keys [get-store]} :StoreService
                                     :as services} :- APIHandlerServices]
  (routes
    (let [capabilities :create-incident]
      (POST "/:id/status" []
            :responses {200 {:schema Incident}}
            :body [update (describe IncidentStatusUpdate
                                    "an Incident Status Update")]
            :summary "Update an Incident Status"
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :description (routes.common/capabilities->description capabilities)
            :capabilities :create-incident
            :auth-identity identity
            :identity-map identity-map
            (let [status-update (assoc (make-status-update update) :id id)
                  get-by-ids-fn (routes.crud/flow-get-by-ids-fn
                                 {:get-store get-store
                                  :entity :incident
                                  :identity-map identity-map})
                  update-fn (routes.crud/flow-update-fn
                             {:get-store get-store
                              :entity :incident
                              :identity-map identity-map
                              :wait_for (routes.common/wait_for->refresh wait_for)})]
              (if-let [updated
                       (some->
                        (flows/patch-flow
                         :services services
                         :get-fn get-by-ids-fn
                         :realize-fn realize-incident
                         :update-fn update-fn
                         :long-id-fn #(with-long-id % services)
                         :entity-type :incident
                         :identity identity
                         :patch-operation :replace
                         :partial-entities [status-update]
                         :spec :new-incident/map)
                        first)]
                (ok updated)
                (not-found)))))))

(def incident-mapping
  {"incident"
   {:dynamic false
    :date_detection false
    :numeric_detection true
    :dynamic_templates
    [{;; Incident schema will prevent other types (like text, array, object)
      :num
      {:match_mapping_type "long"
       :mapping em/float-type}}]
    :properties
    (merge
     em/base-entity-mapping
     em/describable-entity-mapping
     em/sourcable-entity-mapping
     em/stored-entity-mapping
     {:confidence         em/token
      :status             em/token
      :incident_time      em/incident-time
      :categories         em/token
      :discovery_method   em/token
      :intended_effect    em/token
      :assignees          em/token
      :detection_sources  em/token
      :promotion_method   em/token
      :severity           em/token
      :tactics            em/token
      :techniques         em/token
      :scores             {:type "object"
                           :dynamic true}
      :intervals          {:properties (zipmap incident-intervals (repeat em/long-type))}})}})

(def store-opts
  {:stored->es-stored (s/fn [{:keys [doc op prev]}]
                        (cond->> doc
                          prev (compute-intervals prev)))
   :es-stored->stored un-store-incident
   :es-partial-stored->partial-stored un-store-incident
   :es-stored-schema ESStoredIncident
   :es-partial-stored-schema ESPartialStoredIncident})

(def-es-store IncidentStore :incident StoredIncident PartialStoredIncident
  :store-opts store-opts)

(def incident-fields
  (concat default-entity-sort-fields
          describable-entity-sort-fields
          sourcable-entity-sort-fields
          [:confidence
           :status
           :incident_time.opened
           :incident_time.discovered
           :incident_time.reported
           :incident_time.remediated
           :incident_time.closed
           :incident_time.rejected
           :incident_time.contained
           :discovery_method
           :intended_effect
           :assignees
           :detection_sources
           :promotion_method
           :severity
           :tactics
           :techniques]))

(defn score-types
  [get-in-config]
  (some-> (get-in-config [:ctia :http :incident :score-types])
          (str/split #",")))

(defn mk-scores-schema
  [{{:keys [get-in-config]} :ConfigService}]
  (st/optional-keys
   (into {}
         (map (fn [score-type] {(keyword score-type) s/Num}))
         (score-types get-in-config))))

(s/defn sort-extension-definitions :- SortExtensionDefinitions
  [services :- APIHandlerServices]
  (-> {;; override :severity field to sort semantically
       :severity {:op :remap
                  :remappings {"Low" 1
                               "Medium" 2
                               "High" 3
                               "Critical" 4}
                  :remap-default 0}
       ;; override :tactics field to sort by the highest risk score for
       ;; any one tactic on an incident
       ;; https://attack.mitre.org/versions/v11/tactics/enterprise/
       :tactics {:op :remap-list-max
                 :remappings
                 ;; Note: don't use actual scores, they may be proprietary. instead,
                 ;; simulate the same ordering (not proprietary) with dummy scores.
                 ;; generate with `generate-mitre-tactic-scores`
                 {"TA0043" 2,
                  "TA0042" 1,
                  "TA0001" 3,
                  "TA0002" 11,
                  "TA0003" 9,
                  "TA0004" 7,
                  "TA0005" 11,
                  "TA0006" 10,
                  "TA0007" 9,
                  "TA0008" 5,
                  "TA0009" 8,
                  "TA0011" 8,
                  "TA0010" 6,
                  "TA0040" 4}
                 :remap-default 0}}
      ;; Sort by score
      (into (map (fn [score-type]
                   {(keyword (str "scores." score-type)) {:op :field}}))
            (score-types services))))

(s/defn incident-sort-fields
  [services :- APIHandlerServices]
  (apply s/enum
         (map name
              (distinct
               (concat (keys (sort-extension-definitions services))
                       incident-fields)))))

(def incident-enumerable-fields
  [:assignees
   :detection_sources
   :categories
   :confidence
   :discovery_method
   :intended_effect
   :promotion_method
   :source
   :status
   :title
   :severity
   :tactics
   :techniques])

(def incident-histogram-fields
  [:timestamp
   :incident_time.opened
   :incident_time.discovered
   :incident_time.reported
   :incident_time.remediated
   :incident_time.closed
   :incident_time.rejected
   :incident_time.contained])

(def incident-average-fields
  {;; restrict to entities _created_ within from/to interval.
   :intervals.new_to_opened {:date-field :created}
   :intervals.opened_to_closed {:date-field :created}
   :intervals.new_to_contained {:date-field :created}})

(s/defschema IncidentFieldsParam
  {(s/optional-key :fields) [(apply s/enum incident-fields)]})

(s/defn IncidentSearchParams :- (s/protocol s/Schema)
  [services :- APIHandlerServices]
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   IncidentFieldsParam
   (st/optional-keys
    {:confidence         s/Str
     :status             s/Str
     :discovery_method   s/Str
     :intended_effect    s/Str
     :categories         s/Str
     :sort_by            (incident-sort-fields services)
     :assignees          s/Str
     :detection_sources  s/Str
     :promotion_method   s/Str
     :severity           s/Str
     :tactics            [s/Str]
     :techniques         [s/Str]})))

(def IncidentGetParams IncidentFieldsParam)

(s/defschema IncidentByExternalIdQueryParams
  (st/merge
   routes.common/PagingParams
   IncidentFieldsParam))

(def searchable-fields
  #{:description
    :source
    :id
    :short_description
    :title})

(defn with-config-scores
  [schema services]
  (assoc schema
         (s/optional-key :scores)
         (mk-scores-schema services)))

(s/defn incident-routes [services :- APIHandlerServices]
  (routes
   (incident-additional-routes services)
   (routes.crud/services->entity-crud-routes
    services
    {:entity                   :incident
     :new-schema               (with-config-scores NewIncident services)
     :entity-schema            Incident
     :get-schema               PartialIncident
     :get-params               IncidentGetParams
     :list-schema              PartialIncidentList
     :search-schema            PartialIncidentList
     :patch-schema             (with-config-scores PartialNewIncident services)
     :external-id-q-params     IncidentByExternalIdQueryParams
     :search-q-params          (IncidentSearchParams services)
     :new-spec                 :new-incident/map
     :can-patch?               true
     :can-aggregate?           true
     :realize-fn               realize-incident
     :get-capabilities         :read-incident
     :post-capabilities        :create-incident
     :put-capabilities         :create-incident
     :patch-capabilities       :create-incident
     :delete-capabilities      :delete-incident
     :search-capabilities      :search-incident
     :external-id-capabilities :read-incident
     :histogram-fields         incident-histogram-fields
     :average-fields           incident-average-fields
     :enumerable-fields        incident-enumerable-fields
     :sort-extension-definitions (sort-extension-definitions services)})))

(def IncidentType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all (fu/replace-either-with-any is/Incident))
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship-graphql/relatable-entity-fields
            gc/time-metadata-fields
            go/graphql-ownership-fields))))

(def incident-order-arg
  (graphql-sorting/order-by-arg
   "IncidentOrder"
   "incidents"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              incident-fields))))

(def IncidentConnectionType
  (pagination/new-connection IncidentType))

(def capabilities
  #{:create-incident
    :read-incident
    :delete-incident
    :search-incident})

(def incident-entity
  {:route-context         "/incident"
   :tags                  ["Incident"]
   :entity                :incident
   :plural                :incidents
   :new-spec              :new-incident/map
   :schema                Incident
   :partial-schema        (fn [services] (with-config-scores PartialIncident services))
   :partial-list-schema   PartialIncidentList
   :new-schema            (fn [services] (with-config-scores NewIncident services))
   :stored-schema         StoredIncident
   :partial-stored-schema PartialStoredIncident
   :realize-fn            realize-incident
   :es-store              ->IncidentStore
   :es-mapping            incident-mapping
   :services->routes      (routes.common/reloadable-function incident-routes)
   :capabilities          capabilities
   :can-patch?            true
   :patch-capabilities    :create-incident
   :fields                incident-fields
   :sort-fields           incident-fields
   :searchable-fields     searchable-fields})
