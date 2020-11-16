(ns ctia.entity.event.obj-to-event
  (:require [ctia.entity.event.schemas :as vs]
            [clojure.data :refer [diff]]
            [clojure.set :as set]
            [clj-momo.lib.time :as time]
            [clj-momo.lib.clj-time.core :as t]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [schema.core :as s]))

(s/defn to-create-event :- vs/Event
  "Create a CreateEvent from a StoredX object"
  [{{:keys [now]} :CTIATimeService} :- APIHandlerServices
   object
   id]
  {:owner (:owner object)
   :groups (:groups object)
   :entity object
   :timestamp (now)
   :id id
   :type "event"
   :tlp (:tlp object)
   :event_type vs/CreateEventType})

(s/defn ^:deprecated diff-to-list-of-changes :- [vs/Update]
  "Deprecated in favor of `update-changes`.

  Given the output of a `diff` between maps return a list
  of edit distance operation under the form of an Update map"
  [[diff-after diff-before _]]
  (filter
   (fn [{:keys [change]}]
     (seq change))
   (concat
    (map (fn [k]
           (if (contains? diff-after k)
             {:field k
              :action "modified"
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

(defn update-changes
  "Returns a list of changes for an Update event"
  [old new]
  {:pre [(map? old)
         (map? new)]
   :post [(vector? %)]}
  (let [old-keys (set (keys old))
        new-keys (set (keys new))
        same-keys (set/intersection new-keys old-keys)
        added-keys (set/difference new-keys old-keys)
        deleted-keys (set/difference old-keys new-keys)]
    (vec
      ;; sort by :field then :action
      (sort-by
        (juxt :field :action)
        (concat
          (mapcat (fn [k]
                    {:pre [(keyword? k)]}
                    (let [[oldv newv] (map k [old new])]
                      (when (not= oldv newv)
                        [{:field k
                          :action "modified"
                          :change {:before oldv
                                   :after newv}}])))
                  same-keys)
          (map (fn [k]
                 {:pre [(keyword? k)]}
                 {:field k
                  :action "added"
                  :change {:after (k new)}})
               added-keys)
          (map (fn [k]
                 {:pre [(keyword? k)]}
                 {:field k
                  :action "deleted"
                  :change {:before (k old)}})
               deleted-keys))))))

(s/defn to-update-event :- vs/Event
  "Transform an object (generally a `StoredObject`) to an `UpdateEvent`.
   The two arguments `object` and `prev-object` should have the same schema.
   The fields should contain enough information to retrieve all information,
   but the complete object is given for simplicity."
  [object prev-object event-id]
  {:owner (:owner object)
   :groups (:groups object)
   :entity object
   :timestamp (t/internal-now)
   :id event-id
   :type "event"
   :tlp (:tlp object)
   :event_type vs/UpdateEventType
   :fields (update-changes
             (dissoc prev-object :id)
             (dissoc object :id))})

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
