(ns ctia.http.routes.coa
  (:require [compojure.api.sweet :refer :all]
            [ctia.domain.entities :refer [realize-coa]]
            [ctia.domain.entities.coa :refer [with-long-id page-with-long-id]]
            [ctia.flows.crud :as f]
            [ctia.store :refer :all]
            [ctia.http.routes.common :refer [created paginated-ok PagingParams]]
            [ctia.schemas.core :refer [NewCOA StoredCOA]]
            [ring.util.http-response :refer [ok no-content not-found]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema COAByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

(defroutes coa-routes
  (context "/coa" []
    :tags ["COA"]
    (POST "/" []
      :return StoredCOA
      :body [coa NewCOA {:description "a new COA"}]
      :summary "Adds a new COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-coa
      :identity identity
      (created
       (with-long-id
         (f/pop-result
          (f/create-flow :realize-fn realize-coa
                         :store-fn #(write-store :coa create-coa %)
                         :entity-type :coa
                         :identity identity
                         :entity coa)))))
    (PUT "/:id" []
      :return StoredCOA
      :body [coa NewCOA {:description "an updated COA"}]
      :summary "Updates a COA"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :create-coa
      :identity identity
      (ok
       (with-long-id
         (f/pop-result
          (f/update-flow :get-fn #(read-store :coa read-coa %)
                         :realize-fn realize-coa
                         :update-fn #(write-store :coa update-coa id %)
                         :entity-type :coa
                         :entity-id id
                         :identity identity
                         :entity coa)))))

    (GET "/external_id" []
      :return [(s/maybe StoredCOA)]
      :query [q COAByExternalIdQueryParams]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "List COAs by external id"
      :capabilities #{:read-coa :external-id}
      (paginated-ok
       (page-with-long-id
        (read-store :coa
                    list-coas
                    {:external_ids (:external_id q)}
                    q))))

    (GET "/:id" []
      :return (s/maybe StoredCOA)
      :summary "Gets a COA by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :read-coa
      (if-let [d (read-store :coa (fn [s] (read-coa s id)))]
        (ok (with-long-id d))
        (not-found)))

    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a COA"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities :delete-coa
      :identity identity
      (if (f/delete-flow :get-fn #(read-store :coa read-coa %)
                             :delete-fn #(write-store :coa delete-coa %)
                             :entity-type :coa
                             :entity-id id
                             :identity identity)
        (no-content)
        (not-found)))))
