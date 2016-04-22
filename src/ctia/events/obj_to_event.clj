(ns ctia.events.obj-to-event
  (:require [clojure.data :refer [diff]]
            [schema.core :as s]
            [ctia.lib.time :as t]
            [ctia.schemas.actor :refer [StoredActor]]
            [ctia.schemas.campaign :refer [StoredCampaign]]
            [ctia.events.schemas :as vs]))


(s/defn to-create-event :- vs/CreateEvent
  "Create a CreateEvent from a StoredX object"
  [object]
  {:owner (:owner object)
   :object object
   :timestamp (t/now)
   :id (:id object)
   :type vs/CreateEventType})

(defn diff-to-list-of-triplet
  "Given the output of a `diff` between maps return a list
  of edit distance operation under the form of an atom triplet."
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


(s/defn to-update-event :- vs/UpdateEvent
  "transform an object (generally a `StoredObject`) to an `UpdateEvent`.
   The two arguments `object` and `prev-object` should have the same schema.
   The fields should contains enough information to retrieve all informations.
   But the complete object is given for simplicity."
  [object prev-object]
  {:owner (:owner object)
   :object object
   :timestamp (t/now)
   :id (:id object)
   :type vs/UpdateEventType
   :fields (diff-to-list-of-triplet
            (diff object prev-object))
   ;; I believe the `metadata` shoudld be `{s/Any s/Any}`
   ;; in the following schema:
   ;; `ctia.events.schemas/UpdateTriple`
   })

(s/defn to-delete-event :- vs/DeleteEvent
  "transform an object (generally a `StoredObject`) to its corresponding `Event`"
  [object]
  {:owner (:owner object)
   :object object
   :timestamp (t/now)
   :id (:id object)
   :type vs/DeleteEventType})
