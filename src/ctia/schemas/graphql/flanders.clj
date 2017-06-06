(ns ctia.schemas.graphql.flanders
  (:require [clj-momo.lib.time :refer [format-date-time]]
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ctia.schemas.graphql.helpers :as g])
  (:import [flanders.types
            AnythingType BooleanType EitherType InstType IntegerType KeywordType
            MapEntry MapType NumberType SequenceOfType SetOfType StringType]
           graphql.Scalars))

(defprotocol GraphQLNode
  (->graphql' [node f refs]))

(defn conditional-type-resolver
  "Return a GraphQL type resolver. Checks the first typeX where predX returns
  true on the value."
  [preds types]
  (fn [obj _ _]
    (some (fn [[pred val]]
            (when (pred obj) val))
          (map vector preds types))))

(extend-protocol GraphQLNode
  ;; Branches
  EitherType
  (->graphql' [{:keys [choices tests key]} f _]
    (let [choice-types (map f choices)]
      (log/debug "EitherType choices" choice-types)
      {:type (g/new-union
              (str/capitalize (name key))
              ""
              (conditional-type-resolver tests choice-types)
              (map :type choice-types))}))

  MapEntry
  (->graphql' [{:keys [key type required?] :as entry} f _]
    (let [kw (f (assoc key
                       :key? true
                       :description (some :description [key entry])))
          value ((if required?
                   #(update % :type g/non-null)
                   identity)
                 (f (assoc type :key kw)))]
      [kw value]))

  MapType
  (->graphql' [{:keys [name description entries] :as node} f refs]
    (let [root? (:root (meta node))
          resolved-type (get refs name)
          dynamic-map? (nil? name)
          fields (reduce (fn [m [k v]]
                           (assoc m k v))
                         {}
                         (map f entries))]
      (log/debug "MapType" name description entries fields)
      (cond root? {:fields fields
                   :name name
                   :description description}
            ;; Get the MapType from the provided references
            (some? resolved-type) {:type resolved-type}
            ;; Dynamic Object Types are not supported in GraphQL
            dynamic-map? {:type Scalars/GraphQLString}
            :else {:type (g/new-object name
                                       description
                                       []
                                       fields)
                   :description description})))

  SequenceOfType
  (->graphql' [{:keys [type]} f _]
    (update (f type) :type g/list-type))

  SetOfType
  (->graphql' [{:keys [type]} f _]
    (update (f type) :type g/list-type))

  ;; Leaves
  AnythingType
  (->graphql' [{:keys [description]} _ _]
    {:type Scalars/GraphQLString
     :description description})

  BooleanType
  (->graphql' [{:keys [description]} _ _]
    {:type Scalars/GraphQLBoolean
     :description description})

  InstType
  (->graphql' [{:keys [description key]} _ _]
    {:type Scalars/GraphQLString
     :description description
     :resolve (g/map-resolver key format-date-time)})

  IntegerType
  (->graphql' [{:keys [description]} _ _]
    {:type Scalars/GraphQLInt
     :description description})

  KeywordType
  (->graphql' [{:keys [key? values]} _ _]
    (if key?
      (first values)
      nil))

  NumberType
  (->graphql' [{:keys [description]} _ _]
    {:type Scalars/GraphQLString
     :description description})

  StringType
  (->graphql' [{:keys [open? name description key values]} _ _]
    (log/debug "StringType" name key values)
    (let [id? (= key :id)]
      (merge
       {:type (match [id?  open? (seq values)]
                     [true _     _  ] Scalars/GraphQLID
                     [_    true  _  ] Scalars/GraphQLString
                     [_    _     nil] Scalars/GraphQLString
                     :else      (do
                                  (log/debugf "Enum %s, description: %s, values: %s"
                                              name
                                              description
                                              values)
                                  (g/enum name description values)))}
       (when description
         {:description description})))))

(defn ->graphql
  ([node] (->graphql node {}))
  ([node refs]
   (letfn [(recursive-graphql [node]
             (->graphql' node recursive-graphql refs))]
     (recursive-graphql (with-meta node {:root true})))))
