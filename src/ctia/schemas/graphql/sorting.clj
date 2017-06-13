(ns ctia.schemas.graphql.sorting
  (:require [ctia.schemas.graphql.helpers :as g]
            [clojure.string :as string]
            [schema-tools.core :as st]
            [schema.core :as s]
            [clojure.string :as str])
  (:import [graphql.schema GraphQLInputObjectType]))

(s/defschema OrderByOptions
  {s/Str s/Any})

(s/defschema SortingParams
  {:sort_by s/Str})

(s/defschema ConnectionParams
  (st/merge
   {s/Keyword s/Any}
   (st/optional-keys
    {:orderBy [{:field s/Str
                :direction (s/enum "ASC" "DESC")}]})))

(def OrderDirection
  (g/enum
   "OrderDirection"
   (str "Possible directions in which to order a list of items "
        "when provided an orderBy argument.")
   #{"asc" "desc"}))

(s/defn order-by-type :- GraphQLInputObjectType
  "Creates an Ordering input object with two arguments, the
   field in which to order items by and a direction in wich
   to order items by the specified field"
  [type-name :- s/Str
   items-name :- s/Str
   order-by-options :- OrderByOptions]
  (let [order-field
        (g/enum
         (str type-name "Field")
         (format "Ordering options for %s returned from the connection"
                 items-name)
         order-by-options)]
    (g/new-input-object
     type-name
     (format "Ways in which lists of %s can be ordered upon return."
             items-name)
     (g/non-nulls
      {:field {:type order-field
               :description (format "The field in which to order %s by."
                                    items-name)}
       :direction {:type OrderDirection
                   :description
                   (format (str "The direction in which to order "
                                "%s by the specified field.")
                           items-name)}}))))

(s/defn order-by-arg :- GraphQLInputObjectType
  [type-name :- s/Str
   items-name :- s/Str
   order-by-options :- OrderByOptions]
  (let [t (order-by-type type-name items-name order-by-options)]
    {:orderBy {:type (-> t g/non-null g/list-type)
               :description (format (str "Allows specifying the order in which "
                                         "%s are returned.")
                                    items-name)}}))

(s/defn connection-params->sorting-params :- (s/maybe SortingParams)
  "Converts connection params to sorting params"
  [connection-params :- ConnectionParams]
  (some->> (:orderBy connection-params)
           (map (fn [{:keys [field direction]}]
                  (str field ":" direction)))
           (string/join ",")
           (assoc {} :sort_by)))

(defn sorting-kw->enum-name
  "Ex :valid_time.start_time -> VALID_TIME_START_TIME"
  [kw]
  (some-> kw
          name
          str/upper-case
          (str/replace #"\." "_")))
