(ns ctia.entity.feed
  (:require
   [clojure.string :as string]
   [ctia.domain.entities
    :refer [page-with-long-id un-store un-store-page with-long-id]]
   [ctia.entity.feed.schemas
    :refer [Feed FeedViewQueryParams NewFeed PartialFeed PartialFeedList
            PartialStoredFeed realize-feed StoredFeed]]
   [ctia.entity.judgement.schemas :refer [Judgement]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :as routes.common]
   [ctia.http.routes.crud :as routes.crud]
   [ctia.lib.compojure.api.core :refer [DELETE GET POST PUT routes]]
   [ctia.schemas.core :refer [APIHandlerServices Observable]]
   [ctia.schemas.sorting :as sorting]
   [ctia.store
    :refer [create-record delete-search list-records query-string-count
            query-string-search read-record]
    :as store]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store] :as es-store]
   [ctim.domain.validity :as cdv]
   [ductile.pagination :as pagination]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response
    :refer [forbidden no-content not-found ok unauthorized]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def feed-mapping
  {"feed"
   {:dynamic false
    :properties
    (merge em/base-entity-mapping
           em/stored-entity-mapping
           {:title         em/text
            :lifetime      em/valid-time
            :output        em/token
            :secret        em/token
            :feed_view_url em/token
            :indicator_id  em/token})}})

(def-es-store FeedStore :feed StoredFeed PartialStoredFeed)

(def feed-fields
  (concat
   sorting/base-entity-sort-fields
   [:title
    :output
    :indicator_id]))

(def sort-restricted-feed-fields
  (remove #{:title} feed-fields))

(def feed-sort-fields
  (apply s/enum sort-restricted-feed-fields))

(s/defschema FeedFieldsParam
  {(s/optional-key :fields) [feed-sort-fields]})

(s/defschema FeedCountParams
  (st/merge
   routes.common/BaseEntityFilterParams
   FeedFieldsParam
   (st/optional-keys
    {:query s/Str})))

(s/defschema FeedSearchParams
  (st/merge
   FeedCountParams
   routes.common/PagingParams
   routes.common/SearchableEntityParams
   {(s/optional-key :sort_by) feed-sort-fields}))

(s/defschema FeedDeleteSearchParams
  (routes.crud/add-flags-to-delete-search-query-params FeedCountParams))

(def FeedGetParams FeedFieldsParam)

(s/defschema FeedListQueryParams
  (st/merge
   routes.common/PagingParams
   FeedFieldsParam
   {(s/optional-key :sort_by) feed-sort-fields}))

(s/defschema FeedByExternalIdQueryParams
  FeedListQueryParams)

(s/defschema FeedView
  {(s/optional-key :judgements) [Judgement]
   (s/optional-key :observables) [Observable]})

(def identity-map
  {:authorized-anonymous true})

(defn valid-lifetime? [lifetime]
  (cdv/valid-now?
   (java.util.Date.)
   {:valid_time lifetime}))

(s/defn decrypt-feed
  [{:keys [secret
           feed_view_url]
    :as feed}
   {{:keys [decrypt]} :IEncryption
    :as _services_} :- APIHandlerServices]
  (cond-> feed
    secret (assoc :secret
                  (decrypt secret))
    feed_view_url (assoc :feed_view_url
                         (decrypt
                          feed_view_url))))

(s/defn decrypt-feed-page [feed-page services :- APIHandlerServices]
  (update feed-page :data
          (fn [feeds]
            (map #(decrypt-feed % services) feeds))))

(s/defn fetch-feed
  ([id share-token services]
   (fetch-feed id share-token {} services))
  ([id share-token
    {:keys [no-pagination] :as page-params}
    {{:keys [decrypt]} :IEncryption
     {:keys [get-store]} :StoreService
     :as services} :- APIHandlerServices]
   (let [{:keys [indicator_id
                 secret
                 output
                 lifetime
                 owner
                 groups]
          :as feed}
         (read-record (get-store :feed) id identity-map {})]
     (cond
       (not feed)
       :not-found

       (not (valid-lifetime? lifetime))
       :not-found

       (not= share-token (decrypt secret))
       :unauthorized

       :else
       (let [ ;; VERY IMPORTANT! inherit the identity from the Feed!
             feed-identity {:login owner
                            :groups groups
                            :capabilities #{:read-judgement
                                            :read-relationship
                                            :list-relationships}}
             relationship-store (get-store :relationship)
             relationship-max-result-window (get-in relationship-store [:state :config :settings :max_result_window]
                                                    pagination/max-result-window)
             judgement-store (get-store :judgement)
             judgement-max-result-window (get-in judgement-store [:state :config :settings :max_result_window]
                                                 pagination/max-result-window)
             read-judgements #(store/read-records judgement-store % feed-identity {})
             get-paginated-response (if no-pagination
                                      (fn [response]
                                        (transduce
                                         (map #(select-keys % [:data]))
                                         (partial merge-with concat)
                                         response))
                                      first)
             now (java.util.Date.)
             {relationships :data
              {next-page :next} :paging}
             (get-paginated-response
              (store/paginate
               relationship-store
               #(store/list-records %1 {:all-of {:target_ref indicator_id}} feed-identity %2)
               (merge {:fields [:source_ref]
                       :limit relationship-max-result-window
                       :sort_by "timestamp:desc,id"}
                      (select-keys page-params [:search_after :limit]))))
             feed-results (sequence
                           (comp (keep :source_ref)
                                 (distinct)
                                 (partition-all judgement-max-result-window)
                                 (mapcat read-judgements)
                                 (remove nil?)
                                 (remove #(not (cdv/valid-now? now %)))
                                 (map #(with-long-id % services))
                                 (map un-store))
                           relationships)]
         (cond-> {}
           (= :observables output)
           (assoc :output :observables
                  :observables (distinct (map :observable feed-results)))

           (= :judgements output)
           (assoc :output :judgements
                  :judgements feed-results)

           (seq next-page)
           (assoc :next-page next-page)))))))

(s/defn feed-view-routes [services :- APIHandlerServices]
  (routes
   (GET "/:id/view.txt" []
     :summary "Get a Feed View as newline separated entries"
     :path-params [id :- s/Str]
     :produces #{"text/plain"}
     :responses {200 {:schema s/Str}
                 404 {:schema s/Str}
                 401 {:schema s/Str}}
     :query [params FeedViewQueryParams]
     (let [search_after (:search_after params)
           limit (:limit params)
           page-params (cond-> {}
                         search_after
                         (assoc :search_after search_after)

                         limit
                         (assoc :limit limit)

                         ;; Defaults to `no-pagination`
                         (every? nil? [search_after limit])
                         (assoc :no-pagination true))
           {:keys [output next-page]
            :as feed} (fetch-feed id (:s params) page-params services)]
       (case feed
         :not-found (not-found "feed not found")
         :unauthorized (unauthorized "wrong secret")
         (let [data (output feed)
               transformed (string/join \newline (sort (map :value data)))]
           (if next-page
             (routes.common/paginated-ok {:data transformed
                                          :paging next-page})
             (ok transformed))))))

   (GET "/:id/view" []
     :summary "Get a Feed View"
     :path-params [id :- s/Str]
     :responses {200 {:schema FeedView}}
     :query [params FeedViewQueryParams]
     (let [search_after (:search_after params)
           limit (:limit params)
           page-params (cond-> {}
                         search_after
                         (assoc :search_after search_after)

                         limit
                         (assoc :limit limit)

                         ;; Defaults to `no-pagination`
                         (every? nil? [search_after limit])
                         (assoc :no-pagination true))
           {:keys [next-page]
            :as feed} (fetch-feed id (:s params) page-params services)]
       (case feed
         :not-found (not-found {:error "feed not found"})
         :unauthorized (unauthorized {:error "wrong secret"})
         (if next-page
           (routes.common/paginated-ok {:data (dissoc feed :output :next-page)
                                        :paging next-page})
           (ok (dissoc feed :output))))))))

(s/defn feed-routes [{{:keys [get-store]} :StoreService
                      :as services} :- APIHandlerServices]
  (let [get-by-ids-fn (fn [identity-map]
                        (routes.crud/flow-get-by-ids-fn
                         {:get-store get-store
                          :entity :feed
                          :identity-map identity-map}))
        update-fn (fn [identity-map wait_for]
                    (routes.crud/flow-update-fn
                     {:get-store get-store
                      :entity :feed
                      :identity-map identity-map
                      :wait_for (routes.common/wait_for->refresh wait_for)}))]
    (routes
     (let [capabilities :create-feed]
       (POST "/" []
             :responses {201 {:schema Feed}}
             :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
             :body [new-entity NewFeed {:description "a new Feed"}]
             :summary "Adds a new Feed"
             :description (routes.common/capabilities->description capabilities)
             :capabilities capabilities
             :auth-identity identity
             :identity-map identity-map
             (-> (flows/create-flow
                  :services services
                  :entity-type :feed
                  :realize-fn realize-feed
                  :store-fn #(-> (get-store :feed)
                                 (create-record
                                  %
                                  identity-map
                                  (routes.common/wait_for->refresh wait_for)))
                  :long-id-fn #(with-long-id % services)
                  :entity-type :feed
                  :identity identity
                  :entities [new-entity]
                  :spec :new-feed/map)
                 first
                 un-store
                 (decrypt-feed services)
                 routes.common/created)))

     (let [capabilities :create-feed]
       (PUT "/:id" []
            :responses {200 {:schema Feed}}
            :body [entity-update NewFeed {:description "an updated Feed"}]
            :summary "Updates a Feed"
            :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
            :path-params [id :- s/Str]
            :description (routes.common/capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (if-let [updated-rec
                     (-> (flows/update-flow
                          :services services
                          :get-fn (get-by-ids-fn identity-map)
                          :realize-fn realize-feed
                          :update-fn (update-fn identity-map wait_for)
                          :long-id-fn #(with-long-id % services)
                          :entity-type :feed
                          :identity identity
                          :entities [(assoc entity-update :id id)]
                          :spec :new-feed/map)
                         first
                         un-store
                         (decrypt-feed services))]
              (ok updated-rec)
              (not-found))))

     (let [capabilities :read-feed]
       (GET "/external_id/:external_id" []
            :responses {200 {:schema PartialFeedList}}
            :query [q FeedByExternalIdQueryParams]
            :path-params [external_id :- s/Str]
            :summary "List Feeds by external_id"
            :description (routes.common/capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (-> (get-store :feed)
                (list-records
                 {:all-of {:external_ids external_id}}
                 identity-map
                 q)
                (page-with-long-id services)
                un-store-page
                (decrypt-feed-page services)
                routes.common/paginated-ok)))

     (let [capabilities :search-feed]
       (GET "/search" []
            :responses {200 {:schema PartialFeedList}}
            :summary "Search for a Feed using a Lucene/ES query string"
            :query [params FeedSearchParams]
            :description (routes.common/capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (-> (get-store :feed)
                (query-string-search
                  {:search-query (routes.common/search-query {:date-field :created
                                                              :params params})
                   :ident identity-map
                   :params (select-keys params routes.common/search-options)})
                (page-with-long-id services)
                un-store-page
                (decrypt-feed-page services)
                routes.common/paginated-ok)))

     (let [capabilities :search-feed]
       (GET "/search/count" []
            :responses {200 {:schema s/Int}}
            :summary "Count Feed entities matching given search filters."
            :query [params FeedCountParams]
            :description (routes.common/capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (ok (-> (get-store :feed)
                    (query-string-count
                     (routes.common/search-query {:date-field :created
                                                  :params params})
                     identity-map)))))

     (let [capabilities #{:search-feed :delete-feed}]
       (DELETE "/search" []
               :capabilities capabilities
               :description (routes.common/capabilities->description capabilities)
               :responses {200 {:schema s/Int}}
               :summary (format "Delete Feed entities matching given Lucene/ES query string or/and field filters")
               :auth-identity identity
               :identity-map identity-map
               :query [params FeedDeleteSearchParams]
               (let [query (routes.common/search-query {:date-field :created
                                                        :params (dissoc params :wait_for :REALLY_DELETE_ALL_THESE_ENTITIES)})]
                 (if (empty? query)
                   (forbidden {:error "you must provide at least one of from, to, query or any field filter."})
                   (ok
                    (if (:REALLY_DELETE_ALL_THESE_ENTITIES params)
                      (-> (get-store :feed)
                          (delete-search
                           query
                           identity-map
                           (routes.common/wait_for->refresh (:wait_for params))))
                      (-> (get-store :feed)
                          (query-string-count
                           query
                           identity-map))))))))

     (let [capabilities :read-feed]
       (GET "/:id" []
            :responses {200 {:schema (s/maybe PartialFeed)}}
            :summary "Gets a Feed by ID"
            :path-params [id :- s/Str]
            :query [params FeedGetParams]
            :description (routes.common/capabilities->description capabilities)
            :capabilities capabilities
            :auth-identity identity
            :identity-map identity-map
            (if-let [rec (-> (get-store :feed)
                             (read-record
                              id
                              identity-map
                              params))]
              (-> rec
                  (with-long-id services)
                  un-store
                  (decrypt-feed services)
                  ok)
              (not-found))))

     (let [capabilities :delete-feed]
       (DELETE "/:id" []
               :responses {204 nil}
               :no-doc false
               :path-params [id :- s/Str]
               :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
               :summary "Deletes a Feed"
               :description (routes.common/capabilities->description capabilities)
               :capabilities capabilities
               :auth-identity identity
               :identity-map identity-map
               (if (first
                    (flows/delete-flow
                     :services services
                     :get-fn (get-by-ids-fn identity-map)
                     :delete-fn (routes.crud/flow-delete-fn
                                 {:get-store get-store
                                  :entity :feed
                                  :identity-map identity-map
                                  :wait_for wait_for})
                     :entity-type :feed
                     :get-success-entities (fn [fm]
                                             (when (first (:results fm))
                                               (:entities fm)))
                     :long-id-fn #(with-long-id % services)
                     :entity-ids [id]
                     :identity identity))
                 (no-content)
                 (not-found)))))))

(def capabilities
  #{:create-feed
    :read-feed
    :delete-feed
    :search-feed})

(def feed-entity
  {:route-context         "/feed"
   :tags                  ["Feed"]
   :entity                :feed
   :plural                :feeds
   :new-spec              :new-feed/map
   :schema                Feed
   :partial-schema        PartialFeed
   :partial-list-schema   PartialFeedList
   :new-schema            NewFeed
   :no-bulk?              true
   :stored-schema         StoredFeed
   :partial-stored-schema PartialStoredFeed
   :realize-fn            realize-feed
   :es-store              ->FeedStore
   :es-mapping            feed-mapping
   :services->routes      (routes.common/reloadable-function
                           feed-routes)
   :capabilities          capabilities
   :fields                sort-restricted-feed-fields
   :sort-fields           sort-restricted-feed-fields})
