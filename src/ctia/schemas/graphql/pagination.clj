(ns ctia.schemas.graphql.pagination
  (:require [clojure.tools.logging :as log]
            [clojure.data.codec.base64 :as b64]
            [clojure.string :as str]
            [ctia.schemas.graphql.helpers :as g]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import graphql.Scalars))

(def PageInfo
  (g/new-object
   "PageInfo"
   ""
   []
   {:hasNextPage {:type (g/non-null Scalars/GraphQLBoolean)}
    :hasPreviousPage {:type (g/non-null Scalars/GraphQLBoolean)}
    :startCursor {:type Scalars/GraphQLString}
    :endCursor {:type Scalars/GraphQLString}}))

(def connection-arguments
  {:after {:type Scalars/GraphQLString}
   :first {:type Scalars/GraphQLInt
           :default 50}
   :before {:type Scalars/GraphQLString}
   :last {:type Scalars/GraphQLInt}})

(defn new-edge
  [node-type edge-name]
  (g/new-object
   edge-name
   "An edge in a connection."
   []
   {:node {:type node-type}
    :cursor {:type (g/non-null Scalars/GraphQLString)}}))

(defn new-connection
  [node-type list-name]
  (let [capitalized-list-name (str/capitalize list-name)
        connection-name (str capitalized-list-name
                             "Connection")
        edge-name (str capitalized-list-name
                       "Edge")]
    (g/new-object
     connection-name
     (str "A connection to a list of " capitalized-list-name)
     []
     (assoc {:pageInfo {:type (g/non-null PageInfo)}
             :totalCount {:type Scalars/GraphQLInt}
             :edges {:type (g/list-type (new-edge node-type
                                                  edge-name))}}
            (keyword (str/lower-case list-name))
            {:type (g/list-type node-type)}))))

;;------- Limit/Offset with opaque cursor
;; See : https://github.com/darthtrevino/relay-cursor-paging/blob/master/src/getPagingParameters.ts

;; |--C0--|--C1--|--C2--|
;; C0: offset=0, C1: offset=1
;; To select the node corresponding to the C1 cursor:
;; - {:after C0 :first 1} -> {:offset 1 :limit 1}
;; - {:before C2 :last 1} -> {:offset 1 :limit 1}

(def default-limit 50)

(def Cursor s/Str)

(s/defschema PagingDirection
  {:forward-paging? s/Bool
   :backward-paging? s/Bool})

(s/defschema ConnectionParams
  (st/optional-keys
   {:first s/Int
    :last s/Int
    :after Cursor
    :before Cursor}))

(s/defschema PagingParams
  (st/merge
   {:limit s/Int
    :offset s/Int}
   PagingDirection))

(s/defschema Edge
  {:cursor Cursor
   :node s/Any})

(s/defschema Connection
  {:pageInfo {:hasNextPage s/Bool
              :hasPreviousPage s/Bool
              :startCursor (s/maybe Cursor)
              :endCursor (s/maybe Cursor)}
   :totalCount s/Int
   :edges (s/maybe [Edge])})

(s/defschema Result
  {:data [s/Any]
   :paging {s/Any s/Any}})

(s/defn serialize-cursor :- (s/maybe Cursor)
  [offset :- (s/maybe s/Int)]
  (when offset
    (String. (b64/encode (.getBytes (str offset))))))

(s/defn unserialize-cursor :- (s/maybe s/Int)
  [cursor :- (s/maybe Cursor)]
  (when cursor
    (try
      (-> cursor
          (.getBytes)
          b64/decode
          (String.)
          (Integer/parseInt))
      (catch Exception e
        (log/warn e "Unable to unserialize cursor")
        0))))

(s/defn validate-paging :- PagingDirection
  "Validates cursor-based params and returns the
   pagination direction."
  [{:keys [after before]
    first-param :first
    last-param :last} :- ConnectionParams]
  (let [backward-paging? (some? (or last-param before))
        forward-paging? (or (some? (or first-param after))
                            (not backward-paging?))]
    (when (and forward-paging? backward-paging?)
      (throw
       (Exception.
        "cursor-based pagination cannot be forwards AND backwards")))
    (when (or (and forward-paging?
                   first-param
                   (neg? first-param))
              (and backward-paging?
                   last-param
                   (neg? last-param)))
      (throw
       (Exception.
        "paging limit must be positive")))
    ;; This is a weird corner case. We'd have to invert the
    ;; ordering of query to get the last few items then re-invert
    ;; it when emitting the results.
    ;; We'll just ignore it for now.
    (when (and last-param (not before))
      (throw
       (Exception.
        "when paging backwards, a 'before' argument is required")))
    {:forward-paging? forward-paging?
     :backward-paging? backward-paging?}))

(s/defn connection-params->paging-params :- PagingParams
  "Computes limit/offset params from cursor-based
   pagination params."
  [{:keys [first last after before]
    first-param :first
    last-param :last
    :as connection-params} :- ConnectionParams]
  (let [{:keys [forward-paging?
                backward-paging?] :as direction}
        (validate-paging connection-params)]
    (into
     direction
     (cond
       forward-paging?
       (let [after-offset (unserialize-cursor after)]
         {:limit (or first-param default-limit)
          :offset (if after-offset (inc after-offset) 0)})
       backward-paging?
       (let [before-offset (unserialize-cursor before)
             limit last-param
             offset (- before-offset last-param)]
         ;; Check to see if our before-page is underflowing past the 0th item
         (if (neg? offset)
           ;; Adjust the limit with the underflow value
           {:limit (max (+ last-param offset) 0)
            :offset 0}
           {:limit limit
            :offset offset}))
       :else {:limit 0
              :offset 0}))))

(s/defn data->edges :- (s/maybe [Edge])
  "Builds Connection Edges from a data result list"
  [data :- (s/maybe [s/Any])
   first-offset :- s/Int]
  (map-indexed
   (fn [idx x]
     {:cursor (serialize-cursor (+ first-offset idx))
      :node x})
   data))

(s/defn result->connection-response :- Connection
  "Creates a Relay Connection response from search results"
  [{:keys [data paging]} :- Result
   {:keys [forward-paging?
           backward-paging?
           offset]}] :- ConnectionParams
  (let [edges (data->edges data offset)]
    {:pageInfo
     {:hasNextPage (or (and forward-paging?
                            (some? (:next paging)))
                       (and backward-paging?
                            (some? (:previous paging))))
      :hasPreviousPage (or (and forward-paging?
                                (some? (:previous paging)))
                           (and backward-paging?
                                (some? (:next paging))))
      :startCursor (:cursor (first edges))
      :endCursor (:cursor (last edges))}
     :totalCount (:total-hits paging)
     :edges edges}))
