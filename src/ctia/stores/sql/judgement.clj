(ns ctia.stores.sql.judgement
  (:require [ctia.lib.pagination :as pagination]
            [ctia.lib.specter.paths :as path]
            [ctia.lib.time :as time]
            [ctia.stores.sql.common :as c]
            [ctia.stores.sql.db :refer [db]]
            [ctia.stores.sql.selection :as select]
            [ctia.stores.sql.transformation :as transform]
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

(defn insert-judgements [& new-judgements]
  (let [judgements (-> new-judgements
                       transform/dates-to-sqltimes)]
    (kdb/transaction
     (c/insert @judgement (->> judgements
                               select/judgement-entity-values
                               (map transform/to-db-observable)
                               (map transform/to-db-valid-time)
                               (map #(dissoc % :type))))
     (c/insert @judgement-indicator (judgements->db-indicators judgements)))
    new-judgements))

(defn sort-select
  [sort_by sort_order judgements]

  (let [sorted (sort-by sort_by judgements)]
    (if (= :desc sort_order)
      (reverse sorted)
      sorted)))

(defn count-judgements [judgement-query]
  (let [hits-res (-> judgement-query
                     (k/aggregate (count :*) :hits)
                     (k/select))]

    (or (-> hits-res first :hits) 0)))

(defn select-judgements
  [filter-map {:keys [sort_by sort_order offset limit]
               :or {sort_by :id
                    sort_order :asc
                    offset 0
                    limit pagination/default-limit}}]

  (let [judgement-query (-> (k/select* @judgement)
                            (k/where (transform/filter-map->where-map filter-map)))

        judgement-records (-> judgement-query
                              (k/group :id sort_by)
                              (k/order sort_by sort_order)
                              (k/limit limit)
                              (k/offset offset)
                              (k/select))
        judgements (->> judgement-records
                        (group-by :id)
                        (sp/transform path/all-last
                                      (comp transform/to-schema-observable
                                         transform/to-schema-valid-time
                                         transform/sqltimes-to-dates
                                         transform/drop-nils
                                         first)))

        judgement-indicators
        (if-let [ids (keys judgements)]
          (->> (k/select @judgement-indicator
                         (k/where {:judgement_id [in ids]}))
               (group-by :judgement_id)
               (sp/transform path/all-last-all
                             db-indicator->schema-indicator)))

        judgements-with-inds (for [[id judgement] judgements]
                               (merge judgement
                                      (if-let [inds (get judgement-indicators id)]
                                        {:indicators inds}
                                        {})))
        sorted-judgements (sort-select
                           sort_by
                           sort_order
                           judgements-with-inds)]

    (pagination/response (map #(assoc % :type "judgement") sorted-judgements)
                         offset
                         limit
                         (count-judgements judgement-query))))

(defn delete-judgement [id]
  (kdb/transaction
   (let [num-rows-deleted
         (+
          (k/delete @judgement
                    (k/where {:id id}))
          (k/delete @judgement-indicator
                    (k/where {:judgement_id id})))]
     (pos? num-rows-deleted))))

(defn calculate-verdict [{:keys [type value] :as _observable_}]
  (some-> (k/select @judgement
                    (k/fields :disposition [:id :judgement_id] :disposition_name)
                    (k/where {:observable_type type
                              :observable_value value})
                    (k/where (or (nil? :valid_time_end_time)
                                 (> :valid_time_end_time (time/sql-now))))
                    (k/order :priority :DESC)
                    (k/order :disposition)
                    (k/order :valid_time_start_time)
                    (k/limit 1))
          first
          (merge {:type "verdict"})))

(defn create-judgement-indicator [judgement-id indicator-rel]
  (when (seq (k/select @judgement (k/where {:id judgement-id})))
    (c/insert @judgement-indicator
              [(schema-indicator->db-indicator judgement-id indicator-rel)])
    indicator-rel))
