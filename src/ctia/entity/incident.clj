(ns ctia.entity.incident
  (:require [clj-momo.lib.clj-time.core :as time]
            [ctia.domain.entities :refer [default-realize-fn un-store with-long-id]]
            [ctia.entity.incident.es-store :as i-store]
            [ctia.entity.incident.schemas :as is]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common :as routes.common]
            [ctia.http.routes.crud :refer [services->entity-crud-routes]]
            [ctia.lib.compojure.api.core :refer [POST routes]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.store :refer [read-record update-record]]
            [ring.swagger.schema :refer [describe]]
            [ring.util.http-response :refer [not-found ok]]
            [schema.core :as s]))

(def incident-bundle-default-limit 1000)

(def realize-incident
  (default-realize-fn "incident" is/NewIncident is/StoredIncident))

(defn make-status-update
  [{:keys [status]}]
  (let [t (time/internal-now)
        verb (case status
               "New" nil
               "Stalled" nil
               ("Containment Achieved"
                "Restoration Achieved") :remediated
               "Open" :opened
               "Rejected" :rejected
               "Closed" :closed
               "Incident Reported" :reported
               nil)]
    (cond-> {:status status}
      verb (assoc :incident_time {verb t}))))

(s/defn incident-additional-routes [{{:keys [get-store]} :StoreService
                                     :as services} :- APIHandlerServices]
  (routes
    (let [capabilities :create-incident]
      (POST "/:id/status" []
            :return is/Incident
            :body [update is/IncidentStatusUpdate
                   {:description "an Incident Status Update"}]
            :summary "Update an Incident Status"
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :description (routes.common/capabilities->description capabilities)
            :capabilities :create-incident
            :auth-identity identity
            :identity-map identity-map
            (let [status-update (make-status-update update)]
              (if-let [updated
                       (un-store
                        (flows/patch-flow
                         :services services
                         :get-fn #(-> (get-store :incident)
                                      (read-record
                                        %
                                        identity-map
                                        {}))
                         :realize-fn realize-incident
                         :update-fn #(-> (get-store :incident)
                                         (update-record
                                           (:id %)
                                           %
                                           identity-map
                                           (routes.common/wait_for->refresh wait_for)))
                         :long-id-fn #(with-long-id % services)
                         :entity-type :incident
                         :entity-id id
                         :identity identity
                         :patch-operation :replace
                         :partial-entity status-update
                         :spec :new-incident/map))]
                (ok updated)
                (not-found)))))))

(s/defn incident-routes [services :- APIHandlerServices]
  (routes
   (incident-additional-routes services)
   (services->entity-crud-routes
    services
    {:entity                   :incident
     :new-schema               is/NewIncident
     :entity-schema            is/Incident
     :get-schema               is/PartialIncident
     :get-params               is/IncidentGetParams
     :list-schema              is/PartialIncidentList
     :search-schema            is/PartialIncidentList
     :patch-schema             is/PartialNewIncident
     :external-id-q-params     is/IncidentByExternalIdQueryParams
     :search-q-params          is/IncidentSearchParams
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
     :histogram-fields         is/incident-histogram-fields
     :enumerable-fields        is/incident-enumerable-fields})))

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
   :schema                is/Incident
   :partial-schema        is/PartialIncident
   :partial-list-schema   is/PartialIncidentList
   :new-schema            is/NewIncident
   :stored-schema         is/StoredIncident
   :partial-stored-schema is/PartialStoredIncident
   :realize-fn            realize-incident
   :es-store              i-store/->IncidentStore
   :es-mapping            i-store/incident-mapping
   :services->routes      (routes.common/reloadable-function incident-routes)
   :capabilities          capabilities
   :fields                is/incident-fields
   :sort-fields           is/incident-fields})
