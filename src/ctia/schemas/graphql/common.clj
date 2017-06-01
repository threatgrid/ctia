(ns ctia.schemas.graphql.common
  (:require [clj-momo.lib.time :refer [format-date-time]]
            [ctia.schemas.graphql.helpers :as g]
            [ctim.schemas.common :as c]
            [ctim.schemas.vocabularies :as voc])
  (:import graphql.Scalars))

(def TLP
  (g/enum "TLP"
          "TLP stands for [Traffic Light Protocol]"
          #{"red" "amber" "green" "white"}))

(def DispositionNameType
  (g/enum "DispositionName"
          "String verdict identifiers"
          (vals c/disposition-map)))

(def DispositionNumberType
  Scalars/GraphQLInt)

(def HighMedLow
  (g/enum "HighMedLow"
          ""
          voc/high-med-low))

(def ValidTime
  (g/new-object
   "ValidTime"
   "Period of time when a cyber observation is valid."
   []
   {:start_time
    {:type Scalars/GraphQLString
     :description (str "If not present, the valid time position of the "
                       "indicator does not have an upper bound")
     :resolve (g/map-resolver :start_time format-date-time)}
    :end_time
    {:type Scalars/GraphQLString
     :description (str "If end_time is not present, then the valid time "
                       "position of the object does not have an upper bound.")
     :resolve (g/map-resolver :end_time format-date-time)}}))

(def describable-entity-fields
  {:title {:type Scalars/GraphQLString}
   :description {:type Scalars/GraphQLString}
   :short_description {:type Scalars/GraphQLString}})

(def sourced-object-fields
  {:source {:type (g/non-null Scalars/GraphQLString)}
   :source_uri {:type Scalars/GraphQLString}})

(def sourcable-object-fields
  {:source {:type Scalars/GraphQLString}
   :source_uri {:type Scalars/GraphQLString}})

(def base-entity-fields
  (into
   (g/non-nulls
    {:id {:type Scalars/GraphQLID}
     :type {:type Scalars/GraphQLString}
     :schema_version {:type Scalars/GraphQLString
                      :description "CTIM schema version for this entity"}})
   {:revision {:type Scalars/GraphQLInt}
    :external_ids {:type (g/list-type Scalars/GraphQLString)}
    :timestamp {:type Scalars/GraphQLString}
    :language {:type Scalars/GraphQLString}
    :tlp {:type TLP}}))

(def lucene-query-arguments
  {:query {:type Scalars/GraphQLString
           :description (str "A Lucene query string, will only "
                             "return Relationships matching it.")
           :default "*"}})
