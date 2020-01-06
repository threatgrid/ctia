(ns ctia.entity.feed.schemas
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.schemas
             [core :refer [def-acl-schema def-stored-schema]]
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
    (f/entry :name csc/ShortString)
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

(def realize-feed
  (default-realize-fn "feed" NewFeed StoredFeed))
