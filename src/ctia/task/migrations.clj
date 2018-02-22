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

(defn append-version [version]
  "append the version field only
   if the document is not a user"
  (map #(if-not (seq (:capabilities %))
          (assoc % :schema_version version)
          %)))

(def target-observed_time
  "append observed_time to sighting/target
  inheriting the sighting"
  (fn [{:keys [target observed_time] :as doc}]
    (if (and target
             (not (:observed_time target)))
      (update doc :target assoc :observed_time observed_time)
      doc)))

(def pluralize-targets
  "a sighting can have multiple targets"
  (fn [{:keys [target] :as doc}]
    (if target (-> doc
                   (assoc :targets [target])
                   (dissoc :target))
        doc)))

(def available-migrations
  {:__test (map #(assoc % :groups ["migration-test"]))
   :0.4.16 (comp add-groups
                 fix-end-time
                 (append-version "0.4.16"))
   :0.4.26 (comp pluralize-targets
                 target-observed_time
                 add-groups
                 fix-end-time
                 (append-version "0.4.26"))})
