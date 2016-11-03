(ns ctia.stores.atom.common
  (:import java.util.UUID)
  (:require [ctia.schemas.core :refer [ID]]
            [schema.core :as s]
            [clojure.set :as set]
            [ctia.lib.pagination :refer [default-limit
                                         list-response-schema
                                         paginate
                                         response]]
            [ctia.lib.schema :as ls]))

(defn random-id [prefix]
  (fn [_new-entity_]
    (str prefix "-" (UUID/randomUUID))))

(defn read-handler [Model]
  (s/fn :- (s/maybe Model)
    [state :- (ls/atom {s/Str Model})
     id :- ID]
    (get (deref state) id)))

(defn create-handler-from-realized
  "Create a new resource from a realized object"
  [Model]
  (s/fn :- [Model]
    [state :- (ls/atom {s/Str Model})
     models :- [Model]]
    (doall
     (for [model models]
       (let [id (:id model)]
         (get (swap! state assoc id model) id))))))

(defn update-handler-from-realized
  "Update a resource using an id and a realized object"
  [Model]
  (s/fn :- Model
    [state :- (ls/atom {s/Str Model})
     id :- ID
     updated-model :- Model]
    (get (swap! state assoc id updated-model) id)))

(defn delete-handler [Model]
  (s/fn :- s/Bool
    [state :- (ls/atom {s/Str Model})
     id :- ID]
    (if (contains? (deref state) id)
      (do (swap! state dissoc id)
          true)
      false)))

(defn- match? [v1 v2]
  (cond
    (or (and (coll? v1) (empty? v1)) (and (coll? v2) (empty? v2))) false
    (and (coll? v1) (set? v2)) (not (empty? (set/intersection (set v1) v2)))
    (and (set? v2)) (contains? v2 v1)
    (and (coll? v1)) (contains? (set v1) v2)
    :else (= v1 v2)))

(defn filter-state [Model]
  "Mostly work like MongoDB find():

    - `{:a value}` will match all objects such that the
      `:a` field is equal to `value`
    - `{[:a :b] value}` will match all objects such that
      `(= value (get-in object [:a :b]))`
    - `{:a #{v1 v2 v3}}` will match all objects such that
      `:a` field is either equal to `v1`, `v2` or `v3`.
    - `{[:a :b] #{v1 v2 v3}}` will match all objects such that
      `(get-in object [:a :b])` is equal to `v1`, `v2` or `v3`

    - if in the model `:a` links to a sequential value then:
        - `{:a value}` will match all objects s.t. `(contains? (:a object) value)`
           For example: if the object is `{:a [:foo :bar :baz]}`
           and we search for `{:a :foo}`, it will match.
        - `{:a #{v1 v2 v3}}` will match if the intersection is not empty
           For example: if object is `{:a [:foo :bar :baz]}`
           and we search for `{:a #{:quux :foo}}` it will match
           as both the search set and the collection `(:a object)`
           contains `:foo`
        - Of course it still works as expected if the key is a list:
          `{[:a :b] #{v1 v2 v3}}`."

  (s/fn :- [Model]
    [state :- (ls/atom {s/Str Model})
     filter-map :- {s/Any s/Any}]

    (when-not (empty? filter-map)
      (filterv (fn [model]
                 (every? (fn [[k v]]
                           (let [found-v (if (sequential? k)
                                           (get-in model k ::not-found)
                                           (get model k ::not-found))]
                             (match? found-v v)))
                         filter-map))
               (vals (deref state))))))

(defn list-handler [Model]
  (s/fn :- (list-response-schema Model)
    ([state :- (ls/atom {s/Str Model})
      filter-map :- {s/Any s/Any}
      params]

     (let [res ((filter-state Model) state filter-map)]
       (-> res
           (paginate params)
           (response (:offset params)
                     (:limit params)
                     (count res)))))))
