(ns ctia.entity.event.obj-to-event
  (:require [ctia.entity.event.schemas :as vs]
            [ctia.domain.entities :refer [with-long-id]]
            [clojure.data :refer [diff]]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.clj-time.core :as t]
            [schema.core :as s]))

(s/defn to-create-event :- vs/Event
  "Create a CreateEvent from a StoredX object"
  [object id]
  {:owner (:owner object)
   :groups (:groups object)
   :entity object
   :timestamp (t/internal-now)
   :id id
   :type "event"
   :tlp (:tlp object)
   :event_type vs/CreateEventType})

(s/defn diff-to-list-of-changes :- [vs/Update]
  "Given the output of a `diff` between maps return a list
  of edit distance operation under the form of an Update map"
  [[diff-after diff-before _]]
  (filter
   (fn [{:keys [change]}]
     (seq change))
   (concat
    (map (fn [k]
           (if (contains? diff-after k)
             {:field k
              :action "modifed"
              :change {:before (get diff-before k)
                       :after (get diff-after k)}}
             {:field k
              :action "deleted"
              :change {}}))
         (keys diff-before))
    (map (fn [k]
           {:field k
            :action "added"
            :change {}})
         (remove #(contains? diff-before %)
                 (keys diff-after))))))


(s/defn to-update-event :- vs/Event
  "transform an object (generally a `StoredObject`) to an `UpdateEvent`.
   The two arguments `object` and `prev-object` should have the same schema.
   The fields should contains enough information to retrieve all informations.
   But the complete object is given for simplicity."
  [object prev-object event-id]
  {:owner (:owner object)
   :groups (:groups object)
   :entity object
   :timestamp (t/internal-now)
   :id event-id
   :type "event"
   :tlp (:tlp object)
   :event_type vs/UpdateEventType
   :fields (diff-to-list-of-changes
            (diff object (assoc
                          prev-object
                          :id (:id object))))})

(s/defn to-delete-event :- vs/Event
  "transform an object (generally a `StoredObject`) to its corresponding `Event`"
  [object id]
  {:owner (:owner object)
   :groups (:groups object)
   :entity object
   :timestamp (t/internal-now)
   :id id
   :type "event"
   :tlp (:tlp object)
   :event_type vs/DeleteEventType})
