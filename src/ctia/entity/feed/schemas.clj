(ns ctia.entity.feed.schemas
  (:require
   [clj-momo.lib.time :as time]
   [ctia.auth :as auth]
   [ctia.domain.access-control :refer [properties-default-tlp]]
   [ctia.domain.entities :refer [schema-version short-id->long-id]]
   [ctia.graphql.delayed :as delayed]
   [ctia.http.routes.common :refer [PagingParams]]
   [ctia.schemas.core :as ctia-schemas :refer [def-acl-schema
                                               def-stored-schema GraphQLRuntimeContext
                                               RealizeFnResult TempIDs]]
   [ctim.schemas.common :as csc]
   [flanders.core :as f]
   [flanders.spec :as f-spec]
   [flanders.utils :as fu]
   [ring.swagger.schema :refer [describe]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def type-identifier "feed")

(f/def-eq FeedIdentifier type-identifier)

(f/def-enum-type FeedType
  #{:indicator}
  :default :indicator
  :description "The feed data gathering process, currently `indicator` only")

(f/def-enum-type OutputType
  #{:judgements :observables}
  :default :observables
  :description "The Feed output rendering type")

(f/def-entity-type FeedMapType
  "Schema for a Feed"
  (concat
   csc/base-entity-entries
   (f/required-entries
    (f/entry :feed_type FeedType)
    (f/entry :output OutputType))
   (f/optional-entries
    (f/entry :title csc/ShortString)
    (f/entry :secret f/any-str)
    (f/entry :feed_view_url f/any-str)
    (f/entry :indicator_id f/any-str)
    (f/entry :lifetime csc/ValidTime))))

(f/def-entity-type NewFeedMapType
  "Schema for a Feed"
  (:entries FeedMapType)
  csc/base-new-entity-entries
  (f/optional-entries
   (f/entry :type FeedIdentifier)))

(def-acl-schema Feed FeedMapType "feed")
(def-acl-schema NewFeed NewFeedMapType "new-feed")

(def-stored-schema StoredFeed Feed)
(def-acl-schema PartialFeed
  (fu/optionalize-all FeedMapType)
  "partial-feed")

(f-spec/->spec FeedMapType "feed")
(f-spec/->spec NewFeedMapType "new-feed")

(s/defschema PartialStoredFeed
  (st/optional-keys-schema StoredFeed))

(s/defschema PartialFeedList [PartialFeed])

(s/defn realize-feed :- (RealizeFnResult StoredFeed)
  ([new-object :- NewFeed
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    ident-map :- auth/IdentityMap]
   (realize-feed new-object id tempids ident-map nil))
  ([new-object :- NewFeed
    id :- s/Str
    _ :- (s/maybe TempIDs)
    {:keys [login groups client-id]} :- auth/IdentityMap
    prev-object :- (s/maybe StoredFeed)]
  (delayed/fn :- StoredFeed
   [{{{:keys [get-in-config]} :ConfigService
      {:keys [encrypt decrypt]} :IEncryption
      :as services}
     :services} :- GraphQLRuntimeContext]
   (let [long-id (short-id->long-id id services)
         plain-secret (if-let [prev-secret (:secret prev-object)]
                        (decrypt prev-secret)
                        (str (java.util.UUID/randomUUID)))
         output (or (:output new-object)
                    (:output prev-object))
         secret
         (or (:secret prev-object)
             (encrypt
              plain-secret))
         feed_view_url
         (encrypt
          (str long-id "/view?s=" plain-secret))
         feed_view_url_txt
         (encrypt
          (str long-id "/view.txt?s=" plain-secret))
         now (time/now)
         base-stored {:id id
                      :type "feed"
                      :owner (or (:owner prev-object) login)
                      :groups (or (:groups prev-object) groups)
                      :secret secret
                      :feed_view_url (if (= :judgements output)
                                       feed_view_url
                                       feed_view_url_txt)
                      :schema_version schema-version
                      :created (or (:created prev-object) now)
                      :modified now
                      :timestamp (or (:timestamp new-object) now)
                      :tlp
                      (:tlp new-object
                            (:tlp prev-object
                                  (properties-default-tlp get-in-config)))}]
     (cond-> (into new-object base-stored)
       client-id (assoc :client_id client-id))))))

(s/defschema FeedViewQueryParams
  (-> PagingParams
      (st/assoc :s (describe s/Str "The feed share token"))
      (st/dissoc :sort_by :sort_order :offset)
      (st/assoc (s/optional-key :limit) (s/constrained Long #(<= 1 % 10000) 'less-than-10000?))))
