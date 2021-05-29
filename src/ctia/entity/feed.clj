(ns ctia.entity.feed
  (:require
   [clojure.string :as string]
   [ctia.domain.entities
    :refer [page-with-long-id un-store un-store-page with-long-id]]
   [ctia.entity.feed.schemas
    :refer [Feed
            NewFeed
            PartialFeed
            PartialFeedList
            PartialStoredFeed
            realize-feed
            StoredFeed]]
   [ctia.entity.judgement.schemas :refer [Judgement]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :as routes.common]
   [ctia.lib.compojure.api.core :refer [DELETE GET POST PUT routes]]
   [ctia.schemas.core :refer [APIHandlerServices Observable]]
   [ctia.schemas.sorting :as sorting]
   [ctia.store
    :refer [create-record
            delete-record
            delete-search
            list-all-pages
            list-records
            query-string-count
            query-string-search
            read-record
            update-record]]
   [ctia.stores.es.mapping :as em]
   [ctia.stores.es.store :refer [def-es-store]]
   [ctim.domain.validity :as cdv]
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
  (st/merge FeedCountParams
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

(def fetch-limit 200)

(s/defn fetch-feed [id s
                    {{:keys [decrypt]} :IEncryption
                     {:keys [get-store]} :StoreService
                     :as services} :- APIHandlerServices]
  (if-let [{:keys [indicator_id
                   secret
                   output
                   lifetime
                   owner
                   groups]
            :as feed}
           (-> (get-store :feed)
               (read-record
                id
                identity-map
                {}))]
    (cond
      (not feed) :not-found
      (not (valid-lifetime? lifetime)) :not-found
      (not= s (decrypt secret)) :unauthorized
      :else (let [;; VERY IMPORTANT! inherit the identity from the Feed!
                  feed-identity
                  {:login owner
                   :groups groups
                   :capabilities #{:read-judgement
                                   :read-relationship
                                   :list-relationships}}
                  feed-results
                  (some->> (list-all-pages
                            :relationship
                            list-records
                            {:all-of {:target_ref indicator_id}}
                            feed-identity
                            {:fields [:source_ref]
                             :limit fetch-limit}
                            services)
                           (keep :source_ref)
                           (map #(-> (get-store :judgement)
                                     (read-record
                                      %
                                      feed-identity
                                      {})))
                           (remove nil?)
                           (map #(with-long-id % services)))]
              (cond-> {}
                (= :observables output)
                (assoc
                 :output :observables
                 :observables
                 (distinct (map :observable
                                feed-results)))
                (= :judgements output)
                (assoc
                 :output :judgements
                 :judgements
                 (distinct (map un-store
                                feed-results))))))
    :not-found))

(defn sorted-observable-values [data]
  (sort-by :value
           (map #(select-keys % [:value]) data)))

(defn render-headers? [output]
  (not= :observables output))

(s/defn feed-view-routes [services :- APIHandlerServices]
  (routes
   (GET "/:id/view.txt" []
     :summary "Get a Feed View as newline separated entries"
     :path-params [id :- s/Str]
     :return s/Str
     :produces #{"text/plain"}
     :responses {404 {:schema s/Str}
                 401 {:schema s/Str}}
     :query-params [s :- (describe s/Str "The feed share token")]
     (let [{:keys [output]
            :as feed} (fetch-feed id s services)]
       (case feed
         :not-found (not-found "feed not found")
         :unauthorized (unauthorized "wrong secret")
         (let [data (output feed)
               transformed (some->> (sorted-observable-values data)
                                    (map :value)
                                    (string/join \newline))]
           (ok transformed)))))
   (GET "/:id/view" []
     :summary "Get a Feed View"
     :path-params [id :- s/Str]
     :return FeedView
     :query-params [s :- (describe s/Str "The feed share token")]
     (let [feed (fetch-feed id s services)]
       (case feed
         :not-found (not-found {:error "feed not found"})
         :unauthorized (unauthorized {:error "wrong secret"})
         (ok (dissoc feed :output)))))))

(s/defn feed-routes [{{:keys [get-store]} :StoreService
                      :as services} :- APIHandlerServices]
  (routes
   (let [capabilities :create-feed]
     (POST "/" []
       :return Feed
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
       :return Feed
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
                     :get-fn #(-> (get-store :feed)
                                  (read-record
                                   %
                                   identity-map
                                   {}))
                     :realize-fn realize-feed
                     :update-fn #(-> (get-store :feed)
                                     (update-record
                                      (:id %)
                                      %
                                      identity-map
                                      (routes.common/wait_for->refresh wait_for)))
                     :long-id-fn #(with-long-id % services)
                     :entity-type :feed
                     :entity-id id
                     :identity identity
                     :entity entity-update
                     :spec :new-feed/map)
                    un-store
                    (decrypt-feed services))]
         (ok updated-rec)
         (not-found))))

   (let [capabilities :read-feed]
     (GET "/external_id/:external_id" []
       :return PartialFeedList
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
       :return PartialFeedList
       :summary "Search for a Feed using a Lucene/ES query string"
       :query [params FeedSearchParams]
       :description (routes.common/capabilities->description capabilities)
       :capabilities capabilities
       :auth-identity identity
       :identity-map identity-map
       (-> (get-store :feed)
           (query-string-search
            (routes.common/search-query :created params)
            identity-map
            (select-keys params routes.common/search-options))
           (page-with-long-id services)
           un-store-page
           (decrypt-feed-page services)
           routes.common/paginated-ok)))

   (let [capabilities :search-feed]
     (GET "/search/count" []
       :return s/Int
       :summary "Count Feed entities matching given search filters."
       :query [params FeedCountParams]
       :description (routes.common/capabilities->description capabilities)
       :capabilities capabilities
       :auth-identity identity
       :identity-map identity-map
       (ok (-> (get-store :feed)
               (query-string-count
                (routes.common/search-query :created params)
                identity-map)))))


   (let [capabilities #{:search-feed :delete-feed}]
     (DELETE "/search" []
       :capabilities capabilities
       :description (routes.common/capabilities->description capabilities)
       :return s/Int
       :summary (format "Delete Feed entities matching given Lucene/ES query string or/and field filters")
       :auth-identity identity
       :identity-map identity-map
       :query [params FeedDeleteSearchParams]
       (let [query (->> (dissoc params :wait_for :REALLY_DELETE_ALL_THESE_ENTITIES)
                        (routes.common/search-query :created))]
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
       :return (s/maybe PartialFeed)
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
       :no-doc false
       :path-params [id :- s/Str]
       :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
       :summary "Deletes a Feed"
       :description (routes.common/capabilities->description capabilities)
       :capabilities capabilities
       :auth-identity identity
       :identity-map identity-map
       (if (flows/delete-flow
            :services services
            :get-fn #(-> (get-store :feed)
                         (read-record
                          %
                          identity-map
                          {}))
            :delete-fn #(-> (get-store :feed)
                            (delete-record
                             %
                             identity-map
                             (routes.common/wait_for->refresh wait_for)))
            :entity-type :feed
            :long-id-fn #(with-long-id % services)
            :entity-id id
            :identity identity)
         (no-content)
         (not-found))))))

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
   :sort-fields           sort-restricted-feed-fields
   :searchable-fields     (routes.common/searchable-fields
                           feed-entity)})
