(ns ctia.domain.entities
  (:require
    [clj-momo.lib.time :as time]
    [ctim.schemas
     [actor :refer [NewActor StoredActor]]
     [campaign :refer [NewCampaign StoredCampaign]]
     [common :as c]
     [coa :refer [NewCOA StoredCOA]]
     [exploit-target :refer [NewExploitTarget StoredExploitTarget]]
     [feedback :refer [NewFeedback StoredFeedback]]
     [incident :refer [NewIncident StoredIncident]]
     [indicator :refer [NewIndicator StoredIndicator]]
     [judgement :refer [NewJudgement StoredJudgement]]
     [sighting :refer [NewSighting StoredSighting]]
     [ttp :refer [NewTTP StoredTTP]]
     [verdict :refer [Verdict StoredVerdict]]]
    [ring.util.http-response :as http-response]
    [schema.core :as s])
  (:import [java.util UUID]))

(def schema-version c/ctim-schema-version)

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
              :tlp (:tlp new-object (:tlp prev-object c/default-tlp))
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
         :tlp (:tlp new-feedback c/default-tlp)
         :schema_version schema-version))

(def realize-incident
  (default-realize-fn "incident" NewIncident StoredIncident))

(def realize-indicator
  (default-realize-fn "indicator" NewIndicator StoredIndicator))

(s/defn realize-judgement :- StoredJudgement
  [new-judgement :- NewJudgement
   id :- s/Str
   login :- s/Str]
  (let [now (time/now)
        disposition (try
                      (c/determine-disposition-id new-judgement)
                      (catch clojure.lang.ExceptionInfo _
                        (throw
                         (http-response/bad-request!
                          {:error "Mismatching :dispostion and dispositon_name for judgement"
                           :judgement new-judgement}))))
    disposition_name (get c/disposition-map disposition)]
  (assoc new-judgement
         :id id
         :type "judgement"
         :disposition disposition
         :disposition_name disposition_name
         :owner login
         :created now
         :tlp (:tlp new-judgement c/default-tlp)
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
                       (:tlp prev-sighting c/default-tlp))
            :schema_version schema-version
            :created (or (:created prev-sighting) now)
            :modified now))))

(s/defn check-new-sighting :- s/Bool
  "We need either an observable or an indicator,
   as a Sighting is useless without one of them."
  [sighting :- NewSighting]
  (not (and (empty? (:observables sighting))
            (empty? (:indicators sighting)))))

(def realize-ttp
  (default-realize-fn "ttp" NewTTP StoredTTP))
