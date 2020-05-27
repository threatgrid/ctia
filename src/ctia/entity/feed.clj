(ns ctia.entity.feed
  (:require
   [clojure.string :as string]
   [ctia.encryption :as encryption]
   [ctia.http.routes.crud :as crud]
   [compojure.api.sweet :refer [DELETE GET POST PUT routes]]
   [ctia.domain.entities
    :refer
    [page-with-long-id un-store un-store-page with-long-id]]
   [ctia.entity.feed.schemas
    :refer
    [Feed
     NewFeed
     PartialFeed
     PartialFeedList
     PartialStoredFeed
     realize-feed
     StoredFeed]]
   [ctia.entity.judgement.schemas :refer [Judgement]]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common
    :refer
    [BaseEntityFilterParams
     created
     filter-map-search-options
     paginated-ok
     PagingParams
     search-options
     search-query
     wait_for->refresh]]
   [ctia.schemas
    [core :refer [Observable]]
    [sorting :as sorting]]
   [ctia.store :refer :all]
   [ctia.stores.es
    [mapping :as em]
    [store :refer [def-es-store]]]
   [ctim.domain.validity :as cdv]
   [ring.swagger.schema :refer [describe]]
   [ring.util.http-response :refer [no-content
                                    not-found
                                    ok
                                    unauthorized]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def feed-mapping
  {"feed"
   {:dynamic false
    :properties
    (merge em/base-entity-mapping
           em/stored-entity-mapping
           {:title em/all_text
            :lifetime em/valid-time
            :output em/token
            :secret em/token
            :feed_view_url em/token
            :indicator_id em/token})}})

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
   BaseEntityFilterParams
   FeedFieldsParam
   (st/optional-keys
    {:query s/Str})))

(s/defschema FeedSearchParams
  (st/merge
   FeedCountParams
   PagingParams
   {(s/optional-key :sort_by) feed-sort-fields}))

(def FeedGetParams FeedFieldsParam)

(s/defschema FeedListQueryParams
  (st/merge
   PagingParams
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

(defn decrypt-feed
  [{:keys [secret
           feed_view_url]
    :as feed}]
  (cond-> feed
    secret (assoc :secret
                  (encryption/decrypt-str secret))
    feed_view_url (assoc :feed_view_url
                         (encryption/decrypt-str
                          feed_view_url))))

(defn decrypt-feed-page [feed-page]
  (update feed-page :data
          (fn [feeds]
            (map decrypt-feed feeds))))

(defn fetch-feed [id s]
  (if-let [{:keys [indicator_id
                   secret
                   output
                   lifetime
                   owner
                   groups]
            :as feed}
           (read-store :feed
                       read-record
                       id
                       identity-map
                       {})]
    (cond
      (not feed) :not-found
      (not (valid-lifetime? lifetime)) :not-found
      (not= s (encryption/decrypt-str secret)) :unauthorized
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
                            {:fields [:source_ref]})
                           (keep :source_ref)
                           (map #(read-store :judgement
                                             read-record
                                             %
                                             feed-identity
                                             {}))
                           (remove nil?)
                           (map with-long-id))]
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

(def feed-view-routes
  (routes
   (GET "/:id/view.txt" []
     :summary "Get a Feed View as newline separated entries"
     :path-params [id :- s/Str]
     :return s/Str
     :coercion (constantly nil)
     :produces #{"text/plain"}
     :query-params [s :- (describe s/Str "The feed share token")]
     (let [{:keys [output]
            :as feed} (fetch-feed id s)]
       (case feed
         :not-found (not-found "feed not found")
         :unauthorized (unauthorized "wrong secret")
         (let [data (output feed)
               transformed (some->> (sorted-observable-values data)
                                    (map :value)
                                    (string/join \newline))]
           (into (ok transformed)
                 {:compojure.api.meta/serializable? false
                  :headers {"Content-Type" "text/plain; charset=utf8"}})))))
   (GET "/:id/view" []
     :summary "Get a Feed View"
     :path-params [id :- s/Str]
     :return FeedView
     :query-params [s :- (describe s/Str "The feed share token")]
     (let [feed (fetch-feed id s)]
       (case feed
         :not-found (not-found "feed not found")
         :unauthorized (unauthorized "wrong secret")
         (ok (dissoc feed :output)))))))

(def feed-routes
  (routes
   (POST "/" []
     :return Feed
     :query-params [{wait_for :- (describe s/Bool "wait for entity to be available for search") nil}]
     :body [new-entity NewFeed {:description "a new Feed"}]
     :summary "Adds a new Feed"
     :capabilities :create-feed
     :auth-identity identity
     :identity-map identity-map
     (-> (flows/create-flow
          :entity-type :feed
          :realize-fn realize-feed
          :store-fn #(write-store :feed
                                  create-record
                                  %
                                  identity-map
                                  (wait_for->refresh wait_for))
          :long-id-fn with-long-id
          :entity-type :feed
          :identity identity
          :entities [new-entity]
          :spec :new-feed/map)
         first
         un-store
         decrypt-feed
         created))
   (PUT "/:id" []
     :return Feed
     :body [entity-update NewFeed {:description "an updated Feed"}]
     :summary "Updates a Feed"
     :query-params [{wait_for :- (describe s/Bool "wait for updated entity to be available for search") nil}]
     :path-params [id :- s/Str]
     :capabilities :create-feed
     :auth-identity identity
     :identity-map identity-map
     (if-let [updated-rec
              (-> (flows/update-flow
                   :get-fn #(read-store :feed
                                        read-record
                                        %
                                        identity-map
                                        {})
                   :realize-fn realize-feed
                   :update-fn #(write-store :feed
                                            update-record
                                            (:id %)
                                            %
                                            identity-map
                                            (wait_for->refresh wait_for))
                   :long-id-fn with-long-id
                   :entity-type :feed
                   :entity-id id
                   :identity identity
                   :entity entity-update
                   :spec :new-feed/map)
                  un-store
                  decrypt-feed)]
       (ok updated-rec)
       (not-found)))

   (GET "/external_id/:external_id" []
     :return PartialFeedList
     :query [q FeedByExternalIdQueryParams]
     :path-params [external_id :- s/Str]
     :summary "List Feeds by external_id"
     :capabilities :read-feed
     :auth-identity identity
     :identity-map identity-map
     (-> (read-store :feed
                     list-records
                     {:all-of {:external_ids external_id}}
                     identity-map
                     q)
         page-with-long-id
         un-store-page
         decrypt-feed-page
         paginated-ok))

   (GET "/search" []
     :return PartialFeedList
     :summary "Search for a Feed using a Lucene/ES query string"
     :query [params FeedSearchParams]
     :capabilities :search-feed
     :auth-identity identity
     :identity-map identity-map
     (-> (read-store
          :feed
          query-string-search
          (search-query :created params)
          identity-map
          (select-keys params search-options))
         page-with-long-id
         un-store-page
         decrypt-feed-page
         paginated-ok))

   (GET "/search/count" []
        :return s/Int
        :summary "Count Feed entities matching given search filters."
        :query [params FeedCountParams]
        :capabilities :search-feed
        :auth-identity identity
        :identity-map identity-map
        (ok (read-store
             :feed
             query-string-count
             (search-query :created params)
             identity-map)))

   (GET "/:id" []
     :return (s/maybe PartialFeed)
     :summary "Gets a Feed by ID"
     :path-params [id :- s/Str]
     :query [params FeedGetParams]
     :capabilities :read-feed
     :auth-identity identity
     :identity-map identity-map
     (if-let [rec (read-store :feed
                              read-record
                              id
                              identity-map
                              params)]
       (-> rec
           with-long-id
           un-store
           decrypt-feed
           ok)
       (not-found)))

   (DELETE "/:id" []
     :no-doc false
     :path-params [id :- s/Str]
     :query-params [{wait_for :- (describe s/Bool "wait for deleted entity to no more be available for search") nil}]
     :summary "Deletes a Feed"
     :capabilities :delete-feed
     :auth-identity identity
     :identity-map identity-map
     (if (flows/delete-flow
          :get-fn #(read-store :feed
                               read-record
                               %
                               identity-map
                               {})
          :delete-fn #(write-store :feed
                                   delete-record
                                   %
                                   identity-map
                                   (wait_for->refresh wait_for))
          :entity-type :feed
          :long-id-fn with-long-id
          :entity-id id
          :identity identity)
       (no-content)
       (not-found)))))

(def capabilities
  #{:create-feed
    :read-feed
    :delete-feed
    :search-feed})

(def feed-entity
  {:route-context "/feed"
   :tags ["Feed"]
   :entity :feed
   :plural :feeds
   :new-spec :new-feed/map
   :schema Feed
   :partial-schema PartialFeed
   :partial-list-schema PartialFeedList
   :new-schema NewFeed
   :no-bulk? true
   :stored-schema StoredFeed
   :partial-stored-schema PartialStoredFeed
   :realize-fn realize-feed
   :es-store ->FeedStore
   :es-mapping feed-mapping
   :routes feed-routes
   :capabilities capabilities})
