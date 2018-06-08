(ns ctia.link.routes
  (:require [compojure.api.sweet :refer [POST]]
            [ctia.flows.crud :as flows]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :refer [short-id->long-id long-id->id]]
            [ctia.domain.entities :refer [un-store with-long-id]]
            [ctia.entity.entities :refer [entities]]
            [ctia.schemas.core :refer [Reference TLP]]
            [ctia.store :refer :all]
            [ctia.http.routes.common :refer [created]]
            [ring.util.http-response :refer [not-found]]
            [schema.core :as s]))

(s/defschema IncidentCasebookLinkRequest
  {:casebook_id Reference
   (s/optional-key :tlp) TLP})

(def incident-casebook-link-route
  (POST "/:id/link" []
        :return (-> entities :relationship :schema)
        :body [link-req IncidentCasebookLinkRequest
               {:description "an Incident Link request"}]
        :header-params [{Authorization :- (s/maybe s/Str) nil}]
        :summary "Link an Incident to a Casebook"
        :path-params [id :- s/Str]
        :capabilities #{:create-incident
                        :create-relationship
                        :read-casebook}
        :auth-identity identity
        :identity-map identity-map
        (let [incident (read-store :incident
                                   read-record
                                   id
                                   identity-map
                                   {})
              casebook (read-store :casebook
                                   read-record
                                   (-> link-req
                                       :casebook_id
                                       long-id->id
                                       :short-id)
                                   identity-map
                                   {})]

          (cond
            (not incident)
            (not-found)
            (not casebook)
            (not-found)
            :else
            (let [{:keys [tlp casebook_id]
                   :or {tlp "amber"}} link-req
                  new-relationship
                  {:source_ref casebook_id
                   :target_ref (short-id->long-id id
                                                  get-http-show)
                   :relationship_type "related-to"
                   :tlp tlp}
                  stored-relationship
                  (-> (flows/create-flow
                       :entity-type :relationship
                       :realize-fn (-> entities :relationship :realize-fn)
                       :store-fn #(write-store :relationship
                                               create-record
                                               %
                                               identity-map
                                               {})
                       :long-id-fn with-long-id
                       :entity-type :relationship
                       :identity identity
                       :entities [new-relationship]
                       :spec (-> entities :relationship :new-spec))
                      first
                      un-store)]
              (created stored-relationship))))))
