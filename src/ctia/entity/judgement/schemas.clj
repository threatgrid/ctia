(ns ctia.entity.judgement.schemas
  (:require [clj-momo.lib.time :as time]
            [ctia.domain
             [access-control :refer [properties-default-tlp]]
             [entities :refer [schema-version make-valid-time]]]
            [ctia.schemas
             [core :refer [def-acl-schema def-stored-schema TempIDs]]
             [sorting :as sorting]]
            [ctim.schemas
             [common :refer [determine-disposition-id disposition-map]]
             [judgement :as js]]
            [flanders.utils :as fu]
            [ring.util.http-response :as http-response]
            [schema.core :as s]))

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
  js/StoredJudgement
  "stored-judgement")

(def-stored-schema PartialStoredJudgement
  (fu/optionalize-all js/StoredJudgement)
  "partial-stored-judgement")

(s/defn realize-judgement :- StoredJudgement
  ([new-judgement :- NewJudgement
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]]
   (realize-judgement new-judgement id tempids owner groups nil))
  ([new-judgement :- NewJudgement
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-object :- (s/maybe StoredJudgement)]
   (let [now (time/now)
         disposition (try
                       (determine-disposition-id new-judgement)
                       (catch clojure.lang.ExceptionInfo _
                         (throw
                          (http-response/bad-request!
                           {:error "Mismatching :dispostion and dispositon_name for judgement"
                            :judgement new-judgement}))))
         disposition_name (get disposition-map disposition)]
     (merge new-judgement
            {:id id
             :type "judgement"
             :disposition disposition
             :disposition_name disposition_name
             :owner (or (:owner prev-object) owner)
             :groups (or (:groups prev-object) groups)
             :created (or (:created prev-object) now)
             :tlp (:tlp new-judgement
                        (:tlp prev-object (properties-default-tlp)))
             :schema_version schema-version}
            (make-valid-time (:valid_time prev-object)
                             (:valid_time new-judgement)
                             now)))))

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


