(ns ctia.domain.entities
  (:require [clj-momo.lib.time :as time]
            [ctia.domain.access-control :refer [properties-default-tlp]]
            [ctia.properties :refer [get-http-show]]
            [ctia.schemas.core :as ctia-schemas :refer [TempIDs]]
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
                (make-valid-time (:valid_time prev-object)
                                 (:valid_time new-object)
                                 now)))))))

(defn short-id->long-id [id]
  (id/short-id->long-id id get-http-show))

(defn with-long-id [entity]
  (update entity :id short-id->long-id))

(defn page-with-long-id [m]
  (update m :data #(map with-long-id %)))

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
