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
  (s/fn :- (s/maybe Model)
    [state :- (s/atom {s/Str Model})
     filter-map :- {s/Any s/Any}]
    (into []
          (filter (fn [model]
                    (every? (fn [[k v]]
                              (if (sequential? k)
                                (= v (get-in model k ::not-found))
                                (= v (get model k ::not-found))))
                            filter-map))
                  (vals (deref state))))))

(defmacro def-list-handler [name Model]
  `(s/defn ~name :- (s/maybe [~Model])
     [state# :- (s/atom {s/Str ~Model})
      filter-map# :- {s/Any s/Any}]
     (into []
           (filter (fn [model#]
                     (every? (fn [[k# v#]]
                               (if (sequential? k#)
                                 (= v# (get-in model# k# ::not-found))
                                 (= v# (get model# k# ::not-found))))
                             filter-map#))
                   (vals (deref state#))))))
