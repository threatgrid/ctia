(ns ctia.domain.entities
  (:require
   [clj-momo.lib.time :as time]
   [ctia.properties :refer [get-http-show]]
   [ctim.domain.id :as id]
   [ring.util.http-response :as http-response]
   [schema.core :as s]
   [ctim.schemas.common
    :refer [ctim-schema-version
            default-tlp
            determine-disposition-id
            disposition-map]]
   [ctia.schemas.core
    :refer [NewActor
            StoredActor
            NewCampaign
            StoredCampaign
            NewCOA
            StoredCOA
            NewDataTable
            StoredDataTable
            NewExploitTarget
            StoredExploitTarget
            NewFeedback
            StoredFeedback
            NewIncident
            StoredIncident
            NewIndicator
            StoredIndicator
            NewJudgement
            StoredJudgement
            NewSighting
            StoredSighting
            NewTTP
            StoredTTP
            NewRelationship
            StoredRelationship
            Verdict
            StoredVerdict]])
  (:import [java.util UUID]))

(def schema-version ctim-schema-version)

(defn default-realize-fn [type-name Model StoredModel]
  (s/fn default-realize :- StoredModel
    ([new-object :- Model
      id :- s/Str
      login :- s/Str]
     (default-realize new-object id login nil))
    ([new-object :- Model
      id :- s/Str
      login :- s/Str
      prev-object :- (s/maybe StoredModel)]
     (let [now (time/now)]
       (assoc new-object
              :id id
              :type type-name
              :owner login
              :schema_version schema-version
              :created (or (:created prev-object) now)
              :modified now
              :tlp (:tlp new-object (:tlp prev-object default-tlp))
              :valid_time (or (:valid_time prev-object)
                              {:end_time (or (get-in new-object [:valid_time :end_time])
                                             time/default-expire-date)
                               :start_time (or (get-in new-object [:valid_time :start_time])
                                               now)}))))))

(def realize-actor
  (default-realize-fn "actor" NewActor StoredActor))

(def realize-campaign
  (default-realize-fn "campaign" NewCampaign StoredCampaign))

(def realize-coa
  (default-realize-fn "coa" NewCOA StoredCOA))

(def realize-data-table
  (default-realize-fn "data-table" NewDataTable StoredDataTable))

(def realize-exploit-target
  (default-realize-fn "exploit-target" NewExploitTarget StoredExploitTarget))

(s/defn realize-feedback :- StoredFeedback
  [new-feedback :- NewFeedback
   id :- s/Str
   login :- s/Str]
  (assoc new-feedback
         :id id
         :type "feedback"
         :created (time/now)
         :owner login
         :tlp (:tlp new-feedback default-tlp)
         :schema_version schema-version))

(def realize-incident
  (default-realize-fn "incident" NewIncident StoredIncident))

(def realize-indicator
  (default-realize-fn "indicator" NewIndicator StoredIndicator))

(s/defn realize-relationship :- StoredRelationship
  [new-relationship :- NewRelationship
   id :- s/Str
   login :- s/Str]
  (assoc new-relationship
         :id id
         :type "relationship"
         :created (time/now)
         :owner login
         :tlp (:tlp new-relationship default-tlp)
         :schema_version schema-version))


(s/defn realize-judgement :- StoredJudgement
  [new-judgement :- NewJudgement
   id :- s/Str
   login :- s/Str]
  (let [now (time/now)
        disposition (try
                      (determine-disposition-id new-judgement)
                      (catch clojure.lang.ExceptionInfo _
                        (throw
                         (http-response/bad-request!
                          {:error "Mismatching :dispostion and dispositon_name for judgement"
                           :judgement new-judgement}))))
        disposition_name (get disposition-map disposition)]
    (assoc new-judgement
           :id id
           :type "judgement"
           :disposition disposition
           :disposition_name disposition_name
           :owner login
           :created now
           :tlp (:tlp new-judgement default-tlp)
           :schema_version schema-version
           :valid_time {:end_time (or (get-in new-judgement [:valid_time :end_time])
                                      time/default-expire-date)
                        :start_time (or (get-in new-judgement [:valid_time :start_time])
                                        now)})))

(s/defn realize-verdict :- StoredVerdict
  ([new-verdict :- Verdict
    login :- s/Str]
   (realize-verdict new-verdict (str "verdict-" (UUID/randomUUID)) login))
  ([new-verdict :- Verdict
    id :- s/Str
    login :- s/Str]
   (let [now (time/now)]
     (assoc new-verdict
            :id id
            :schema_version schema-version
            :created now))))

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str]
   (realize-sighting new-sighting id login nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    login :- s/Str
    prev-sighting :- (s/maybe StoredSighting)]
   (let [now (time/now)]
     (assoc new-sighting
            :id id
            :type "sighting"
            :owner login
            :count (:count new-sighting
                           (:count prev-sighting 1))
            :confidence (:confidence new-sighting
                                     (:confidence prev-sighting "Unknown"))
            :tlp (:tlp new-sighting
                       (:tlp prev-sighting default-tlp))
            :schema_version schema-version
            :created (or (:created prev-sighting) now)
            :modified now))))

(def realize-ttp
  (default-realize-fn "ttp" NewTTP StoredTTP))

(def ->long-id (id/factory:short-id+type->long-id get-http-show))

(defn un-store [m]
  (dissoc m :created :modified :owner))

(defn un-store-all [x]
  (if (sequential? x)
    (map un-store x)
    (un-store x)))

(defn un-store-page [page]
  (update page :data un-store-all))
