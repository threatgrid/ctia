(ns ctia.http.routes.incident
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.store :refer :all]
            [ctia.schemas.incident :refer [NewIncident
                                           StoredIncident
                                           realize-incident]]))

(defroutes incident-routes

  (context "/incident" []
    :tags ["Incident"]
    (POST "/" []
      :return StoredIncident
      :body [incident NewIncident {:description "a new incident"}]
      :summary "Adds a new Incident"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-incident :admin}
      :login login
      (ok (flows/create-flow :model StoredIncident
                             :realize-fn realize-incident
                             :store-fn #(create-incident @incident-store %)
                             :object-type :incident
                             :login login
                             :object incident)))
    (PUT "/:id" []
      :return StoredIncident
      :body [incident NewIncident {:description "an updated incident"}]
      :summary "Updates an Incident"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-incident :admin}
      :login login
      (ok (flows/update-flow :model StoredIncident
                             :get-fn #(read-incident @incident-store %)
                             :realize-fn realize-incident
                             :update-fn #(update-incident @incident-store (:id %) %)
                             :object-type :incident
                             :id id
                             :login login
                             :object incident)))
    (GET "/:id" []
      :return (s/maybe StoredIncident)
      :summary "Gets an Incident by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-incident
      (if-let [d (read-incident @incident-store id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes an Incident"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:delete-incident :admin}
      (if (flows/delete-flow :model StoredIncident
                             :get-fn #(read-incident @incident-store %)
                             :delete-fn #(delete-incident @incident-store %)
                             :object-type :incident
                             :id id)
        (no-content)
        (not-found)))))
