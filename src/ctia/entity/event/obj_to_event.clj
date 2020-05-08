(ns ctia.entity.event.obj-to-event
  (:require [ctia.entity.event.schemas :as vs]
            [ctia.domain.entities :refer [with-long-id]]
            [ctia.properties :as prop]
            [clojure.data :refer [diff]]
            [clojure.set :as set]
            [clojure.walk :as walk]
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

(s/defn ^:deprecated diff-to-list-of-changes :- [vs/Update]
  "Given the output of a `diff` between maps return a list
  of edit distance operation under the form of an Update map"
  [[diff-after diff-before _]]
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
                 (keys diff-after)))))

(defn truncate
  "Truncate v, a possibly-nested value, by inserting placeholder after given
  max-depth and max-count."
  ([v placeholder max-count max-depth] (truncate v placeholder max-count max-depth 0))
  ([v placeholder max-count max-depth depth]
   (let [tru #(truncate % placeholder max-count max-depth (inc depth))
         dots placeholder]
     (cond
       (< max-depth depth) dots
       (seq? v) (let [[pre suf] (split-at max-count v)]
                  (doall
                    (concat (map tru pre)
                            (when (seq suf)
                              [dots]))))
       (map? v) (let [pre (take max-count (keys v))
                      ;; in case keys conflict after truncation,
                      ;; remove all conflicting keys.
                      ;; eg., if kvs is [[:a 1] [:a 2] [:b 3]]
                      ;;      then truncate to [[:b 3]]
                      kvs (mapv vector
                                (map tru pre)
                                (map (comp tru v) pre))
                      ks (->> (map first kvs)
                              frequencies
                              (filter (comp #{1} second))
                              (map first)
                              set)
                      m (into {}
                              (filter (comp ks first))
                              kvs)]
                  (cond-> m
                    (not= (count m) (count v)) (assoc dots dots)))
       (vector? v) (let [pre (subvec v
                                     0
                                     (min (count v) max-count))]
                     (into (mapv tru pre)
                           (when (<= max-count (count v))
                             [dots])))
       ;; conservatively don't walk other collections.
       ;; can add other cases as needed.
       (coll? v) dots
       ;; leave everything else, presumably atoms like strings, idents...
       :else v))))

(defn update-changes
  "Returns a list of changes for an Update event"
  ([old new placeholder max-count max-depth]
   {:pre [(map? old)
          (map? new)]}
   (let [truncate #(truncate % placeholder max-count max-depth)
         old-keys (set (keys old))
         new-keys (set (keys new))
         same-keys (set/intersection new-keys old-keys)
         added-keys (set/difference new-keys old-keys)
         deleted-keys (set/difference old-keys new-keys)]
     (vec
       (concat
         (mapcat (fn [k]
                   {:pre [(keyword? k)]}
                   (let [[oldv newv] (map k [old new])]
                     (when (not= oldv newv)
                       [{:field k
                         :action "modified"
                         :change {:before (truncate oldv)
                                  :after (truncate newv)}}])))
                 same-keys)
         (map (fn [k]
                {:pre [(keyword? k)]}
                {:field k
                 :action "added"
                 :change {:after (truncate (k new))}})
              added-keys)
         (map (fn [k]
                {:pre [(keyword? k)]}
                {:field k
                 :action "deleted"
                 :change {:before (truncate (k old))}})
              deleted-keys))))))

(def default-max-count
  "Default maximum length of collections to preserve in Update diffs"
  10)

(def default-max-depth
  "Default maximum depth of values to preserve in Update diffs"
  10)

(def default-placeholder
  "Default placeholder for truncated values."
  '...)

(s/defn to-update-event :- vs/Event
  "transform an object (generally a `StoredObject`) to an `UpdateEvent`.
   The two arguments `object` and `prev-object` should have the same schema.
   The fields should contains enough information to retrieve all informations.
   But the complete object is given for simplicity."
  ([object prev-object event-id]
   (to-update-event object prev-object event-id
                    {:placeholder default-placeholder
                     :max-count (get-in @prop/properties [:ctia :events :diff :max-count]
                                        default-max-count)
                     :max-depth (get-in @prop/properties [:ctia :events :diff :max-depth]
                                        default-max-depth)}))
  ([object prev-object event-id {:keys [placeholder max-count max-depth]}]
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
              (dissoc object :id)
              placeholder
              max-count
              max-depth)}))

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
