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
  (map #(assoc % :schema_version version)))

(def available-migrations
  {:__test (map #(assoc % :groups ["migration-test"]))
   :0.4.16 (comp add-groups
              fix-end-time
              (append-version "0.4.16"))})
