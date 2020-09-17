(ns ctia.domain.entities
  (:require [clj-momo.lib.time :as time]
            [ctia.domain.access-control :refer [properties-default-tlp]]
            [ctia.properties :refer [get-http-show]]
            [ctia.schemas.core :as ctia-schemas
             :refer [GraphQLRuntimeOptions
                     MaybeDelayedRealizeFn
                     MaybeDelayedRealizeFnResult
                     TempIDs]]
            [ctim.domain.id :as id]
            [ctim.schemas.common :refer [ctim-schema-version]]
            [schema.core :as s]))

(def schema-version ctim-schema-version)

(defn contains-key?
  "Returns true if the schema contains the given key, false otherwise."
  [schema k]
  (or (contains? schema (s/optional-key k))
      (contains? schema (s/required-key k))
      (contains? schema k)))

(defn make-valid-time
  "make a valid-time object from either in this order:
  the newest value, the previous value or the default value"
  [prev-valid-time new-valid-time now]
  {:valid_time {:start_time
                (or (:start_time new-valid-time)
                    (:start_time prev-valid-time)
                    now)
                :end_time
                (or (:end_time new-valid-time)
                    (:end_time prev-valid-time)
                    time/default-expire-date)}})

(s/defn default-realize-fn
  ;; commented since StoredModel is a parameter and not in scope here,
  ;; checking is propagated to the body via s/fn
  #_#_:- (MaybeDelayedRealizeFn StoredModel)
  [type-name
   Model :- (s/protocol s/Schema)
   StoredModel  :- (s/protocol s/Schema)]
  (s/fn default-realize :- (MaybeDelayedRealizeFnResult StoredModel)
    ([new-object :- Model
      id :- s/Str
      tempids :- (s/maybe TempIDs)
      owner :- s/Str
      groups :- [s/Str]]
     (default-realize new-object id tempids owner groups nil))
    ([new-object :- Model
      id :- s/Str
      _ :- (s/maybe TempIDs)
      owner :- s/Str
      groups :- [s/Str]
      prev-object :- (s/maybe StoredModel)]
    (s/fn :- StoredModel
     [{{{:keys [get-in-config]} :ConfigService} :services} :- GraphQLRuntimeOptions]
     (let [now (time/now)]
       (merge new-object
              {:id id
               :type type-name
               :owner (or (:owner prev-object) owner)
               :groups (or (:groups prev-object) groups)
               :schema_version schema-version
               :created (or (:created prev-object) now)
               :modified now
               :timestamp (or (:timestamp new-object) now)
               :tlp (:tlp new-object
                          (:tlp prev-object (properties-default-tlp get-in-config)))}
              (when (contains-key? Model :valid_time)
                (make-valid-time (:valid_time prev-object)
                                 (:valid_time new-object)
                                 now))))))))

(defn short-id->long-id [id get-in-config]
  (id/short-id->long-id id #(get-http-show get-in-config)))

(defn long-id->id [id]
  (id/long-id->id id))

(defn with-long-id [entity get-in-config]
  (update entity :id short-id->long-id get-in-config))

(defn page-with-long-id [m get-in-config]
  (update m :data #(map (fn [entity]
                          (with-long-id entity get-in-config))
                        %)))

(defn long-id->entity-type [id-str]
  (:type (id/long-id->id id-str)))

(defn short-id->entity-type [id-str get-in-config]
  (when-let [short-id (id/short-id->long-id
                       id-str
                       #(get-http-show get-in-config))]
    (:type (id/long-id->id short-id))))

(defn id->entity-type
  "Extract the entity type from an id"
  [id-str get-in-config]
  (if (id/short-id? id-str)
    (short-id->entity-type id-str get-in-config)
    (long-id->entity-type id-str)))

(defn un-store [record]
  (apply dissoc record [:created :modified]))

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
