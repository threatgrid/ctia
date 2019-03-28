(ns ctia.task.migration.migrations
  (:require [clj-momo.lib.clj-time
             [coerce :as time-coerce]
             [core :as time-core]]
            [clojure.set :as set]))

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
   if the document is not a user or an event"
  [version]
  (map #(if-not (or (= (:type %) "event")
                    (seq (:capabilities %)))
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

;;-- Rename observable type

(defn with-renamed-observable-type
  [observable old new]
  (update observable
          :type
          (fn [obs-type]
            (if (= obs-type old)
              new
              obs-type))))

(defn with-renamed-observable-types
  [c old new]
  (mapv #(with-renamed-observable-type % old new)
        c))

(defn rename-sighting-relations-observable-types
  [relation old new]
  (-> relation
      (update :source with-renamed-observable-type old new)
      (update :related with-renamed-observable-type old new)))

(defn rename-sighting-observable-types
  [sighting old new]
  (-> sighting
      (update :observables with-renamed-observable-types old new)
      (update :relations
              (fn [relations]
                (mapv #(rename-sighting-relations-observable-types % old new)
                      relations)))))

(defn rename-judgement-observable-type
  [judgement old new]
  (update judgement
          :observable
          with-renamed-observable-type
          old
          new))

(defn rename-bundle-observable-types
  [bundle old new]
  (-> bundle
      (update :sightings (fn [sightings]
                           (mapv #(rename-sighting-observable-types % old new)
                                 sightings)))
      (update :judgements (fn [judgements]
                            (mapv #(rename-judgement-observable-type % old new)
                                  judgements)))
      (update :verdicts (fn [verdicts]
                          (mapv #(rename-judgement-observable-type % old new)
                                verdicts)))))

(defn rename-casebook-observable-types
  [{:keys [observables bundle] :as casebook} old new]
  (cond-> casebook
    (seq observables) (update :observables with-renamed-observable-types old new)
    bundle (update :bundle rename-bundle-observable-types old new)))

(defn rename-observable-type
  [old new]
  (map (fn [{observable-type :type
             :as doc}]
         (case observable-type
           "sighting" (rename-sighting-observable-types doc old new)
           ("judgement"
            "verdict") (rename-judgement-observable-type doc old new)
           "casebook" (rename-casebook-observable-types doc old new)
           doc))))

;;--- Simplify incident model

(defn simplify-incident-time
  [incident-time]
  (-> incident-time
      (dissoc :first_malicious_action :initial_compromise :first_data_exfiltration
              :containment_achieved :restoration_achieved)
      (set/rename-keys {:incident_discovery :discovered
                        :incident_opened :opened
                        :incident_reported :reported
                        :incident_closed :closed})))

(def simplify-incident
  (map (fn [{entity-type :type :as doc}]
         (if (= entity-type "incident")
           (-> doc
               (dissoc :valid_time :reporter :responder :coordinator :victim
                       :affected_assets :impact_assessment :security_compromise
                       :COA_requested :COA_taken :contact :history :related_indicators
                       :related_observables :attributed_actors :related_incidents)
               (update :incident_time simplify-incident-time))
           doc))))

(def available-migrations
  {:__test (map #(assoc % :groups ["migration-test"]))
   :0.4.16 (comp (append-version "0.4.16")
                 add-groups
                 fix-end-time)
   :0.4.28 (comp (append-version "0.4.28")
                 fix-end-time
                 add-groups
                 target-observed_time
                 pluralize-target)
   :1.0.0 (comp (append-version "1.0.0")
                (rename-observable-type "pki-serial" "pki_serial")
                simplify-incident)})
