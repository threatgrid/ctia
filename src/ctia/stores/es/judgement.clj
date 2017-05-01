(ns ctia.stores.es.judgement
  (:require [clj-momo.lib.es.document :refer [search-docs update-doc]]
            [clj-momo.lib.time :as time]
            [ctia.schemas.core :refer [StoredJudgement Verdict]]
            [ctia.stores.es
             [crud :as crud]
             [query :refer [active-judgements-by-observable-query]]]
            [ctim.schemas.common :refer [disposition-map]]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]))

(def ^{:private true} judgement-mapping "judgement")

(def coerce-stored-judgement-list
  (c/coercer! [(s/maybe StoredJudgement)]
              sc/json-schema-coercion-matcher))

(def handle-create (crud/handle-create :judgement StoredJudgement))
(def handle-read (crud/handle-read :judgement StoredJudgement))
(def handle-delete (crud/handle-delete :judgement StoredJudgement))
(def handle-list (crud/handle-find :judgement StoredJudgement))
(def handle-query-string-search (crud/handle-query-string-search :judgement StoredJudgement))

(defn handle-add-indicator-to
  "add an indicator relation to a judgement"
  [state judgement-id indicator-rel]

  (let [judgement (handle-read state judgement-id)
        indicator-rels (:indicators judgement)
        updated-rels (conj indicator-rels indicator-rel)
        updated {:indicators (set updated-rels)}]

    (update-doc (:conn state)
                (:index state)
                judgement-mapping
                judgement-id
                updated
                (get-in state [:props :refresh] false))
    indicator-rel))


(defn list-active-by-observable
  [state observable]
  (let [now-str (-> (time/now) time/format-date-time)

        params
        {:sort
         {:priority
          "desc"

          :disposition
          "asc"

          "valid_time.start_time"
          {:order "asc"
           :mode "min"
           :nested_filter
           {"range" {"valid_time.start_time" {"lte" now-str}}}}}}]

    (some->> (search-docs (:conn state)
                          (:index state)
                          judgement-mapping
                          (active-judgements-by-observable-query observable
                                                                 now-str)
                          nil
                          params)
             :data
             coerce-stored-judgement-list)))

(s/defn make-verdict :- Verdict
  [judgement :- StoredJudgement]
  {:type "verdict"
   :disposition (:disposition judgement)
   :disposition_name (get disposition-map (:disposition judgement))
   :judgement_id (:id judgement)
   :observable (:observable judgement)
   :valid_time (:valid_time judgement)})

(s/defn handle-calculate-verdict :- (s/maybe Verdict)
  [state observable]

  (some-> (list-active-by-observable state observable)
          first
          make-verdict))
