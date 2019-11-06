(ns ctia.http.routes.crud
  (:require
   [clojure.string :refer [capitalize]]
   [ctia.http.middleware.auth :refer :all]
   [compojure.api.sweet :refer [DELETE GET POST PUT PATCH routes]]
   [ctia.domain.entities
    :refer
    [page-with-long-id
     un-store
     un-store-page
     with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer
    [created filter-map-search-options paginated-ok search-options]]
   [ctia.store :refer :all]
   [ring.util.http-response :refer [no-content not-found ok]]
   [ring.swagger.schema :refer [describe]]
   [schema.core :as s]))

(defn wait_for->refresh
  [wait_for]
  (case wait_for
    true {:refresh "wait_for"}
    false {:refresh "false"}
    {}))

(defn entity-crud-routes
  [{:keys [entity
           new-schema
           entity-schema
           get-schema
           get-params
           list-schema
           search-schema
           patch-schema
           external-id-q-params
           search-q-params
           new-spec
           realize-fn
           get-capabilities
           post-capabilities
           put-capabilities
           patch-capabilities
           delete-capabilities
           search-capabilities
           external-id-capabilities
           hide-delete?
           can-post?
           can-update?
           can-patch?
           can-search?
           can-get-by-external-id?]
    :or {hide-delete? false
         can-post? true
         can-update? true
         can-patch? false
         can-search? true
         can-get-by-external-id? true}}]
  (let [entity-str (name entity)
        capitalized (capitalize entity-str)]
    (routes
     (when can-post?
       (POST "/" []
             :return entity-schema
             :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
             :body [new-entity new-schema {:description (format "a new %s" capitalized)}]
             :summary (format "Adds a new %s" capitalized)
             :capabilities post-capabilities
             :auth-identity identity
             :identity-map identity-map
             (-> (flows/create-flow
                  :entity-type entity
                  :realize-fn realize-fn
                  :store-fn #(write-store entity
                                          create-record
                                          %
                                          identity-map
                                          (wait_for->refresh wait_for))
                  :long-id-fn with-long-id
                  :entity-type entity
                  :identity identity
                  :entities [new-entity]
                  :spec new-spec)
                 first
                 un-store
                 created)))
     (when can-update?
       (PUT "/:id" []
            :return entity-schema
            :body [entity-update new-schema {:description (format "an updated %s" capitalized)}]
            :summary (format "Updates an %s" capitalized)
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :capabilities put-capabilities
            :auth-identity identity
            :identity-map identity-map
            (if-let [updated-rec
                     (-> (flows/update-flow
                          :get-fn #(read-store entity
                                               read-record
                                               %
                                               identity-map
                                               {})
                          :realize-fn realize-fn
                          :update-fn #(write-store entity
                                                   update-record
                                                   (:id %)
                                                   %
                                                   identity-map
                                                   (wait_for->refresh wait_for))
                          :long-id-fn with-long-id
                          :entity-type entity
                          :entity-id id
                          :identity identity
                          :entity entity-update
                          :spec new-spec)
                         un-store)]
              (ok updated-rec)
              (not-found))))
     (when can-patch?
       (PATCH "/:id" []
              :return entity-schema
              :body [partial-update patch-schema {:description (format "%s partial update" capitalized)}]
              :summary (format "Partially Update %s" capitalized)
              :query-params [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
              :path-params [id :- s/Str]
              :capabilities patch-capabilities
              :auth-identity identity
              :identity-map identity-map
              (if-let [updated-rec
                       (-> (flows/patch-flow
                            :get-fn #(read-store entity
                                                 read-record
                                                 %
                                                 identity-map
                                                 {})
                            :realize-fn realize-fn
                            :update-fn #(write-store entity
                                                     update-record
                                                     (:id %)
                                                     %
                                                     identity-map
                                                     (wait_for->refresh wait_for))
                            :long-id-fn with-long-id
                            :entity-type entity
                            :entity-id id
                            :identity identity
                            :patch-operation :replace
                            :partial-entity partial-update
                            :spec new-spec)
                           un-store)]
                (ok updated-rec)
                (not-found))))
     (when can-get-by-external-id?
       (GET "/external_id/:external_id" []
            :return list-schema
            :query [q external-id-q-params]
            :path-params [external_id :- s/Str]
            :summary (format "List %s by external id" capitalized)
            :capabilities external-id-capabilities
            :auth-identity identity
            :identity-map identity-map
            (-> (read-store entity
                            list-records
                            {:all-of {:external_ids external_id}}
                            identity-map
                            q)
                page-with-long-id
                un-store-page
                paginated-ok)))

     (when can-search?
       (GET "/search" []
            :return search-schema
            :summary (format "Search for a %s using a Lucene/ES query string" capitalized)
            :query [params search-q-params]
            :capabilities search-capabilities
            :auth-identity identity
            :identity-map identity-map
            (-> (query-string-search-store
                 entity
                 query-string-search
                 (:query params)
                 (apply dissoc params filter-map-search-options)
                 identity-map
                 (select-keys params search-options))
                page-with-long-id
                un-store-page
                paginated-ok)))

     (GET "/:id" []
          :return (s/maybe get-schema)
          :summary (format "Gets a %s by ID" entity-str)
          :path-params [id :- s/Str]
          :query [params get-params]
          :capabilities get-capabilities
          :auth-identity identity
          :identity-map identity-map
          (if-let [rec (read-store entity
                                   read-record
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
             :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
             :summary (format "Deletes a %s" capitalized)
             :capabilities delete-capabilities
             :auth-identity identity
             :identity-map identity-map
             (if (flows/delete-flow
                  :get-fn #(read-store entity
                                       read-record
                                       %
                                       identity-map
                                       {})
                  :delete-fn #(write-store entity
                                           delete-record
                                           %
                                           identity-map
                                           (wait_for->refresh wait_for))
                  :entity-type entity
                  :long-id-fn with-long-id
                  :entity-id id
                  :identity identity)
               (no-content)
               (not-found))))))
