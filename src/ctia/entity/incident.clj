(ns ctia.entity.incident
  (:require
   [clojure.string :as str]
   [java-time.api :as jt]
   [clj-momo.lib.clj-time.core :as time]
   [ctia.domain.entities
    :refer [default-realize-fn un-store with-long-id]]
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship-graphql]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :as routes.crud]
   [ctia.lib.compojure.api.core :refer [POST routes]]
   [ctia.schemas.core :refer [APIHandlerServices def-acl-schema def-stored-schema SortExtensionDefinitions]]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.sorting :as graphql-sorting]
   [ctia.schemas.sorting :refer [default-entity-sort-fields describable-entity-sort-fields sourcable-entity-sort-fields]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.schemas.incident :as is]
   [ctim.schemas.vocabularies :as vocs]
   [flanders.schema :as fs]
   [flanders.utils :as fu]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response :refer [not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def incident-bundle-default-limit 1000)

(def-acl-schema Incident
  is/Incident
  "incident")

(def-acl-schema PartialIncident
  (fu/optionalize-all is/Incident)
  "partial-incident")

(s/defschema PartialIncidentList
  [PartialIncident])

(def-acl-schema NewIncident
  is/NewIncident
  "new-incident")

(def incident-intervals
  [:new_to_opened
   :opened_to_closed])

(def-stored-schema StoredIncident
  (st/assoc Incident
            (s/optional-key :intervals) (st/optional-keys
                                          (zipmap incident-intervals (repeat (s/pred nat-int?))))))

(s/defschema PartialNewIncident
  (st/optional-keys-schema NewIncident))

(s/defschema PartialStoredIncident
  (st/optional-keys-schema StoredIncident))

(def realize-incident
  (default-realize-fn "incident" NewIncident StoredIncident))

(s/defschema IncidentStatus
  (fs/->schema vocs/Status))

(s/defschema IncidentStatusUpdate
  {:status IncidentStatus})

(defn make-status-update
  [{:keys [status]}]
  (let [t (time/internal-now)
        verb (case status
               "New" nil
               "Stalled" nil
               ;; Note: GitHub syntax highlighting doesn't like lists with strings
               "Containment Achieved" :remediated
               "Restoration Achieved" :remediated
               "Open" :opened
               "Rejected" :rejected
               "Closed" :closed
               "Incident Reported" :reported
               nil)]
    (cond-> {:status status}
      verb (assoc :incident_time {verb t}))))

(s/defschema IncidentUpdateBeforeComputeIntervals
  (s/conditional
    #(= "Open" (:status %)) (-> PartialIncident
                                (st/required-keys [:id])
                                (st/update :incident_time st/required-keys [:opened]))
    #(= "Closed" (:status %)) (-> PartialIncident
                                  (st/required-keys [:id])
                                  (st/update :incident_time st/required-keys [:closed]))
    :else (st/required-keys PartialIncident [:id :status])))

(s/defn compute-intervals :- PartialStoredIncident
  "Given an incident update and the a function returning the current stored incident, return a new update
  that also computes any relevant intervals that are missing from the stored incident.
  Avoids retrieving the stored incident if possible."
  [{:keys [id status] :as incident-update} :- IncidentUpdateBeforeComputeIntervals
   ->stored-incident :- (s/pred delay?) #_(s/delay StoredIncident)]
  (if-not (#{"Open" "Closed"} status) ;; only deref stored incident if needed
    incident-update
    (let [stored-incident @->stored-incident
          update-interval (s/fn [interval :- (apply s/enum incident-intervals)
                                 earlier :- s/Inst
                                 later :- s/Inst]
                            (cond-> incident-update
                              (and (not (get (:intervals stored-incident) interval)) ;; don't clobber existing interval
                                   (jt/not-after? (jt/instant earlier) (jt/instant later)))
                              (assoc-in [:intervals interval]
                                        (jt/time-between (jt/instant earlier) (jt/instant later) :seconds))))]
      (case status
        ;; the duration between the time at which the incident changed from New to Open and the incident creation time
        ;; https://github.com/advthreat/iroh/issues/7622#issuecomment-1496374419
        "Open"   (update-interval :new_to_opened
                                  (:created stored-incident)
                                  (get-in incident-update [:incident_time :opened]))
        "Closed" (update-interval :opened_to_closed
                                  (get-in stored-incident [:incident_time :opened])
                                  (get-in incident-update [:incident_time :closed]))))))

(s/defn incident-additional-routes [{{:keys [get-store]} :StoreService
                                     :as services} :- APIHandlerServices]
  (routes
    (let [capabilities :create-incident]
      (POST "/:id/status" []
            :return Incident
            :body [status-update IncidentStatusUpdate
                   {:description "an Incident Status Update"}]
            :summary "Update an Incident Status"
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :description (routes.common/capabilities->description capabilities)
            :capabilities :create-incident
            :auth-identity identity
            :identity-map identity-map
            (let [get-by-ids-fn (routes.crud/flow-get-by-ids-fn
                                  {:get-store get-store
                                   :entity :incident
                                   :identity-map identity-map})
                  status-update (-> (make-status-update status-update)
                                    (assoc :id id)
                                    (compute-intervals (delay (first (get-by-ids-fn [id])))))
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
                        first
                        un-store)]
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
     {:confidence       em/token
      :status           em/token
      :incident_time    em/incident-time
      :categories       em/token
      :discovery_method em/token
      :intended_effect  em/token
      :assignees        em/token
      :promotion_method em/token
      :severity         em/token
      :tactics          em/token
      :techniques       em/token
      :scores           {:type "object"
                         :dynamic true}
      :intervals        {:type "object"
                         :dynamic true}})}})

(def-es-store IncidentStore :incident StoredIncident PartialStoredIncident)

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
           :discovery_method
           :intended_effect
           :assignees
           :promotion_method
           :severity
           :tactics
           :techniques]))

(comment
  (defn generate-mitre-tactic-scores
    "script for stripping proprietary info from mitre tactic scores.
    Use to generate new :remappings for :tactics sorting."
    [csv-file]
    (let [s (slurp csv-file)
          ;; lifecycle order
          relevant-tactics ["TA0043"
                            "TA0042"
                            "TA0001"
                            "TA0002"
                            "TA0003"
                            "TA0004"
                            "TA0005"
                            "TA0006"
                            "TA0007"
                            "TA0008"
                            "TA0009"
                            "TA0011"
                            "TA0010"
                            "TA0040"]
          tactic->pos (into {} (map-indexed (fn [i id] [id i]))
                            relevant-tactics)
          groups (-> s 
                     ((requiring-resolve 'cheshire.core/parse-string))
                     ((requiring-resolve 'clojure.walk/keywordize-keys))
                     (->> (filter (comp (set relevant-tactics) :id))
                          (group-by :risk_score)
                          (sort-by key)
                          (map second)))
          out (into (sorted-map-by (fn [id1 id2]
                                     (< (tactic->pos id1 0) (tactic->pos id2 0))))
                    (map (fn [score group]
                           (zipmap (map :id group) (repeat score)))
                         (next (range)) groups))

          _ (assert (= relevant-tactics (keys out))
                    "missing score/s")]
     ((requiring-resolve 'clojure.pprint/pprint) out)))
  (generate-mitre-tactic-scores ""))

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
   :incident_time.rejected])

(def incident-average-fields
  (mapv #(keyword (str "intervals." (name %)))
        incident-intervals))

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
    {:confidence       s/Str
     :status           s/Str
     :discovery_method s/Str
     :intended_effect  s/Str
     :categories       s/Str
     :sort_by          (incident-sort-fields services)
     :assignees        s/Str
     :promotion_method s/Str
     :severity         s/Str
     :tactics          [s/Str]
     :techniques       [s/Str]})))

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
         (fu/optionalize-all is/Incident)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship-graphql/relatable-entity-fields
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
