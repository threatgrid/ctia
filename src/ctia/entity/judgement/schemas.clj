(ns ctia.entity.judgement.schemas
  (:require [ctia.domain
             [entities :refer [default-realize-fn]]]
            [ctia.graphql.delayed :as delayed]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema
                           def-stored-schema
                           GraphQLRuntimeContext
                           TempIDs
                           RealizeFnResult
                           lift-realize-fn-with-context]]
             [sorting :as sorting]]
            [ctim.schemas
             [common :refer [determine-disposition-id disposition-map]]
             [judgement :as js]]
            [flanders.utils :as fu]
            [schema.core :as s]
            [ctia.flows.schemas :refer [with-error]]))

(def-acl-schema Judgement
  js/Judgement
  "judgement")

(def-acl-schema PartialJudgement
  (fu/optionalize-all js/Judgement)
  "partial-judgement")

(s/defschema PartialJudgementList
  [PartialJudgement])

(def-acl-schema NewJudgement
  js/NewJudgement
  "new-judgement")

(def-stored-schema StoredJudgement
  Judgement)

(s/defschema PartialStoredJudgement
  (csu/optional-keys-schema StoredJudgement))

(def judgement-default-realize
  (default-realize-fn "judgement" NewJudgement StoredJudgement))

(s/defn realize-judgement :- (RealizeFnResult (with-error StoredJudgement))
  ([new-judgement id tempids owner groups]
   (realize-judgement new-judgement id tempids owner groups nil))
  ([new-judgement :- NewJudgement
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-judgement :- (s/maybe StoredJudgement)]
  (delayed/fn :- (with-error StoredJudgement)
   [rt-ctx :- GraphQLRuntimeContext]
   (try
     (let [disposition (determine-disposition-id new-judgement)
           disposition-name (get disposition-map disposition)
           judgement-default-realize (-> judgement-default-realize
                                         (lift-realize-fn-with-context rt-ctx))]
       (judgement-default-realize
        (assoc new-judgement
               :disposition disposition
               :disposition_name disposition-name)
        id tempids owner groups prev-judgement))
     (catch clojure.lang.ExceptionInfo e
       (let [{error-type :type} (ex-data e)]
         (if (= error-type :ctim.schemas.common/disposition-missing)
           {:error "Mismatched disposition and dispositon_name for judgement"
            :id id
            :type :realize-entity-error
            :judgement new-judgement}
           (throw e))))))))

(def judgement-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:disposition
           :priority
           :confidence
           :severity
           :valid_time.start_time
           :valid_time.end_time
           :reason
           :observable.type
           :observable.value]))

(def judgement-sort-fields
  (concat judgement-fields
          ["valid_time.start_time,timestamp"]))

(def judgement-enumerable-fields
  [:disposition
   :priority
   :confidence
   :severity
   :observable.type
   :observable.value])

(def judgement-histogram-fields
  [:timestamp
   :valid_time.start_time
   :valid_time.end_time])

(def judgements-by-observable-sort-fields
  (map name (conj judgement-fields
                  "disposition:asc,valid_time.start_time:desc"
                  "disposition:asc,valid_time.start_time:desc,timestamp:desc")))
