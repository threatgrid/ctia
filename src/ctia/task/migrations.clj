(ns ctia.task.migrations
  (:require [clj-momo.lib.clj-time
             [coerce :as time-coerce]
             [core :as time-core]]))

(def add-groups
  "set a document group to [\"tenzin\"] if unset"
  (map (fn [{:keys [groups]
             :as doc}]
         (if-not (seq groups)
           (assoc doc :groups ["tenzin"])
           doc))))

(def fix-end-time
  "fix end_time to 2535"
  (map
   (fn [{:keys [valid_time]
         :as doc}]
     (if (:end_time valid_time)
       (update-in doc
                  [:valid_time
                   :end_time]
                  #(let [max-end-time (time-core/internal-date 2525 01 01)
                         end-time (time-coerce/to-internal-date %)]
                     (if (time-core/after? end-time max-end-time)
                       max-end-time
                       end-time)))
       doc))))

(defn append-version
  "append the version field only
   if the document is not a user"
  [version]
  (map #(if-not (seq (:capabilities %))
          (assoc % :schema_version version)
          %)))

(def target-observed_time
  "append observed_time to sighting/target
  inheriting the sighting"
  (map (fn [{:keys [target observed_time] :as doc}]
         (if (and target
                  (not (:observed_time target)))
           (update doc :target assoc :observed_time observed_time)
           doc))))

(def pluralize-target
  "a sighting can have multiple targets"
  (map (fn [{:keys [type target] :as doc}]
         (if (and (= "sighting" type)
                  (not (nil? target)))
           (-> doc
               (assoc :targets (if (vector? target)
                                 target [target]))
               (dissoc :target))
           doc))))

(def available-migrations
  {:__test (map #(assoc % :groups ["migration-test"]))
   :0.4.16 (comp add-groups
                 fix-end-time
                 (append-version "0.4.16"))
   :0.4.28 (comp pluralize-target
                 target-observed_time
                 add-groups
                 fix-end-time
                 (append-version "0.4.28"))})
