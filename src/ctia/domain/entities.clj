(ns ctia.domain.entities
  (:require [clj-momo.lib.time :as time]
            [ctia.domain.access-control :refer [properties-default-tlp]]
            [ctia.properties :refer [get-http-show]]
            [ctia.schemas.core
             :refer
             [NewActor
              NewCampaign
              NewCOA
              NewDataTable
              NewExploitTarget
              NewFeedback
              NewIncident
              NewIndicator
              NewJudgement
              NewRelationship
              NewSighting
              NewTTP
              StoredActor
              StoredCampaign
              StoredCOA
              StoredDataTable
              StoredExploitTarget
              StoredFeedback
              StoredIncident
              StoredIndicator
              StoredJudgement
              StoredRelationship
              StoredSighting
              StoredTTP]]
            [ctim.domain.id :as id]
            [ctim.schemas.common
             :refer
             [ctim-schema-version determine-disposition-id disposition-map]]
            [ring.util.http-response :as http-response]
            [schema.core :as s]))

(def schema-version ctim-schema-version)

(defn default-realize-fn [type-name Model StoredModel]
  (s/fn default-realize :- StoredModel
    ([new-object :- Model
      id :- s/Str
      owner :- s/Str
      groups :- [s/Str]]
     (default-realize new-object id owner groups nil))
    ([new-object :- Model
      id :- s/Str
      owner :- s/Str
      groups :- [s/Str]
      prev-object :- (s/maybe StoredModel)]
     (let [now (time/now)]
       (assoc new-object
              :id id
              :type type-name
              :owner (or (:owner prev-object) owner)
              :groups (or (:groups prev-object) groups)
              :schema_version schema-version
              :created (or (:created prev-object) now)
              :modified now
              :tlp (:tlp new-object
                         (:tlp prev-object (properties-default-tlp)))
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

(s/defn realize-relationship :- StoredRelationship
  [new-relationship :- NewRelationship
   id :- s/Str
   owner :- s/Str
   groups :- [s/Str]]
  (assoc new-relationship
         :id id
         :type "relationship"
         :created (time/now)
         :owner owner
         :groups groups
         :tlp (:tlp new-relationship (properties-default-tlp))
         :schema_version schema-version))


(s/defn realize-judgement :- StoredJudgement
  [new-judgement :- NewJudgement
   id :- s/Str
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

(s/defn realize-sighting :- StoredSighting
  ([new-sighting :- NewSighting
    id :- s/Str
    owner :- s/Str
    groups :- [s/Str]]
   (realize-sighting new-sighting id owner groups nil))
  ([new-sighting :- NewSighting
    id :- s/Str
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

(def realize-ttp
  (default-realize-fn "ttp" NewTTP StoredTTP))

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
