(ns ctia.http.routes.tool
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.domain.entities :as ent]
   [ctia.domain.entities.tool :refer [with-long-id page-with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            PagingParams
            ToolSearchParams]]
   [ctia.store :refer :all]
   [ctia.schemas.core :refer [NewTool Tool]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema ToolByExternalIdQueryParams
  PagingParams)

(defroutes tool-routes
  (context "/tool" []
           :tags ["Tool"]
           (POST "/" []
                 :return Tool
                 :body [tool NewTool {:description "a new Tool"}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "Adds a new Tool"
                 :capabilities :create-tool
                 :identity identity
                 :identity-map identity-map
                 (-> (flows/create-flow
                      :entity-type :tool
                      :realize-fn ent/realize-tool
                      :store-fn #(write-store :tool
                                              create-tools
                                              %
                                              identity-map)
                      :long-id-fn with-long-id
                      :entity-type :tool
                      :identity identity
                      :entities [tool]
                      :spec :new-tool/map)
                     first
                     ent/un-store
                     created))

           (PUT "/:id" []
                :return Tool
                :body [tool NewTool {:description "an updated Tool"}]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "Updates a Tool"
                :path-params [id :- s/Str]
                :capabilities :create-tool
                :identity identity
                :identity-map identity-map
                (-> (flows/update-flow
                     :get-fn #(read-store :tool
                                          read-tool
                                          %
                                          identity-map)
                     :realize-fn ent/realize-tool
                     :update-fn #(write-store :tool
                                              update-tool
                                              (:id %)
                                              %
                                              identity-map)
                     :long-id-fn with-long-id
                     :entity-type :tool
                     :entity-id id
                     :identity identity
                     :entity tool
                     :spec :new-tool/map)
                    ent/un-store
                    ok))

           (GET "/external_id/:external_id" []
                :return (s/maybe [Tool])
                :query [q ToolByExternalIdQueryParams]
                :path-params [external_id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :summary "List tools by external id"
                :capabilities #{:read-tool :external-id}
                :identity identity
                :identity-map identity-map
                (-> (read-store :tool list-tools
                                {:external_ids external_id}
                                identity-map
                                q)
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/search" []
                :return (s/maybe [Tool])
                :summary "Search for a Tool using a Lucene/ES query string"
                :query [params ToolSearchParams]
                :capabilities #{:read-tool :search-tool}
                :identity identity
                :identity-map identity-map
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                (-> (query-string-search-store
                     :tool
                     query-string-search
                     (:query params)
                     (dissoc params :query :sort_by :sort_order :offset :limit)
                     identity-map
                     (select-keys params [:sort_by :sort_order :offset :limit]))
                    page-with-long-id
                    ent/un-store-page
                    paginated-ok))

           (GET "/:id" []
                :return (s/maybe Tool)
                :summary "Gets a Tool by ID"
                :path-params [id :- s/Str]
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :capabilities :read-tool
                :identity identity
                :identity-map identity-map
                (if-let [tool (read-store :tool
                                           read-tool
                                           id
                                           identity-map)]
                  (-> tool
                      with-long-id
                      ent/un-store
                      ok)
                  (not-found)))

           (DELETE "/:id" []
                   :no-doc true
                   :path-params [id :- s/Str]
                   :summary "Deletes a Tool"
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities :delete-tool
                   :identity identity
                   :identity-map identity-map
                   (if (flows/delete-flow
                        :get-fn #(read-store :tool
                                             read-tool
                                             %
                                             identity-map)
                        :delete-fn #(write-store :tool
                                                 delete-tool
                                                 %
                                                 identity-map)
                        :entity-type :tool
                        :entity-id id
                        :identity identity)
                     (no-content)
                     (not-found)))))
