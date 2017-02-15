(ns ctia.http.routes.ttp
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.ttp :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewTTP TTP]]
   [ctia.http.routes.common
    :refer [created paginated-ok PagingParams TTPSearchParams]]
   [ring.util.http-response :refer [ok no-content not-found]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(s/defschema TTPByExternalIdQueryParams
  PagingParams)

(defroutes ttp-routes
  (context "/ttp" []
           :tags ["TTP"]
           (POST "/" []
                 :return TTP
                 :body [ttp NewTTP {:description "a new TTP"}]
                 :summary "Adds a new TTP"
                 :header-params [api_key :- (s/maybe s/Str)]
                 :capabilities :create-ttp
                 :identity identity
                 (-> (flows/create-flow :realize-fn ent/realize-ttp
                                        :store-fn #(write-store :ttp create-ttps %)
                                        :long-id-fn with-long-id
                                        :entity-type :ttp
                                        :identity identity
                                        :entities [ttp])
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return TTP
                :body [ttp NewTTP {:description "an updated TTP"}]
                :summary "Updates a TTP"
                :path-params [id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :create-ttp
                :identity identity
                (-> (flows/update-flow
                     :get-fn #(read-store :ttp (fn [s] (read-ttp s %)))
                     :realize-fn ent/realize-ttp
                     :update-fn #(write-store :ttp update-ttp (:id %) %)
                     :long-id-fn with-long-id
                     :entity-type :ttp
                     :entity-id id
                     :identity identity
                     :entity ttp)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [TTP])
                :query [q TTPByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [api_key :- (s/maybe s/Str)]
                :summary "List TTPs by external id"
                :capabilities #{:read-ttp :external-id}
                (-> (read-store :ttp list-ttps
                                {:external_ids external_id} q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [TTP])
                :summary "Search for a TTP using a Lucene/ES query string"
                :query [params TTPSearchParams]
                :capabilities #{:read-ttp :search-ttp}
                :header-params [api_key :- (s/maybe s/Str)]
                (-> (query-string-search-store
                     :ttp
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe TTP)
                :summary "Gets a TTP by ID"
                :header-params [api_key :- (s/maybe s/Str)]
                :capabilities :read-ttp
                :path-params [id :- s/Str]
                (if-let [ttp (read-store :ttp
                                         (fn [s] (read-ttp s id)))]
                  (-> ttp
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a TTP"
                   :header-params [api_key :- (s/maybe s/Str)]
                   :capabilities :delete-ttp
                   :identity identity
                   (if (flows/delete-flow
                        :get-fn #(read-store :ttp read-ttp %)
                        :delete-fn #(write-store :ttp delete-ttp %)
                        :entity-type :ttp
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
