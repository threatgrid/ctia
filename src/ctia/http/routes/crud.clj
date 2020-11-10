(ns ctia.http.routes.crud
  (:require
   [clj-momo.lib.clj-time.core :as time]
   [clojure.string :refer [capitalize]]
   ;; do not delete, defmethod side effects
   [ctia.http.middleware.auth :as auth]
   [compojure.api.core :refer [context DELETE GET POST PUT PATCH routes]]
   [ctia.domain.entities
    :refer
    [page-with-long-id
     un-store
     un-store-page
     with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [created
                                    filter-map-search-options
                                    paginated-ok
                                    search-options
                                    wait_for->refresh
                                    search-query
                                    coerce-date-range
                                    format-agg-result]]
   [ctia.schemas.core :refer [APIHandlerServices DelayedRoutes Entity]]
   [ctia.store :refer [query-string-search
                       query-string-count
                       aggregate
                       create-record
                       delete-record
                       read-record
                       update-record
                       list-records]]
   [ctia.schemas.search-agg :refer [HistogramParams
                                    CardinalityParams
                                    TopnParams
                                    MetricResult]]
   [ctia.lib.utils :refer [assoc-absent]]
   [ring.util.http-response :refer [no-content not-found ok]]
   [ring.swagger.schema :refer [describe]]
   [schema.core :as s]
   [schema-tools.core :as st]))

(defn fill-delayed-routes-config-defaults [delayed-routes-config]
  (assoc-absent
    delayed-routes-config
    :hide-delete? false
    :can-post? true
    :can-update? true
    :can-patch? false
    :can-search? true
    :can-aggregate? false
    :can-get-by-external-id? true
    :date-field :created
    :histogram-fields [:created]))

(s/defn capitalize-entity [entity :- (s/pred simple-keyword?)]
  (-> entity name capitalize))

(s/defn revoke-request
  "Implementation details of revocation-routes* that can
  be implemented with a function (rather than requiring a macro).
  
  Would be private, except it's used by a macro."
  [{{:keys [id]} :path-params
    {:keys [wait_for]} :query-params :as req}
   {{:keys [read-store
            write-store]} :StoreService
    :as services} :- APIHandlerServices
   {:keys [entity
           new-spec
           post-capabilities
           realize-fn
           revocation-update-fn]
    :as _delayed-routes-config}]
  {:pre [post-capabilities]}
  (let [_ (auth/capabilities! req post-capabilities)
        identity (auth/req->auth-identity req)
        identity-map (auth/req->identity-map req)]
    ;; almost identical to the PATCH route returned by entity-crud-routes
    ;; except for :update-fn and :partial-entity
    (if-let [updated-rec
             (-> (flows/patch-flow
                   :services services
                   :get-fn #(read-store entity
                                        read-record
                                        %
                                        identity-map
                                        {})
                   :realize-fn realize-fn
                   :update-fn #(write-store entity
                                            update-record
                                            (:id %)
                                            (cond-> %
                                              true (assoc-in [:valid_time :end_time] (time/internal-now))
                                              revocation-update-fn (revocation-update-fn {:req req}))
                                            identity-map
                                            (wait_for->refresh wait_for))
                   :long-id-fn #(with-long-id % services)
                   :entity-type entity
                   :entity-id id
                   :identity identity
                   :patch-operation :replace
                   :partial-entity {}
                   :spec new-spec)
                 un-store)]
      (ok updated-rec)
      (not-found (str (capitalize-entity entity) " not found")))))

(defmacro revocation-routes*
  "A lower level version of revocation-routes that
  allows configurable :query-params.

  :extra-query-params-syntax is assumed hygienic, so
  must be created by another macro.
  
  Assumes delayed-routes-config defaults have been filled
  by fill-delayed-routes-config-defaults."
  [services ;:- APIHandlerServices
   delayed-routes-config
   & {:keys [extra-query-params-syntax]}]
  `(let [services# ~services
         {entity# :entity
          entity-schema# :entity-schema
          :as delayed-routes-config#} ~delayed-routes-config]
     (POST "/:id/expire" [req#]
           :summary (format "Expires the supplied %s" (capitalize-entity entity#))
           :path-params [~'id :- s/Str]
           :query-params [~@extra-query-params-syntax
                          {~'wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
           :return entity-schema#
           (revoke-request req# services# delayed-routes-config#))))

(s/defn revocation-routes
  "Returns POST /:id/expire routes for the given entity

  Assumes delayed-routes-config defaults have been filled
  by fill-delayed-routes-config-defaults."
  [services :- APIHandlerServices
   delayed-routes-config]
  (revocation-routes* services
                      delayed-routes-config))

(s/defn entity-crud-routes
 :- DelayedRoutes
 [delayed-routes-config-no-defaults]
 (s/fn [{{:keys [write-store read-store]} :StoreService
         :as services} :- APIHandlerServices]
  (let [{:keys [entity
                can-revoke?
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
                can-aggregate?
                can-get-by-external-id?
                date-field
                histogram-fields
                enumerable-fields]
         :as delayed-routes-config} (fill-delayed-routes-config-defaults
                                     delayed-routes-config-no-defaults)
        capitalized (capitalize-entity entity)
        search-filters (st/dissoc search-q-params
                                  :sort_by
                                  :sort_order
                                  :fields
                                  :limit
                                  :offset)
        agg-search-schema (st/merge
                           search-filters
                           {:from s/Inst})
        aggregate-on-enumerable {:aggregate-on (apply s/enum (map name enumerable-fields))}
        histogram-filters {:aggregate-on (apply s/enum (map name histogram-fields))
                           :from (describe s/Inst "Start date of the histogram. Filters the value of selected aggregated-on field.")
                           (s/optional-key :to) (describe s/Inst "End date of the histogram. Filters the value of selected aggregated-on field.")}
        histogram-q-params (st/merge agg-search-schema
                                     HistogramParams
                                     histogram-filters)
        cardinality-q-params (st/merge agg-search-schema
                                       aggregate-on-enumerable)
        topn-q-params (st/merge agg-search-schema
                                TopnParams
                                aggregate-on-enumerable)]
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
                  :services services
                  :entity-type entity
                  :realize-fn realize-fn
                  :store-fn #(write-store entity
                                          create-record
                                          %
                                          identity-map
                                          (wait_for->refresh wait_for))
                  :long-id-fn #(with-long-id % services)
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
            :summary (format "Updates an existing %s" capitalized)
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :capabilities put-capabilities
            :auth-identity identity
            :identity-map identity-map
            (if-let [updated-rec
                     (-> (flows/update-flow
                          :services services
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
                          :long-id-fn #(with-long-id % services)
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
                            :services services
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
                            :long-id-fn #(with-long-id % services)
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
                (page-with-long-id services)
                un-store-page
                paginated-ok)))

     (when can-search?
       (context "/search" []
                :capabilities search-capabilities
                :auth-identity identity
                :identity-map identity-map
                (GET "/" []
                     :return search-schema
                     :summary (format "Search for %s entities using a Lucene/ES query string and field filters" capitalized)
                     :query [params search-q-params]
                     (-> (read-store
                          entity
                          query-string-search
                          (search-query date-field params)
                          identity-map
                          (select-keys params search-options))
                         (page-with-long-id services)
                         un-store-page
                         paginated-ok))
                (GET "/count" []
                     :return s/Int
                     :summary (format "Count %s matching a Lucene/ES query string and field filters" capitalized)
                     :query [params search-filters]
                     (ok (read-store
                          entity
                          query-string-count
                          (search-query date-field params)
                          identity-map)))))
     (when can-aggregate?
       (context "/metric" []
                :capabilities search-capabilities
                :auth-identity identity
                :identity-map identity-map
                (GET "/histogram" []
                     :return MetricResult
                     :summary (format "Histogram for some %s field" capitalized)
                     :query [params histogram-q-params]
                     (let [aggregate-on (keyword (:aggregate-on params))
                           search-q (search-query aggregate-on
                                                  (st/select-schema params agg-search-schema)
                                                  coerce-date-range)
                           agg-q (st/assoc (st/select-schema params HistogramParams)
                                           :agg-type :histogram)]
                       (-> (read-store
                            entity
                            aggregate
                            search-q
                            agg-q
                            identity-map)
                           (format-agg-result :histogram aggregate-on search-q)
                           ok)))
                (GET "/topn" []
                     :return MetricResult
                     :summary (format "Topn for some %s field" capitalized)
                     :query [params topn-q-params]
                     (let [aggregate-on (:aggregate-on params)
                           search-q (search-query date-field
                                                  (st/select-schema params agg-search-schema)
                                                  coerce-date-range)
                           agg-q (st/assoc (st/select-schema params TopnParams)
                                           :agg-type :topn)]
                       (-> (read-store
                            entity
                            aggregate
                            search-q
                            agg-q
                            identity-map)
                           (format-agg-result :topn aggregate-on search-q)
                           ok)))
                (GET "/cardinality" []
                     :return MetricResult
                     :summary (format "Cardinality for some %s field" capitalized)
                     :query [params cardinality-q-params]
                     (let [aggregate-on (:aggregate-on params)
                           search-q (search-query date-field
                                                  (st/select-schema params agg-search-schema)
                                                  coerce-date-range)
                           agg-q (st/assoc (st/select-schema params CardinalityParams)
                                           :agg-type :cardinality)]
                       (-> (read-store
                            entity
                            aggregate
                            search-q
                            agg-q
                            identity-map)
                           (format-agg-result :cardinality aggregate-on search-q)
                           ok)))))
     (GET "/:id" []
          :return (s/maybe get-schema)
          :summary (format "Gets one %s by ID" capitalized)
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
                (with-long-id services)
                un-store
                ok)
            (not-found)))

     (DELETE "/:id" []
             :no-doc hide-delete?
             :path-params [id :- s/Str]
             :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
             :summary (format "Deletes one %s" capitalized)
             :capabilities delete-capabilities
             :auth-identity identity
             :identity-map identity-map
             (if (flows/delete-flow
                  :services services
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
                  :long-id-fn #(with-long-id % services)
                  :entity-id id
                  :identity identity)
               (no-content)
               (not-found)))
     (when can-revoke?
       (revocation-routes
         services
         delayed-routes-config))))))

(s/defn services->entity-crud-routes
  [services :- APIHandlerServices
   opt]
  ((entity-crud-routes opt)
   services))
