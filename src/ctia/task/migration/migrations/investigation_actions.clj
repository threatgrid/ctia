(ns ctia.task.migration.migrations.investigation-actions
  (:require
   [cheshire.core :as json]
   [ctia.schemas.core :as schemas]))

(defn object-id [doc]
  (let [id (get doc :id)]
    (when (not (schemas/transient-id? id))
      id)))

(defn read-actions [investigation]
  (let [actions (get investigation :actions)]
    (if (string? actions)
      (try
        (let [x (json/parse-string actions true)]
          (if (sequential? x)
            x
            []))
        (catch Exception _
          []))
      [])))

(def empty-action-data
  {:object_ids #{}
   :investigated_observables #{}
   :targets #{}})

(defn merge-action-data
  ([] empty-action-data)
  ([m] m)
  ([m1 m2]
   (merge m1 {:object_ids (into (get m1 :object_ids)
                                (get m2 :object_ids))
              :investigated_observables (into (get m1 :investigated_observables)
                                              (get m2 :investigated_observables))
              :targets (into (get m1 :targets)
                             (get m2 :targets))})))

(defn derive-collect-action-data
  {:private true}
  [action]
  (let [investigated-observables (into #{} (map (fn [{:keys [type value]}]
                                                  (str type ":" value)))
                                       (:result action))]
    (assoc empty-action-data :investigated_observables investigated-observables)))

(defn derive-investigate-action-data
  {:private true}
  [action]
  (let [data (get-in action [:result :data])]
    (transduce (comp (map :data)
                     (mapcat vals)
                     (mapcat :docs))
               (fn
                 ([result] result)
                 ([result doc]
                  (let [result (if-some [object-id (object-id doc)]
                                 (update result :object_ids conj object-id)
                                 result)
                        result (update result :targets into (get doc :targets))]
                    result)))
               {:object_ids #{}
                :investigated_observables []
                :targets #{}}
               data)))

(defn derive-action-data [action]
  (let [action-type (get action :type)]
    (case action-type
      "collect"
      (derive-collect-action-data action)

      "investigate"
      (derive-investigate-action-data action)

      ;; else
      empty-action-data)))

(defn migrate-investigation-action-data [investigation]
  (transduce (map derive-action-data)
             merge-action-data
             (merge investigation empty-action-data)
             (read-actions investigation)))

(def migrate-action-data
  (map (fn [{entity-type :type :as doc}]
         (if (= entity-type "investigation")
           (migrate-investigation-action-data doc)
           doc))))
