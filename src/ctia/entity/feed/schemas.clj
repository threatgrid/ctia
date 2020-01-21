(ns ctia.entity.feed.schemas
  (:require
   [ctia.encryption :as encryption]
   [clj-momo.lib.time :as time]
   [ctia.domain
    [access-control :refer [properties-default-tlp]]
    [entities
     :refer [contains-key?
             make-valid-time
             schema-version
             short-id->long-id]]]
   [ctia.schemas
    [core :as ctia-schemas :refer [def-acl-schema def-stored-schema TempIDs]]
    [utils :as csu]]
   [ctim.schemas.common :as csc]
   [flanders
    [core :as f]
    [spec :as f-spec]
    [utils :as fu]]
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
  (csu/optional-keys-schema StoredFeed))

(s/defschema PartialFeedList [PartialFeed])

(s/defn realize-feed :- StoredFeed
  ([new-object :- NewFeed
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]]
   (realize-feed new-object id tempids owner groups nil))
  ([new-object :- NewFeed
    id :- s/Str
    _ :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-object :- (s/maybe StoredFeed)]
   (let [long-id (short-id->long-id id)
         plain-secret (if-let [prev-secret (:secret prev-object)]
                        (encryption/decrypt-str prev-secret)
                        (str (java.util.UUID/randomUUID)))
         output (or (:output new-object)
                    (:output prev-object))
         secret
         (or (:secret prev-object)
             (encryption/encrypt-str
              plain-secret))
         feed_view_url
         (encryption/encrypt-str
          (str long-id "/view?s=" plain-secret))
         feed_view_url_csv
         (encryption/encrypt-str
          (str long-id "/view.csv?s=" plain-secret))
         now (time/now)]
     (merge new-object
            {:id id
             :type "feed"
             :owner (or (:owner prev-object) owner)
             :groups (or (:groups prev-object) groups)
             :secret secret
             :feed_view_url (if (= :judgements output)
                              feed_view_url
                              feed_view_url_csv)
             :schema_version schema-version
             :created (or (:created prev-object) now)
             :modified now
             :timestamp (or (:timestamp new-object) now)
             :tlp
             (:tlp new-object
                   (:tlp prev-object
                         (properties-default-tlp)))}
            (when (contains-key? Feed :lifetime)
              {:lifetime (:valid_time (make-valid-time
                                       (:lifetime prev-object)
                                       (:lifetime new-object)
                                       now))})))))
