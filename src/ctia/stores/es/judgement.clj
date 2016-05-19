(ns ctia.stores.es.judgement
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]

   [ctia.stores.es.crud :as crud]

   [ring.swagger.coerce :as sc]
   [ctia.schemas.common :refer [disposition-map]]
   [ctia.schemas.verdict :refer [Verdict]]
   [ctia.schemas.judgement :refer [Judgement
                                   NewJudgement
                                   StoredJudgement
                                   realize-judgement]]
   [ctia.stores.es.query :refer
    [active-judgements-by-observable-query]]

   [ctia.lib.es.document :refer [update-doc
                                 delete-doc
                                 search-docs]]))

(def ^{:private true} mapping "judgement")

(def coerce-stored-judgement-list
  (c/coercer! [(s/maybe StoredJudgement)]
              sc/json-schema-coercion-matcher))

(def handle-create-judgement (crud/handle-create :judgement StoredJudgement))
(def handle-read-judgement (crud/handle-read :judgement StoredJudgement))
(def handle-delete-judgement (crud/handle-delete :judgement StoredJudgement))
(def handle-list-judgements (crud/handle-find :judgement StoredJudgement))

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
                updated
                (get-in state [:props :refresh] false))
    indicator-rel))


(defn list-active-judgements-by-observable
  [state observable]
  (let [params {:query (active-judgements-by-observable-query observable)
                :sort {:priority "desc"
                       :disposition "asc"
                       "valid_time.start_time" {:order "asc"
                                                :mode "min"
                                                :nested_filter
                                                {"range" {"valid_time.start_time" {"lt" "now/d"}}}}}}]

    (some->> (search-docs (:conn state)
                          (:index state)
                          mapping
                          nil
                          params)
             :data
             coerce-stored-judgement-list)))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:type "verdict"
   :disposition (:disposition judgement)
   :judgement_id (:id judgement)
   :disposition_name (get disposition-map (:disposition judgement))})

(s/defn handle-calculate-verdict :- (s/maybe Verdict)
  [state observable]

  (some-> (list-active-judgements-by-observable state observable)
          first
          make-verdict))
