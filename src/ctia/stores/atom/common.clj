(ns ctia.stores.atom.common
  (:require [ctia.schemas.common :as c]
            [schema.core :as s]
            [clojure.set :as set]
            [ctia.lib.pagination :refer [default-limit
                                         list-response-schema
                                         response]]))

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

;;borrowed from yann's upcoming PR ;-)
(defn- match? [v1 v2]
  (cond
    (or (and (coll? v1) (empty? v1)) (and (coll? v2) (empty? v2))) false
    (and (coll? v1) (set? v2)) (not (empty? (set/intersection (set v1) v2)))
    (and (set? v2)) (contains? v2 v1)
    (and (coll? v1)) (contains? (set v1) v2)
    :else (= v1 v2)))

(defn- filter-state [state filter-map]
  (when-not (empty? filter-map)
    (into []
          (filter (fn [model]
                    (every? (fn [[k v]]
                              (let [found-v (if (sequential? k)
                                              (get-in model k ::not-found)
                                              (get model k ::not-found))]
                                (match? found-v v)))
                            filter-map))
                  (vals (deref state))))))

(defn paginate
  [data {:keys [sort_by sort_order offset limit]
         :or {sort_by :id
              sort_order :asc
              offset 0
              limit default-limit}}]
  (as-> data $
    (sort-by sort_by $)
    (if (= :desc sort_order)
      (reverse $) $)
    (drop offset $)
    (take limit $)))

(defn list-handler [Model]
  (s/fn :- (list-response-schema Model)
    ([state :- (s/atom {s/Str Model})
      filter-map :- {s/Any s/Any}
      params]

     (let [res (filter-state state filter-map)]
       (-> res
           (paginate params)
           (response (:offset params)
                     (:limit params)
                     (count res)))))))

