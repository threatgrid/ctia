(ns ctia.http.routes.crud
  (:require
   [clj-momo.lib.clj-time.core :as time]
   [clojure.string :as str]
   [ctia.domain.entities :as ent :refer [with-long-id]]
   [ctia.flows.crud :as flows]
   [ctia.http.middleware.auth]
   [ctia.http.routes.common :as routes.common :refer [capabilities->description
                                                      wait_for->refresh
                                                      search-query
                                                      coerce-date-range]]
   [ctia.lib.compojure.api.core :refer [context DELETE GET POST PUT PATCH routes]]
   [ctia.schemas.core :refer [APIHandlerServices DelayedRoutes SortExtensionDefinitions]]
   [ctia.schemas.search-agg :refer [AverageParams
                                    HistogramParams
                                    CardinalityParams
                                    TopnParams
                                    MetricResult]]
   [ctia.store :as store]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response :refer [no-content not-found ok forbidden]]
   [schema-tools.core :as st]
   [schema.core :as s]
   [compojure.api.routes :as routes]))

(s/defn capitalize-entity [entity :- (s/pred simple-keyword?)]
  (-> entity name str/capitalize))

(s/defn add-flags-to-delete-search-query-params :- (s/protocol s/Schema)
  [search-filters :- (s/protocol s/Schema)]
  (st/merge search-filters
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
                         " IF YOU INTEND TO USE THAT ROUTE MULTIPLE TIMES."))}))

(defn flow-get-by-ids-fn
  [{:keys [get-store entity identity-map]}]
  (let [s (get-store entity)]
    (fn [ids]
      (store/read-records s ids identity-map {}))))

(defn flow-update-fn
  [{:keys [identity-map wait_for get-store entity]}]
  (let [update-fn #(-> (get-store entity)
                       (store/update-record
                        (:id %)
                        %
                        identity-map
                        (wait_for->refresh wait_for)))]
    (fn [patches]
      (keep update-fn patches))))

(defn flow-delete-fn
  [{:keys [identity-map wait_for get-store entity]}]
  (let [delete-fn
        #(-> (get-store entity)
             (store/delete-record
              %
              identity-map
              (wait_for->refresh wait_for)))]
    (fn [ids]
      (map delete-fn ids))))

(s/defschema RevokeParams
  {:identity s/Any
   :identity-map s/Any
   :id s/Any
   :wait_for (s/pred (some-fn nil? boolean?)
                     'nilable-boolean?)
   (s/optional-key :revocation-update-fn) (s/pred ifn?)})

(s/defn revoke-request
  "Process POST /:id/expire route.
  Implemented separately from a POST call to share
  between entity implementations with different :query-params
  requirements (which must be provided at compile-time with
  POST)."
  [{{:keys [get-store]} :StoreService :as services} :- APIHandlerServices
   {:keys [entity new-spec realize-fn] :as _entity-crud-config}
   revoke-params :- RevokeParams]
  ;; almost identical to the PATCH route returned by entity-crud-routes
  ;; except for :update-fn and :partial-entity
  (let [{ident :identity
         :keys [identity-map id revocation-update-fn wait_for]
         :or {revocation-update-fn identity}} revoke-params

        get-by-ids (flow-get-by-ids-fn
                    {:get-store get-store
                     :entity entity
                     :identity-map identity-map})
        update-fn (flow-update-fn
                   {:get-store get-store
                    :entity entity
                    :identity-map identity-map
                    :wait_for (routes.common/wait_for->refresh wait_for)})
        revoke-update-fn (fn [entities]
                           (update-fn (map revocation-update-fn entities)))
        patch {:id id
               :valid_time {:end_time (time/internal-now)}}
        prev-entity (first (get-by-ids [id]))]
    (if prev-entity
      (-> (flows/patch-flow
           :services services
           :get-fn get-by-ids
           :realize-fn realize-fn
           :update-fn revoke-update-fn
           :long-id-fn #(with-long-id % services)
           :entity-type entity
           :identity ident
           :patch-operation :replace
           :partial-entities [patch]
           :spec new-spec)
          first
          ok)
      (not-found (str (capitalize-entity entity) " not found")))))

(s/defn revocation-routes
  "Returns POST /:id/expire routes for the given entity."
  [services :- APIHandlerServices
   {:keys [entity
           entity-schema
           post-capabilities] :as entity-crud-config}]
  (let [capabilities post-capabilities]
    (POST "/:id/expire" []
          :summary (format "Expires the supplied %s" (capitalize-entity entity))
          :path-params [id :- s/Str]
          :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
          :responses {200 {:schema entity-schema}}
          :description (capabilities->description capabilities)
          :capabilities capabilities
          :auth-identity identity
          :identity-map identity-map
          (revoke-request services entity-crud-config
                          {:id id
                           :identity identity
                           :identity-map identity-map
                           :wait_for wait_for}))))

(s/defschema EntityCrudRoutesArgs
  {(s/optional-key :sort-extension-definitions) SortExtensionDefinitions
   (s/optional-key :average-fields) {s/Keyword {:date-field s/Keyword}}
   s/Any s/Any})

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
           sort-extension-definitions
           average-fields]
    :or {hide-delete? false
         can-post? true
         can-update? true
         can-patch? false
         can-search? true
         can-aggregate? false
         can-get-by-external-id? true
         date-field :created
         histogram-fields [:created]}
    :as entity-crud-config}
   :- EntityCrudRoutesArgs]
 (s/fn [{{:keys [get-store]} :StoreService
         {:keys [flag-value]} :FeaturesService
         :as services} :- APIHandlerServices]
  (let [capitalized (capitalize-entity entity)
        search-q-params* (-> (routes.common/prep-es-fields-schema
                               services
                               entity-crud-config)
                             routes.common/prep-sort_by-param-schema)
        search-filters (apply st/dissoc search-q-params
                              :sort_by
                              :sort_order
                              :fields
                              :limit
                              :offset
                              (keys sort-extension-definitions))
        agg-search-schema (st/merge
                           search-filters
                           {:from s/Inst})
        aggregate-on-enumerable {:aggregate-on (apply s/enum (map name enumerable-fields))}
        average-filters {:aggregate-on (describe (apply s/enum (map name (keys average-fields)))
                                                 (apply str
                                                        "The field to compute an average over. `from` and `to` are dates to narrow the "
                                                        "entities used in this calculation.\n\n"
                                                        (->> average-fields
                                                             (map (fn [[aggregate-on {:keys [date-field]}]]
                                                                    (str "When aggregate-on=" (name aggregate-on)
                                                                         ", from/to filters on "
                                                                         (case date-field
                                                                           ;; :created is internal
                                                                           :created " when the incident was first ingested in CTIA"
                                                                           (name date-field))
                                                                         ".")))
                                                             (str/join " "))))
                         :from (describe s/Inst "Start date of the average. See `aggregate-on` doc for which field is used to filter.")
                         (s/optional-key :to) (describe s/Inst "End date of the average. See `aggregate-on` doc for which field is used to filter.")}
        average-q-params (st/merge agg-search-schema
                                   AverageParams
                                   average-filters)
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
                                aggregate-on-enumerable)
        get-by-ids-fn (fn [identity-map]
                        (flow-get-by-ids-fn
                         {:get-store get-store
                          :entity entity
                          :identity-map identity-map}))
        update-fn (fn [identity-map wait_for]
                    (flow-update-fn
                     {:get-store get-store
                      :entity entity
                      :identity-map identity-map
                      :wait_for wait_for}))
        add-search-extensions (fn [params]
                                (-> params
                                    (assoc :sort-extension-definitions (get entity-crud-config :sort-extension-definitions {}))))]
   (routes
     (when can-post?
       (let [capabilities post-capabilities]
         (POST "/" []
               :responses {201 {:schema entity-schema}}
               :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
               :body [new-entity (describe new-schema (format "a new %s" capitalized))]
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
                                   (store/create-record
                                     %
                                     identity-map
                                     (wait_for->refresh wait_for)))
                    :long-id-fn #(with-long-id % services)
                    :entity-type entity
                    :identity identity
                    :entities [new-entity]
                    :spec new-spec)
                   first
                   routes.common/created))))
     (when can-update?
       (let [capabilities put-capabilities]
         (PUT "/:id" []
              :responses {200 {:schema entity-schema}}
              :body [entity-update (describe new-schema (format "an updated %s" capitalized))]
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
                            :get-fn (get-by-ids-fn identity-map)
                            :realize-fn realize-fn
                            :update-fn (update-fn identity-map wait_for)
                            :long-id-fn #(with-long-id % services)
                            :entity-type entity
                            :identity identity
                            :entities [(assoc entity-update :id id)]
                            :spec new-spec)
                           first)]
                (ok updated-rec)
                (not-found)))))
     (when can-patch?
       (let [capabilities patch-capabilities]
         (PATCH "/:id" []
                :responses {200 {:schema entity-schema}}
                :body [partial-update (describe patch-schema (format "%s partial update" capitalized))]
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
                              :get-fn (get-by-ids-fn identity-map)
                              :realize-fn realize-fn
                              :update-fn (update-fn identity-map wait_for)
                              :long-id-fn #(with-long-id % services)
                              :entity-type entity
                              :identity identity
                              :patch-operation :replace
                              :partial-entities [(assoc partial-update :id id)]
                              :spec new-spec)
                             first)]
                  (ok updated-rec)
                  (not-found)))))
     (when can-get-by-external-id?
       (let [capabilities external-id-capabilities
             _ (assert capabilities
                       (str ":external-id-capabilities is missing for " entity))]
         (GET "/external_id/:external_id" []
              :responses {200 {:schema list-schema}}
              :query [q external-id-q-params]
              :path-params [external_id :- s/Str]
              :summary (format "List %s by external id" capitalized)
              :description (capabilities->description capabilities)
              ;; TODO unit test this capability is required in entity-crud-test
              :capabilities capabilities
              :auth-identity identity
              :identity-map identity-map
              (-> (get-store entity)
                  (store/list-records
                    {:all-of {:external_ids external_id}}
                    identity-map
                    q)
                  (ent/page-with-long-id services)
                  routes.common/paginated-ok))))

     (when can-search?
       (let [delete-search-capabilities (->> [search-capabilities delete-capabilities]
                                             (map #(if (coll? %) % [%]))
                                             (reduce into #{}))]
         (context "/search" []
           (GET "/" []
             :auth-identity identity
             :identity-map identity-map
             :responses {200 {:schema search-schema}}
             :summary (format "Search for %s entities using a ES query syntax and field filters" capitalized)
             :description (capabilities->description search-capabilities)
             :capabilities search-capabilities
             :query [params search-q-params*]
             (-> (get-store entity)
                 (store/query-string-search
                   (-> {:search-query (-> {:date-field date-field
                                           :params params}
                                          add-search-extensions
                                          (search-query services))
                        :ident identity-map
                        :params (select-keys params routes.common/search-options)}
                       add-search-extensions))
                 (ent/page-with-long-id services)
                 routes.common/paginated-ok))
           (GET "/count" []
             :auth-identity identity
             :identity-map identity-map
             :responses {200 {:schema s/Int}}
             :summary (format "Count %s matching a Lucene/ES query string and field filters" capitalized)
             :description (capabilities->description search-capabilities)
             :capabilities search-capabilities
             :query [params search-filters]
             (ok (-> (get-store entity)
                     (store/query-string-count
                       (search-query {:date-field date-field
                                      :params params}
                                     services)
                       identity-map))))
           (DELETE "/" []
             :auth-identity identity
             :identity-map identity-map
             :capabilities delete-search-capabilities
             :description (capabilities->description delete-search-capabilities)
             :responses {200 {:schema s/Int}}
             :summary (format "Delete %s entities matching given Lucene/ES query string or/and field filters" capitalized)
             :query [params (add-flags-to-delete-search-query-params search-filters)]
             (let [query (search-query {:date-field date-field
                                        :params (dissoc params :wait_for :REALLY_DELETE_ALL_THESE_ENTITIES)}
                                       services)]
               (if (empty? query)
                 (forbidden {:error "you must provide at least one of from, to, query or any field filter."})
                 (ok
                  (if (:REALLY_DELETE_ALL_THESE_ENTITIES params)
                    (-> (get-store entity)
                        (store/delete-search
                          query
                          identity-map
                          {:wait_for_completion (boolean (:wait_for params))}))
                    (-> (get-store entity)
                        (store/query-string-count
                          query
                          identity-map))))))))))
     (when can-aggregate?
       (let [capabilities search-capabilities]
         (context "/metric" []
                  :description (capabilities->description capabilities)
                  :capabilities capabilities
                  (routes
                    (when (seq average-fields)
                      (GET "/average" []
                           :auth-identity identity
                           :identity-map identity-map
                           :responses {200 {:schema MetricResult}}
                           :summary (format (str "Average for some %s field. Use X-Total-Hits header on response for count used for average."
                                                 " For aggregate-on field X.Y.Z, response body will be {:data {:X {:Y {:Z <average>}}}}."
                                                 " If X-Total-Hits is 0, then average will be nil.")
                                            capitalized)
                           :query [params average-q-params]
                           (let [aggregate-on (keyword (:aggregate-on params))
                                 date-field (or (get-in average-fields [aggregate-on :date-field])
                                                ;; should never happen but a reasonable default
                                                :created)
                                 search-q (search-query {:date-field date-field
                                                         :params (st/select-schema params agg-search-schema)
                                                         :make-date-range-fn coerce-date-range}
                                                        services)
                                 agg-q (assoc (st/select-schema params AverageParams)
                                              :agg-type :avg)]
                             (-> (get-store entity)
                                 (store/aggregate
                                   search-q
                                   agg-q
                                   identity-map)
                                 (routes.common/format-agg-result :avg aggregate-on search-q)
                                 routes.common/paginated-ok))))
                    (GET "/histogram" []
                         :auth-identity identity
                         :identity-map identity-map
                         :responses {200 {:schema MetricResult}}
                         :summary (format "Histogram for some %s field" capitalized)
                         :query [params histogram-q-params]
                         (let [aggregate-on (keyword (:aggregate-on params))
                               search-q (search-query {:date-field aggregate-on
                                                       :params (st/select-schema params agg-search-schema)
                                                       :make-date-range-fn coerce-date-range}
                                                      services)
                               agg-q (assoc (st/select-schema params HistogramParams)
                                            :agg-type :histogram)]
                           (-> (get-store entity)
                               (store/aggregate
                                 search-q
                                 agg-q
                                 identity-map)
                               (routes.common/format-agg-result :histogram aggregate-on search-q)
                               routes.common/paginated-ok)))
                    (GET "/topn" []
                         :auth-identity identity
                         :identity-map identity-map
                         :responses {200 {:schema MetricResult}}
                         :summary (format "Topn for some %s field" capitalized)
                         :query [params topn-q-params]
                         (let [aggregate-on (:aggregate-on params)
                               search-q (search-query {:date-field date-field
                                                       :params (st/select-schema params agg-search-schema)
                                                       :make-date-range-fn coerce-date-range}
                                                      services)
                               agg-q (assoc (st/select-schema params TopnParams)
                                            :agg-type :topn)]
                           (-> (get-store entity)
                               (store/aggregate
                                 search-q
                                 agg-q
                                 identity-map)
                               (routes.common/format-agg-result :topn aggregate-on search-q)
                               routes.common/paginated-ok)))
                    (GET "/cardinality" []
                         :auth-identity identity
                         :identity-map identity-map
                         :responses {200 {:schema MetricResult}}
                         :summary (format "Cardinality for some %s field" capitalized)
                         :query [params cardinality-q-params]
                         (let [aggregate-on (:aggregate-on params)
                               search-q (search-query {:date-field date-field
                                                       :params (st/select-schema params agg-search-schema)
                                                       :make-date-range-fn coerce-date-range}
                                                      services)
                               agg-q (assoc (st/select-schema params CardinalityParams)
                                            :agg-type :cardinality)]
                           (-> (get-store entity)
                               (store/aggregate
                                 search-q
                                 agg-q
                                 identity-map)
                               (routes.common/format-agg-result :cardinality aggregate-on search-q)
                               routes.common/paginated-ok)))))))
     (let [capabilities get-capabilities]
       (GET "/:id" []
            :responses {200 {:schema (s/maybe get-schema)}}
            :summary (format "Get one %s by ID" capitalized)
            :path-params [id :- s/Str]
            :query [params get-params]
            :description (capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (if-let [rec (-> (get-store entity)
                             (store/read-record
                               id
                               identity-map
                               params))]
              (-> rec
                  (with-long-id services)
                  ok)
              (not-found))))

     (let [capabilities delete-capabilities]
       (DELETE "/:id" []
               :responses {204 nil}
               :no-doc hide-delete?
               :path-params [id :- s/Str]
               :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
               :summary (format "Delete one %s" capitalized)
               :description (capabilities->description capabilities)
               :capabilities capabilities
               :auth-identity identity
               :identity-map identity-map
               (if (first
                    (flows/delete-flow
                     :services services
                     :get-fn (get-by-ids-fn identity-map)
                     :delete-fn (flow-delete-fn
                                 {:get-store get-store
                                  :entity entity
                                  :identity-map identity-map
                                  :wait_for wait_for})
                     :get-success-entities (fn [fm]
                                             (when (first (:results fm))
                                                 (:entities fm)))
                     :entity-type entity
                     :long-id-fn #(with-long-id % services)
                     :entity-ids [id]
                     :identity identity))
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
