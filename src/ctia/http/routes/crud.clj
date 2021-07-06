(ns ctia.http.routes.crud
  (:require [clj-momo.lib.clj-time.core :as time]
            [clojure.string :as str]
            [ctia.domain.entities
             :refer
             [page-with-long-id un-store un-store-page with-long-id]]
            [ctia.flows.crud :as flows]
            [ctia.http.routes.common
             :as routes.common
             :refer [capabilities->description
                     coerce-date-range
                     created
                     format-agg-result
                     paginated-ok
                     search-options
                     search-query
                     wait_for->refresh]]
            [ctia.lib.compojure.api.core
             :refer
             [context DELETE GET PATCH POST PUT routes]]
            [ctia.schemas.core :refer [APIHandlerServices DelayedRoutes]]
            [ctia.schemas.search-agg
             :refer
             [CardinalityParams HistogramParams MetricResult TopnParams]]
            [ctia.store
             :refer
             [aggregate
              create-record
              delete-record
              delete-search
              list-records
              query-string-count
              query-string-search
              read-record
              update-record]]
            [ring.swagger.schema :refer [describe]]
            [ring.util.http-response :refer [forbidden no-content not-found ok]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(s/defn capitalize-entity [entity :- (s/pred simple-keyword?)]
  (-> entity name str/capitalize))

(s/defn revoke-request
  "Process POST /:id/expire route.
  Implemented separately from a POST call to share
  between entity implementations with different :query-params
  requirements (which must be provided at compile-time with
  POST)."
  [req :- (s/pred map?)
   {{:keys [get-store]} :StoreService
    :as services} :- APIHandlerServices
   {:keys [entity
           new-spec
           realize-fn]
    :as _entity-crud-config}
   {:keys [identity
           identity-map
           id
           revocation-update-fn
           wait_for]} :- {:identity s/Any
                          :identity-map s/Any
                          :id s/Any
                          :wait_for (s/pred (some-fn nil? boolean?)
                                            'nilable-boolean?)
                          (s/optional-key :revocation-update-fn) (s/pred ifn?)}]
  ;; almost identical to the PATCH route returned by entity-crud-routes
  ;; except for :update-fn and :partial-entity
  (if-let [updated-rec
           (flows/patch-flow
            :services services
            :get-fn (fn [_]
                      (-> (get-store entity)
                          (read-record
                            id
                            identity-map
                            {})))
            :realize-fn realize-fn
            :update-fn #(-> (get-store entity)
                            (update-record
                              (:id %)
                              (cond-> %
                                true (assoc-in [:valid_time :end_time] (time/internal-now))
                                revocation-update-fn (revocation-update-fn {:req req}))
                              identity-map
                              (wait_for->refresh wait_for)))
            :long-id-fn #(with-long-id % services)
            :entity-type entity
            :entity-id id
            :identity identity
            :patch-operation :replace
            :partial-entity {}
            :spec new-spec)]
    (ok (un-store updated-rec))
    (not-found (str (capitalize-entity entity) " not found"))))

(s/defn revocation-routes
  "Returns POST /:id/expire routes for the given entity."
  [services :- APIHandlerServices
   {:keys [entity
           entity-schema
           post-capabilities] :as entity-crud-config}]
  (let [capabilities post-capabilities]
    (POST "/:id/expire" req
          :summary (format "Expires the supplied %s" (capitalize-entity entity))
          :path-params [id :- s/Str]
          :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
          :return entity-schema
          :description (capabilities->description capabilities)
          :capabilities capabilities
          :auth-identity identity
          :identity-map identity-map
          (revoke-request req services entity-crud-config
                          {:id id
                           :identity identity
                           :identity-map identity-map
                           :wait_for wait_for}))))

(s/defn ^:private entity-crud-routes
  :- DelayedRoutes
  "Implementation of services->entity-crud-routes."
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
           can-revoke?
           can-search?
           can-aggregate?
           can-get-by-external-id?
           date-field
           histogram-fields
           enumerable-fields
           searchable-fields]
    :or {hide-delete?            false
         can-post?               true
         can-update?             true
         can-patch?              false
         can-search?             true
         can-aggregate?          false
         can-get-by-external-id? true
         date-field              :created
         histogram-fields        [:created]}
    :as entity-crud-config}]
 (s/fn [{{:keys [get-store]} :StoreService
         :as services} :- APIHandlerServices]
  (let [capitalized (capitalize-entity entity)
        search-q-params* (routes.common/prep-es-fields-schema entity-crud-config)
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
       (let [capabilities post-capabilities]
         (POST "/" []
               :return entity-schema
               :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
               :body [new-entity new-schema {:description (format "a new %s" capitalized)}]
               :summary (format "Adds a new %s" capitalized)
               :description (capabilities->description capabilities)
               :capabilities capabilities
               :auth-identity identity
               :identity-map identity-map
               (-> (flows/create-flow
                    :services services
                    :entity-type entity
                    :realize-fn realize-fn
                    :store-fn #(-> (get-store entity)
                                   (create-record
                                     %
                                     identity-map
                                     (wait_for->refresh wait_for)))
                    :long-id-fn #(with-long-id % services)
                    :entity-type entity
                    :identity identity
                    :entities [new-entity]
                    :spec new-spec)
                   first
                   un-store
                   created))))
     (when can-update?
       (let [capabilities put-capabilities]
         (PUT "/:id" []
              :return entity-schema
              :body [entity-update new-schema {:description (format "an updated %s" capitalized)}]
              :summary (format "Update an existing %s" capitalized)
              :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
              :path-params [id :- s/Str]
              :description (capabilities->description capabilities)
              :capabilities capabilities
              :auth-identity identity
              :identity-map identity-map
              (if-let [updated-rec
                       (-> (flows/update-flow
                            :services services
                            :get-fn #(-> (get-store entity)
                                         (read-record
                                           %
                                           identity-map
                                           {}))
                            :realize-fn realize-fn
                            :update-fn #(-> (get-store entity)
                                            (update-record
                                              (:id %)
                                              %
                                              identity-map
                                              (wait_for->refresh wait_for)))
                            :long-id-fn #(with-long-id % services)
                            :entity-type entity
                            :entity-id id
                            :identity identity
                            :entity entity-update
                            :spec new-spec)
                           un-store)]
                (ok updated-rec)
                (not-found)))))
     (when can-patch?
       (let [capabilities patch-capabilities]
         (PATCH "/:id" []
                :return entity-schema
                :body [partial-update patch-schema {:description (format "%s partial update" capitalized)}]
                :summary (format "Partially update an existing %s" capitalized)
                :query-params [{wait_for :- (describe s/Bool "wait for patched entity to be available for search") nil}]
                :path-params [id :- s/Str]
                :description (capabilities->description capabilities)
                :capabilities capabilities
                :auth-identity identity
                :identity-map identity-map
                (if-let [updated-rec
                         (-> (flows/patch-flow
                              :services services
                              :get-fn #(-> (get-store entity)
                                           (read-record
                                             %
                                             identity-map
                                             {}))
                              :realize-fn realize-fn
                              :update-fn #(-> (get-store entity)
                                              (update-record
                                                (:id %)
                                                %
                                                identity-map
                                                (wait_for->refresh wait_for)))
                              :long-id-fn #(with-long-id % services)
                              :entity-type entity
                              :entity-id id
                              :identity identity
                              :patch-operation :replace
                              :partial-entity partial-update
                              :spec new-spec)
                             un-store)]
                  (ok updated-rec)
                  (not-found)))))
     (when can-get-by-external-id?
       (let [capabilities external-id-capabilities
             _ (assert capabilities
                       (str ":external-id-capabilities is missing for " entity))]
         (GET "/external_id/:external_id" []
              :return list-schema
              :query [q external-id-q-params]
              :path-params [external_id :- s/Str]
              :summary (format "List %s by external id" capitalized)
              :description (capabilities->description capabilities)
              ;; TODO unit test this capability is required in entity-crud-test
              :capabilities capabilities
              :auth-identity identity
              :identity-map identity-map
              (-> (get-store entity)
                  (list-records
                    {:all-of {:external_ids external_id}}
                    identity-map
                    q)
                  (page-with-long-id services)
                  un-store-page
                  paginated-ok))))

     (when can-search?
       (let [delete-search-capabilities (->> [search-capabilities delete-capabilities]
                                             (map #(if (coll? %) % [%]))
                                             (reduce into #{}))]
         (context "/search" []
           :auth-identity identity
           :identity-map identity-map
           (GET "/" []
             :return search-schema
             :summary (format "Search for %s entities using a ES query syntax and field filters" capitalized)
             :description (capabilities->description search-capabilities)
             :capabilities search-capabilities
             :query [params search-q-params*]
             (let [params* (routes.common/enforce-search-fields
                            params
                            searchable-fields)]
              (-> (get-store entity)
                  (query-string-search
                   (search-query date-field params*)
                   identity-map
                   (select-keys params* search-options))
                  (page-with-long-id services)
                  un-store-page
                  paginated-ok)))
           (GET "/count" []
             :return s/Int
             :summary (format "Count %s matching a Lucene/ES query string and field filters" capitalized)
             :description (capabilities->description search-capabilities)
             :capabilities search-capabilities
             :query [params search-filters]
             (ok (-> (get-store entity)
                     (query-string-count
                       (search-query date-field params)
                       identity-map))))
           (DELETE "/" []
             :capabilities delete-search-capabilities
             :description (capabilities->description delete-search-capabilities)
             :return s/Int
             :summary (format "Delete %s entities matching given Lucene/ES query string or/and field filters" capitalized)
             :query [params (into search-filters
                                  {(s/optional-key :wait_for)
                                   (describe s/Bool "wait for matched entity to be deleted")
                                   (s/optional-key :REALLY_DELETE_ALL_THESE_ENTITIES)
                                   (describe s/Bool
                                             (str
                                              " If you do not set this value or set it to false"
                                              " this route will perform a dry run."
                                              " Set this value to true to perform the deletion."
                                              " You MUST confirm you will fix the mess after"
                                              " the inevitable disaster that will occur after"
                                              " you perform that operation."
                                              " DO NOT FORGET TO SET THAT TO FALSE AFTER EACH DELETION"
                                              " IF YOU INTEND TO USE THAT ROUTE MULTIPLE TIMES."))})]
             (let [query (->> (dissoc params :wait_for :REALLY_DELETE_ALL_THESE_ENTITIES)
                              (search-query date-field))]
               (if (empty? query)
                 (forbidden {:error "you must provide at least one of from, to, query or any field filter."})
                 (ok
                  (if (:REALLY_DELETE_ALL_THESE_ENTITIES params)
                    (-> (get-store entity)
                        (delete-search
                          query
                          identity-map
                          (wait_for->refresh (:wait_for params))))
                    (-> (get-store entity)
                        (query-string-count
                          query
                          identity-map))))))))))
     (when can-aggregate?
       (let [capabilities search-capabilities]
         (context "/metric" []
                  :description (capabilities->description capabilities)
                  :capabilities capabilities
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
                         (-> (get-store entity)
                             (aggregate
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
                         (-> (get-store entity)
                             (aggregate
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
                         (-> (get-store entity)
                             (aggregate
                               search-q
                               agg-q
                               identity-map)
                             (format-agg-result :cardinality aggregate-on search-q)
                             ok))))))
     (let [capabilities get-capabilities]
       (GET "/:id" []
            :return (s/maybe get-schema)
            :summary (format "Get one %s by ID" capitalized)
            :path-params [id :- s/Str]
            :query [params get-params]
            :description (capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (if-let [rec (-> (get-store entity)
                             (read-record
                               id
                               identity-map
                               params))]
              (-> rec
                  (with-long-id services)
                  un-store
                  ok)
              (not-found))))

     (let [capabilities delete-capabilities]
       (DELETE "/:id" []
               :no-doc hide-delete?
               :path-params [id :- s/Str]
               :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
               :summary (format "Delete one %s" capitalized)
               :description (capabilities->description capabilities)
               :capabilities capabilities
               :auth-identity identity
               :identity-map identity-map
               (if (flows/delete-flow
                    :services services
                    :get-fn #(-> (get-store entity)
                                 (read-record
                                   %
                                   identity-map
                                   {}))
                    :delete-fn #(-> (get-store entity)
                                    (delete-record
                                      %
                                      identity-map
                                      (wait_for->refresh wait_for)))
                    :entity-type entity
                    :long-id-fn #(with-long-id % services)
                    :entity-id id
                    :identity identity)
                 (no-content)
                 (not-found))))

     (when can-revoke?
       (revocation-routes
        services
        entity-crud-config))))))

(s/defn services->entity-crud-routes
  [services :- APIHandlerServices
   opt]
  ((entity-crud-routes opt)
   services))
