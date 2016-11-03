(ns ctia.http.routes.ttp
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :refer [realize-ttp]]
   [ctia.domain.entities.ttp :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewTTP StoredTTP]]
   [ctia.http.routes.common :refer [created paginated-ok PagingParams TTPSearchParams]]
   [ring.util.http-response :refer [ok no-content not-found]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema TTPByExternalIdQueryParams
  (st/merge
   PagingParams
   {:external_id s/Str}))

(defroutes ttp-routes
  (context "/ttp" []
           :tags ["TTP"]
           (POST "/" []
                 :return StoredTTP
                 :body [ttp NewTTP {:description "a new TTP"}]
                 :summary "Adds a new TTP"
                 :header-params [api_key :- (s/maybe s/Str)]
                 :capabilities :create-ttp
                 :identity identity
                 (created
                  (with-long-id
                    (first (flows/create-flow :realize-fn realize-ttp
                                              :store-fn #(write-store :ttp create-ttps %)
                                              :entity-type :ttp
                                              :identity identity
                                              :entities [ttp])))))
           
           (PUT "/:id" []
                :return StoredTTP
                :body [ttp NewTTP {:description "an updated TTP"}]
                :summary "Updates a TTP"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :create-ttp
                :identity identity
                (ok
                 (with-long-id
                   (flows/update-flow :get-fn #(read-store :ttp
                                                           (fn [s] (read-ttp s %)))
                                      :realize-fn realize-ttp
                                      :update-fn #(write-store :ttp update-ttp (:id %) %)
                                      :entity-type :ttp
                                      :entity-id id
                                      :identity identity
                                      :entity ttp))))
           
           (GET "/external_id" []
                :return [(s/maybe StoredTTP)]
                :query [q TTPByExternalIdQueryParams]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List TTPs by external id"
                :capabilities #{:read-ttp :external-id}
                (paginated-ok
                 (page-with-long-id
                  (read-store :ttp list-ttps
                              {:external_ids (:external_id q)} q))))
           
           (GET "/search" []
                :return (s/maybe [StoredTTP])
                :summary "Search for a TTP using a Lucene/ES query string"
                :query [params TTPSearchParams]
                :capabilities #{:read-ttp :search-ttp}
                :header-params [api_key :- (s/maybe s/Str)]
                (paginated-ok
                 (page-with-long-id
                  (query-string-search-store :ttp
                                             query-string-search
                                             (:query params)
                                             (dissoc params :query :sort_by :sort_order :offset :limit)
                                             (select-keys params [:sort_by :sort_order :offset :limit])))))
           
           (GET "/:id" []
                :return (s/maybe StoredTTP)
                :summary "Gets a TTP by ID"
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-ttp
                :path-params [id :- s/Str]
                (if-let [d (read-store :ttp
                                       (fn [s] (read-ttp s id)))]
                  (ok (with-long-id d))
                  (not-found)))
           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a TTP"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-ttp
                   :identity identity
                   (if (flows/delete-flow :get-fn #(read-store :ttp read-ttp %)
                                          :delete-fn #(write-store :ttp delete-ttp %)
                                          :entity-type :ttp
                                          :entity-id id
                                          :identity identity)
                     (no-content)
                     (not-found)))))
