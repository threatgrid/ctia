(ns ctia.stores.sql.transformation
  (:require [ctia.lib.specter.paths :as path]
            [ctia.lib.time :as time]
            [clojure.string :as str]
            [com.rpl.specter :refer :all]))

(defn drop-nils [entity]
  (into {}
        (filter (fn [[k v]]
                  (some? v))
                entity)))

(defn to-db-observable
  [{{:keys [type value]} :observable
    :as entity}]
  (-> entity
      (cond-> type  (assoc :observable_type type)
              value (assoc :observable_value value))
      (dissoc :observable)))

(defn to-schema-observable
  [{:keys [observable_value observable_type]
    :as entity}]
  (-> entity
      (cond-> observable_value (assoc-in [:observable :value] observable_value)
              observable_type  (assoc-in [:observable :type] observable_type))
      (dissoc :observable_type :observable_value)))

(defn to-db-valid-time
  [{{:keys [start_time end_time]} :valid_time
    :as entity}]
  (-> entity
      (cond-> start_time (assoc :valid_time_start_time start_time)
              end_time   (assoc :valid_time_end_time end_time))
      (dissoc :valid_time)))

(defn to-schema-valid-time
  [{:keys [valid_time_start_time valid_time_end_time]
    :as entity}]
  (-> entity
      (cond-> valid_time_start_time (assoc-in [:valid_time :start_time] valid_time_start_time)
              valid_time_end_time   (assoc-in [:valid_time :end_time] valid_time_end_time))
      (dissoc :valid_time_end_time :valid_time_start_time)))

(defn dates-to-sqltimes
  "walk entities and convert joda DateTimes to sql Timestamps"
  [entities]
  (transform path/walk-dates
             time/to-sql-time
             entities))

(defn sqltimes-to-dates
  "walk entities and convert sql Timestamps to joda java.util.Dates"
  [entities]
  (transform path/walk-sqltimes
             time/from-sql-time
             entities))

(defn db-relationship->schema-relationship
  "Make an fn that takes one DB relationship and converts it to a
   schema-style structure.  Takes a relationship description map."
  [{:keys [entity-relationship-key
           relationship-reference-key
           entity-id-key
           other-id-key]}]
  (fn [{:keys [confidence source relationship]
        :as db-relationship}]
    (cond-> {relationship-reference-key (get db-relationship other-id-key)}
      confidence   (assoc :confidence confidence)
      source       (assoc :source source)
      relationship (assoc :relationship relationship))))

(defn schema-relationship->db-relationship
  "Make an fn that takes one ctim.schema style relationship and
  converts it to a structure that can be inserted into the DB.  Takes
  a relationship description map.  The fn takes an entity ID and
  relationship structure (which doesn't have the entity ID)."
  [{:keys [entity-relationship-key
           relationship-reference-key
           entity-id-key
           other-id-key]}]
  (fn [entity-id
       {:keys [confidence source relationship] :as related-structure}]
    (cond-> {other-id-key (get related-structure relationship-reference-key)
             entity-id-key entity-id}
      confidence   (assoc :confidence confidence)
      source       (assoc :source source)
      relationship (assoc :relationship relationship))))

(defn entities->db-relationships
  ([relationship-description]
   (entities->db-relationships relationship-description
                               (schema-relationship->db-relationship
                                relationship-description)))
  ([{:keys [entity-relationship-key]}
    schema-relationship->db-relationship-fn]
   (fn [entities]
     (for [{entity-id :id :as entity} entities
           relationship-map (get entity entity-relationship-key)]
       (schema-relationship->db-relationship-fn
        entity-id
        relationship-map)))))

(defn filter-map->where-map [filter-map]
  (into {}
        (map
         (fn [[key value]]
           (vector (cond
                     (sequential? key)
                     (keyword (str/join "_" (map name key)))

                     :else
                     key)
                   value))
         filter-map)))
