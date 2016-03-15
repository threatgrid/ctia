(ns cia.stores.sql.judgement
  (:require [cia.lib.specter.paths :as path]
            [cia.schemas.judgement :as judgement-schema]
            [cia.stores.sql.common :as c]
            [cia.stores.sql.db :refer [db]]
            [cia.stores.sql.selection :as select]
            [cia.stores.sql.transformation :as transform]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [com.rpl.specter :as sp]
            [korma.core :as k]
            [korma.db :as kdb])
  (:import java.util.UUID))

(defonce judgement (atom nil))

(defonce judgement-indicator (atom nil))

(defn init! []
  (reset! judgement
          (-> (k/create-entity "judgement")
              (k/database @db)))
  (reset! judgement-indicator
          (-> (k/create-entity "judgement-indicator")
              (k/table :judgement_indicator)
              (k/database @db))))

(defn realize-judgements [login new-judgements]
  (for [new-judgement new-judgements]
    (judgement-schema/realize-judgement new-judgement
                                        (str "judgement-" (UUID/randomUUID))
                                        login)))

(def judgement-indicator-relationship-map
  {:entity-relationship-key :indicators
   :relationship-reference-key :indicator_id
   :entity-id-key :judgement_id
   :other-id-key :indicator_id})

(def db-indicator->schema-indicator
  (transform/db-relationship->schema-relationship
   judgement-indicator-relationship-map))

(def schema-indicator->db-indicator
  (transform/schema-relationship->db-relationship
   judgement-indicator-relationship-map))

(def judgements->db-indicators
  (transform/entities->db-relationships
   judgement-indicator-relationship-map
   schema-indicator->db-indicator))

(defn insert-judgements [login & new-judgements]
  (let [realized-judgements (realize-judgements login new-judgements)
        judgements (-> realized-judgements
                       transform/datetimes-to-sqltimes)]
    (kdb/transaction
     (c/insert @judgement (->> judgements
                               select/judgement-entity-values
                               (map transform/to-db-observable)
                               (map transform/to-db-valid-time)))
     (c/insert @judgement-indicator (judgements->db-indicators judgements)))
    realized-judgements))

(defn select-judgements [filter-map]
  (let [judgements
        (->> (k/select @judgement
                       (k/where (transform/filter-map->where-map filter-map)))
             (group-by :id)
             (sp/transform path/all-last
                           (comp transform/to-schema-observable
                                 transform/to-schema-valid-time
                                 transform/sqltimes-to-datetimes
                                 transform/drop-nils
                                 first)))

        judgement-indicators
        (if-let [ids (keys judgements)]
          (->> (k/select @judgement-indicator
                         (k/where {:judgement_id [in ids]}))
               (group-by :judgement_id)
               (sp/transform path/all-last-all
                             db-indicator->schema-indicator)))]

    (for [[id judgement] judgements]
      (assoc judgement :indicators (get judgement-indicators id)))))

(defn delete-judgement [id]
  (kdb/transaction
   (let [num-rows-deleted
         (+
          (k/delete @judgement
                    (k/where {:id id}))
          (k/delete @judgement-indicator
                    (k/where {:judgement_id id})))]
     (> num-rows-deleted 0))))

(defn calculate-verdict [{:keys [type value] :as _observable_}]
  (k/select @judgement
            (k/fields :disposition [:id :judgement_id] :disposition_name)
            (k/where {:observable_type type
                      :observable_value value})
            (k/where (or (= :valid_time_end_time nil)
                         (> :valid_time_end_time (coerce/to-sql-time (time/now)))))
            (k/order :priority :DESC)
            (k/order :disposition)
            (k/order :valid_time_start_time)
            (k/limit 1)))

(defn create-judgement-indicator [judgement-id indicator-rel]
  (when (seq (k/select @judgement (k/where {:id judgement-id})))
    (c/insert @judgement-indicator
              [(schema-indicator->db-indicator judgement-id indicator-rel)])
    indicator-rel))
