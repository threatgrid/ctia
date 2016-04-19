(ns ctia.flows.event-hook
  (:require [ctia.flows.hook-protocol :refer [Hook]]
            [ctia.events.schemas :as vs]
            [ctia.events.producer :as p]
            [ctia.lib.time :as t]
            [ctia.schemas.actor :refer [StoredActor]]
            [ctia.schemas.campaign :refer [StoredCampaign]]
            [clojure.data :refer [diff]]))

(defn model-of
  "given a keyword retrieve the schema.

  We should either use an explicit function in `schemas/disptacher.clj`
  The problem is that for each new resource we must think to add it.

  Another possibilty is to use only `defrecord` instead of map for the
  schemas and create a Protocol ModelOf. That way, we won't need to be explicit.
  But for me this is a problem and a code smell.

  So I propose to use a centralized function that should be updated manually."
  [object-type]
  (case object-type
    :actor StoredActor
    :campaign StoredCampaign
    (throw (ex-info (str "unkown object-type: " object-type)
                    {:error "Unknown object type"
                     :object-type object-type}))))

(defn gen-create-event
  [object-type object _]
  {:owner (:owner object)
   :model (model-of object-type)
   :timestamp (t/now)
   :id (:id object)
   :type vs/CreateEventType
   })

(defn diff-to-list-of-triplet
  [[diff-before diff-after _]]
  (concat
   (map (fn [k]
          (if (contains? diff-after k)
            [k "modified" {(get diff-before k)
                           (get diff-after k)}]
            [k "deleted" {}]))
        (keys diff-before))
   (map (fn [k] [k "added" {}])
        (remove #(contains? diff-before %) 
                (keys diff-after)))))


(defn gen-update-event
  [object-type object prev-object]
  {:owner (:owner object)
   :model (model-of object-type)
   :timestamp (t/now)
   :id (:id object)
   :type vs/UpdateEventType
   :fields (diff-to-list-of-triplet
            (diff object prev-object))
   ;; I believe the `metadata` shoudld be `{s/Any s/Any}`
   ;; in the following schema:
   ;; `ctia.events.schemas/UpdateTriple`
   })

(defn gen-delete-event
  [object-type object _]
  {:owner (:owner object)
   :model (model-of object-type)
   :timestamp (t/now)
   :id (:id object)
   :type vs/DeleteEventType})

(defrecord EventHook [to-event]
  Hook
  (init [_] :nothing)
  (destroy [_] :nothing)
  (handle [_ object-type object prev-object]
    (p/produce (to-event object-type object prev-object))))

(def CreateEventHook (EventHook. gen-create-event))
