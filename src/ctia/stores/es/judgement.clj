(ns ctia.stores.es.judgement
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.common :refer [disposition-map]]
   [ctia.schemas.verdict :refer [Verdict]]
   [ctia.schemas.judgement :refer [Judgement
                                   NewJudgement
                                   StoredJudgement
                                   realize-judgement]]
   [ctia.stores.es.query :refer
    [unexpired-judgements-by-observable-query]]

   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs
                                    raw-search-docs]]))

(def ^{:private true} mapping "judgement")

(def coerce-stored-judgement
  (c/coercer! (s/maybe StoredJudgement)
              sc/json-schema-coercion-matcher))

(defn handle-create-judgement [state login realized-new-judgement]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-new-judgement)
      coerce-stored-judgement))

(defn handle-read-judgement [state id]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               id)
      coerce-stored-judgement))

(defn handle-add-indicator-to-judgement
  "add an indicator relation to a judgement"
  [state judgement-id indicator-rel]

  (let [judgement (handle-read-judgement state judgement-id)
        indicator-rels (:indicators judgement)
        updated-rels (conj indicator-rels indicator-rel)
        updated {:indicators (set updated-rels)}]

    (update-doc (:conn state)
                (:index state)
                mapping
                judgement-id
                updated)
    indicator-rel))

(defn handle-delete-judgement [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-judgements [state filter-map]
  (->> (search-docs (:conn state)
                    (:index state)
                    mapping
                    filter-map)
       (map coerce-stored-judgement)))

(defn list-unexpired-judgements-by-observable
  [state observable]
  (let [sort {:priority "desc"
              :disposition "asc"
              "valid_time.start_time"
              {:order "asc"
               :mode "min"
               :nested_filter
               {"range" {"valid_time.start_time" {"lt" "now/d"}}}}}
        query
        (unexpired-judgements-by-observable-query observable)]

    (->> (raw-search-docs (:conn state)
                          (:index state)
                          mapping
                          query
                          sort)

         (map coerce-stored-judgement))))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:type "verdict"
   :disposition (:disposition judgement)
   :judgement_id (:id judgement)
   :disposition_name (get disposition-map (:disposition judgement))})

(s/defn handle-calculate-verdict :- Verdict
  [state observable]
  (let [verdict
        (->> (list-unexpired-judgements-by-observable state observable)
             first
             make-verdict)]

    (when (:judgement_id verdict)
      verdict)))
