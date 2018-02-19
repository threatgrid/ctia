(ns ctia.domain.entities
  (:require
   [clj-momo.lib.time :as time]
   [ctia.domain.access-control :refer [properties-default-tlp]]
   [ctia.properties :refer [get-http-show]]
   [ctim.domain.id :as id]
   [ring.util.http-response :as http-response]
   [schema.core :as s]
   [ctim.schemas.common
    :refer [ctim-schema-version
            default-tlp
            determine-disposition-id
            disposition-map]]
   [ctia.domain.entities
    [actor :as act-ent]
    [attack-pattern :as attack-ent]
    [campaign :as cam-ent]
    [coa :as coa-ent]
    [exploit-target :as ept-ent]
    [data-table :as dt-ent]
    [feedback :as fbk-ent]
    [incident :as inc-ent]
    [indicator :as ind-ent]
    [investigation :as inv-ent]
    [judgement :as jud-ent]
    [malware :as malware-ent]
    [relationship :as rel-ent]
    [sighting :as sig-ent]
    [tool :as tool-ent]]
   [ctia.schemas.core
    :as ctia-schemas
    :refer [NewActor
            StoredActor
            NewAttackPattern
            StoredAttackPattern
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
            NewInvestigation
            StoredInvestigation
            NewJudgement
            StoredJudgement
            NewMalware
            StoredMalware
            NewScratchpad
            StoredScratchpad
            NewSighting
            StoredSighting
            NewTool
            StoredTool
            NewRelationship
            StoredRelationship
            TempIDs]])
  (:import [java.util UUID]))

(def schema-version ctim-schema-version)

(defn contains-key?
  "Returns true if the schema contains the given key, false otherwise."
  [schema k]
  (or (contains? schema (s/optional-key k))
      (contains? schema (s/required-key k))
      (contains? schema k)))

(defn default-realize-fn [type-name Model StoredModel]
  (s/fn default-realize :- StoredModel
    ([new-object :- Model
      id :- s/Str
      tempids :- (s/maybe TempIDs)
      owner :- s/Str
      groups :- [s/Str]]
     (default-realize new-object id tempids owner groups nil))
    ([new-object :- Model
      id :- s/Str
      tempids :- (s/maybe TempIDs)
      owner :- s/Str
      groups :- [s/Str]
      prev-object :- (s/maybe StoredModel)]
     (let [now (time/now)]
       (merge new-object
              {:id id
               :type type-name
               :owner (or (:owner prev-object) owner)
               :groups (or (:groups prev-object) groups)
               :schema_version schema-version
               :created (or (:created prev-object) now)
               :modified now
               :tlp (:tlp new-object
                          (:tlp prev-object (properties-default-tlp)))}
              (when (contains-key? Model :valid_time)
                {:valid_time (or (:valid_time prev-object)
                                 {:end_time (or (get-in new-object [:valid_time :end_time])
                                                time/default-expire-date)
                                  :start_time (or (get-in new-object [:valid_time :start_time])
                                                  now)})}))))))

(def realize-actor
  (default-realize-fn "actor" NewActor StoredActor))

(def realize-attack-pattern
  (default-realize-fn "attack-pattern" NewAttackPattern StoredAttackPattern))

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
   tempids :- (s/maybe TempIDs)
   owner :- s/Str
   groups :- [s/Str]]
  (assoc new-feedback
         :id id
         :type "feedback"
         :created (time/now)
         :owner owner
         :groups groups
         :tlp (:tlp new-feedback (properties-default-tlp))
         :schema_version schema-version))

(def realize-incident
  (default-realize-fn "incident" NewIncident StoredIncident))

(def realize-indicator
  (default-realize-fn "indicator" NewIndicator StoredIndicator))

(def realize-investigation
  (default-realize-fn "investigation" NewInvestigation StoredInvestigation))

(def realize-scratchpad
  (default-realize-fn "scratchpad" NewScratchpad StoredScratchpad))

(def relationship-default-realize
  (default-realize-fn "relationship" NewRelationship StoredRelationship))

(s/defn realize-relationship
  :- StoredRelationship
  [{:keys [source_ref
           target_ref]
    :as new-entity}
   id
   tempids
   & rest-args]
  (assoc (apply relationship-default-realize new-entity id tempids rest-args)
         :source_ref (get tempids source_ref source_ref)
         :target_ref (get tempids target_ref target_ref)))

(s/defn realize-judgement :- StoredJudgement
  [new-judgement :- NewJudgement
   id :- s/Str
   tempids :- (s/maybe TempIDs)
   owner :- s/Str
   groups :- [s/Str]]
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
           :owner owner
           :groups groups
           :created now
           :tlp (:tlp new-judgement (properties-default-tlp))
           :schema_version schema-version
           :valid_time {:end_time (or (get-in new-judgement
                                              [:valid_time :end_time])
                                      time/default-expire-date)
                        :start_time (or (get-in new-judgement
                                                [:valid_time :start_time])
                                        now)})))

(def realize-malware
  (default-realize-fn "malware" NewMalware StoredMalware))

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]]
   (realize-sighting new-sighting id tempids owner groups nil))
  ([new-sighting :- NewSighting
    id :- s/Str
    tempids :- (s/maybe TempIDs)
    owner :- s/Str
    groups :- [s/Str]
    prev-sighting :- (s/maybe StoredSighting)]
   (let [now (time/now)]
     (assoc new-sighting
            :id id
            :type "sighting"
            :owner (or (:owner prev-sighting) owner)
            :groups (or (:groups prev-sighting) groups)
            :count (:count new-sighting
                           (:count prev-sighting 1))
            :confidence (:confidence new-sighting
                                     (:confidence prev-sighting "Unknown"))
            :tlp (:tlp new-sighting
                       (:tlp prev-sighting (properties-default-tlp)))
            :schema_version schema-version
            :created (or (:created prev-sighting) now)
            :modified now))))

(def realize-tool
  (default-realize-fn "tool" NewTool StoredTool))

(def ->long-id (id/factory:short-id+type->long-id get-http-show))

(defn un-store [m]
  (dissoc m
          :created
          :modified
          :owner
          :groups))

(defn un-store-all [x]
  (if (sequential? x)
    (map un-store x)
    (un-store x)))

(defn un-store-page [page]
  (update page :data un-store-all))

(defn un-store-map [m]
  (into {}
        (map (fn [[k v]]
               [k (un-store-all v)])
             m)))

(def realize-fn
  {:actor          realize-actor
   :attack-pattern realize-attack-pattern
   :campaign       realize-campaign
   :coa            realize-coa
   :data-table     realize-data-table
   :exploit-target realize-exploit-target
   :feedback       realize-feedback
   :incident       realize-incident
   :indicator      realize-indicator
   :investigation  realize-investigation
   :judgement      realize-judgement
   :malware        realize-malware
   :relationship   realize-relationship
   :sighting       realize-sighting
   :tool           realize-tool})

(def page-with-long-id-fn
  {:actor          act-ent/page-with-long-id
   :attack-pattern attack-ent/page-with-long-id
   :campaign       cam-ent/page-with-long-id
   :coa            coa-ent/page-with-long-id
   :data-table     dt-ent/page-with-long-id
   :exploit-target ept-ent/page-with-long-id
   :feedback       fbk-ent/page-with-long-id
   :incident       inc-ent/page-with-long-id
   :indicator      ind-ent/page-with-long-id
   :investigation  inv-ent/page-with-long-id
   :judgement      jud-ent/page-with-long-id
   :malware        malware-ent/page-with-long-id
   :relationship   rel-ent/page-with-long-id
   :sighting       sig-ent/page-with-long-id
   :tool           tool-ent/page-with-long-id})

(def with-long-id-fn
  {:actor          act-ent/with-long-id
   :attack-pattern attack-ent/with-long-id
   :campaign       cam-ent/with-long-id
   :coa            coa-ent/with-long-id
   :data-table     dt-ent/with-long-id
   :exploit-target ept-ent/with-long-id
   :feedback       fbk-ent/with-long-id
   :incident       inc-ent/with-long-id
   :indicator      ind-ent/with-long-id
   :investigation  inv-ent/with-long-id
   :judgement      jud-ent/with-long-id
   :malware        malware-ent/with-long-id
   :relationship   rel-ent/with-long-id
   :sighting       sig-ent/with-long-id
   :tool           tool-ent/with-long-id})
