(ns ctia.entity.judgement.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [access-control :refer [properties-default-tlp]]
             [entities :refer [default-realize-fn]]]
            [ctia.schemas
             [utils :as csu]
             [core :refer [def-acl-schema def-stored-schema TempIDs]]
             [sorting :as sorting]]
            [ctim.schemas
             [common :refer [determine-disposition-id disposition-map]]
             [judgement :as js]]
            [flanders.utils :as fu]
            [ring.util.http-response :as http-response]
            [schema-tools.core :as st]
            [schema.core :as s]
            [ctim.schemas.incident :as is]
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

(s/defn realize-judgement :- (with-error StoredJudgement)
  ([new-judgement id tempids owner groups]
   (realize-judgement new-judgement id tempids owner groups nil))
  ([new-judgement :- NewJudgement
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-judgement :- (s/maybe StoredJudgement)]
   (try
     (let [disposition (determine-disposition-id new-judgement)
           disposition-name (get disposition-map disposition)]
       (judgement-default-realize
        (assoc new-judgement
               :disposition disposition
               :disposition_name disposition-name)
        id tempids owner groups prev-judgement))
     (catch clojure.lang.ExceptionInfo e
       (let [{error-type :type} (ex-data e)]
         (if (= error-type :ctim.schemas.common/disposition-missing)
           {:error "Mismatching disposition and dispositon_name for judgement"
            :id id
            :type :realize-entity-error
            :judgement new-judgement}
           (throw e)))))))

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

(def judgements-by-observable-sort-fields
  (map name (conj judgement-fields
                  "disposition:asc,valid_time.start_time:desc"
                  "disposition:asc,valid_time.start_time:desc,timestamp:desc")))
