(ns ctia.stores.atom.common
  (:require [ctia.schemas.common :as c]
            [schema.core :as s])
  (:import java.util.UUID))

(defn random-id [prefix]
  (fn [_new-entity_]
    (str prefix "-" (UUID/randomUUID))))

(defn read-handler [Model]
  (s/fn :- (s/maybe Model)
    [state :- (s/atom {s/Str Model})
     id :- s/Str]
    (get (deref state) id)))

(defn create-handler-from-realized
  "Create a new resource from a realized object"
  [Model]
  (s/fn :- Model
    [state :- (s/atom {s/Str Model})
     model :- Model]
    (let [id (:id model)]
      (get (swap! state assoc id model) id))))

(defn update-handler-from-realized
  "Update a resource using an id and a realized object"
  [Model]
  (s/fn :- Model
    [state :- (s/atom {s/Str Model})
     id :- c/ID
     updated-model :- Model]
    (get (swap! state assoc id updated-model) id)))


(defn delete-handler [Model]
  (s/fn :- s/Bool
    [state :- (s/atom {s/Str Model})
     id :- s/Str]
    (if (contains? (deref state) id)
      (do (swap! state dissoc id)
          true)
      false)))

(defn list-handler [Model]
  "Mostly work like MongoDB find():

    - `{:a value}` will match all objects such that the
      `:a` field is equal to `value`
    - `{[:a :b] value}` will match all objects such that
      `(= value (get-in object [:a :b]))`
    - `{:a #{v1 v2 v3}}` will match all objects such that
      `:a` field is either equal to `v1`, `v2` or `v3`.
    - `{[:a :b] #{v1 v2 v3}}` will match all objects such that
      `(get-in object [:a :b])` is equal to `v1`, `v2` or `v3`"
  (s/fn :- (s/maybe [Model])
    [state :- (s/atom {s/Str Model})
     filter-map :- {s/Any s/Any}]
    (into []
          (filter (fn [model]
                    (every? (fn [[k v]]
                              (let [found-v (if (sequential? k)
                                              (get-in model k ::not-found)
                                              (get model k ::not-found))]
                                (if (set? v)
                                  (contains? v found-v)
                                  (= v found-v))))
                            filter-map))
                  (vals (deref state))))))

