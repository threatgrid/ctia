(ns ctia.http.routes.crud
  (:refer-clojure :exclude [list read update])
  (:require
   [ctia.store :refer :all]
   [ctia.flows.crud :as flows]
   [ctia.domain.entities
    :refer [with-long-id
            un-store
            un-store-page
            page-with-long-id]]
   [ring.util.http-response :refer [ok no-content not-found]]
   [ctia.http.routes.common
    :refer [created
            paginated-ok
            search-options
            filter-map-search-options]]
   [schema.core :as s]
   [clojure.string :refer [capitalize]]
   [compojure.api.sweet :refer [routes
                                context
                                GET
                                POST
                                PUT
                                DELETE]]))

(defn entity-crud-routes
  [{:keys [entity
           new-schema
           entity-schema
           get-schema
           get-params
           list-schema
           search-schema
           external-id-q-params
           search-q-params
           new-spec
           realize-fn
           get-capabilities
           post-capabilities
           put-capabilities
           delete-capabilities
           search-capabilities
           external-id-capabilities
           hide-delete?]
    :or {hide-delete? true}}]

  (let [entity-str (name entity)
        capitalized (capitalize entity-str)]
    (routes
     (context (str "/" (name entity)) []
              :tags [capitalized]
              (POST "/" []
                    :return entity-schema
                    :body [new-entity new-schema {:description (format "a new %s" capitalized)}]
                    :header-params [{Authorization :- (s/maybe s/Str) nil}]
                    :summary (format "Adds a new %s" capitalized)
                    :capabilities post-capabilities
                    :identity identity
                    :identity-map identity-map
                    (-> (flows/create-flow
                         :entity-type entity
                         :realize-fn realize-fn
                         :store-fn #(write-store entity
                                                 create
                                                 %
                                                 identity-map
                                                 {})
                         :long-id-fn with-long-id
                         :entity-type entity
                         :identity identity
                         :entities [new-entity]
                         :spec new-spec)
                        first
                        un-store
                        created))

              (PUT "/:id" []
                   :return entity-schema
                   :body [entity-update new-schema {:description (format "an updated %s" capitalized)}]
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :summary (format "Updates an %s" capitalized)
                   :path-params [id :- s/Str]
                   :capabilities put-capabilities
                   :identity identity
                   :identity-map identity-map
                   (-> (flows/update-flow
                        :get-fn #(read-store entity
                                             read
                                             %
                                             identity-map
                                             {})
                        :realize-fn realize-fn
                        :update-fn #(write-store entity
                                                 update
                                                 (:id %)
                                                 %
                                                 identity-map)
                        :long-id-fn with-long-id
                        :entity-type entity
                        :entity-id id
                        :identity identity
                        :entity entity-update
                        :spec new-spec)
                       un-store
                       ok))

              (GET "/external_id/:external_id" []
                   :return list-schema
                   :query [q external-id-q-params]
                   :path-params [external_id :- s/Str]
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :summary (format "List %s by external id" capitalized)
                   :capabilities external-id-capabilities
                   :identity identity
                   :identity-map identity-map
                   (-> (read-store entity list
                                   {:external_ids external_id}
                                   identity-map
                                   q)
                       page-with-long-id
                       un-store-page
                       paginated-ok))

              (GET "/search" []
                   :return search-schema
                   :summary (format "Search for a %s using a Lucene/ES query string" capitalized)
                   :query [params search-q-params]
                   :capabilities search-capabilities
                   :identity identity
                   :identity-map identity-map
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   (-> (query-string-search-store
                        entity
                        query-string-search
                        (:query params)
                        (apply dissoc params filter-map-search-options)
                        identity-map
                        (select-keys params search-options))
                       page-with-long-id
                       un-store-page
                       paginated-ok))

              (GET "/:id" []
                   :return (s/maybe get-schema)
                   :summary (format "Gets a %s by ID" entity)
                   :path-params [id :- s/Str]
                   :query [params get-params]
                   :header-params [{Authorization :- (s/maybe s/Str) nil}]
                   :capabilities get-capabilities
                   :identity identity
                   :identity-map identity-map
                   (if-let [rec (read-store entity
                                            read
                                            id
                                            identity-map
                                            params)]
                     (-> rec
                         with-long-id
                         un-store
                         ok)
                     (not-found)))

              (DELETE "/:id" []
                      :no-doc hide-delete?
                      :path-params [id :- s/Str]
                      :summary (format "Deletes a %s" capitalized)
                      :header-params [{Authorization :- (s/maybe s/Str) nil}]
                      :capabilities delete-capabilities
                      :identity identity
                      :identity-map identity-map
                      (if (flows/delete-flow
                           :get-fn #(read-store entity
                                                read
                                                %
                                                identity-map
                                                {})
                           :delete-fn #(write-store entity
                                                    delete
                                                    %
                                                    identity-map)
                           :entity-type entity
                           :entity-id id
                           :identity identity)
                        (no-content)
                        (not-found)))))))
