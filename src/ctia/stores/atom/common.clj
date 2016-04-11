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

(defmacro def-read-handler [name Model]
  `(s/defn ~name :- (s/maybe ~Model)
     [state# :- (s/atom {s/Str ~Model})
      id# :- s/Str]
     (get (deref state#) id#)))

(defn create-handler-from-realized
  "Create a new resource from a realized object"
  [Model]
  (s/fn :- Model
    [state :- (s/atom {s/Str Model})
     _ :- s/Any ;; Deprecated; to refactor
     model :- Model]
    (let [id (:id model)]
      (get (swap! state assoc id model) id))))

(defn update-handler-from-realized
  "Update a resource using an id and a realized object"
  [Model]
  (s/fn :- Model
    [state :- (s/atom {s/Str Model})
     id :- c/ID
     _ :- s/Any ;; Deprecated; to refactor
     updated-model :- Model]
    (get (swap! state assoc id updated-model) id)))


(defmacro def-create-handler [name Model NewModel swap-fn id-fn]
  `(s/defn ~name :- ~Model
     [state# :- (s/atom {s/Str ~Model})
      login# :- s/Str
      new-model# :- ~NewModel]
     (let [new-id# (~id-fn new-model#)]
       (get
        (swap! state# ~swap-fn new-model# new-id# login#)
        new-id#))))

(defmacro def-update-handler [name Model NewModel swap-fn]
  `(s/defn ~name :- ~Model
     [state# :- (s/atom {s/Str ~Model})
      id# :- c/ID
      login# :- s/Str
      updated-model# :- ~NewModel]
     (get
      (swap! state#
             ~swap-fn
             updated-model#
             id#
             login#
             (get (deref state#) id#))
      id#)))

(defn delete-handler [Model]
  (s/fn :- s/Bool
    [state :- (s/atom {s/Str Model})
     id :- s/Str]
    (if (contains? (deref state) id)
      (do (swap! state dissoc id)
          true)
      false)))

(defmacro def-delete-handler [name Model]
  `(s/defn ~name :- s/Bool
     [state# :- (s/atom {s/Str ~Model})
      id# :- s/Str]
     (if (contains? (deref state#) id#)
       (do (swap! state# dissoc id#)
           true)
       false)))

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

(defn make-swap-fn [entity-fn]
  (fn [state-map & [new-model id :as args]]
    (assoc state-map id (apply entity-fn args))))
