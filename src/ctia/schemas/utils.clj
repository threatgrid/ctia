(ns ctia.schemas.utils
  (:require [schema-tools.walk :as sw]
            [schema-tools.util :as stu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(defn recursive-open-schema-version
  "walk a schema and replace all instances of schema version
   with an open string schema"
  [s]
  (sw/walk
   (fn [x]
     (if (and
          (instance? clojure.lang.IMapEntry x)
          (= :schema_version (:k (first x))))
       [(first x) s/Str]
       (recursive-open-schema-version x)))
   identity
   s))


;; fns below are backported from schema-tools master
;; from https://github.com/metosin/schema-tools/blob/master/src/schema_tools/core.cljc#L266
;; TODO those can be removed once we use 0.10.6

(defn- explicit-key [k] (if (s/specific-key? k) (s/explicit-schema-key k) k))

(defn- explicit-key-set [ks]
  (reduce (fn [s k] (conj s (explicit-key k))) #{} ks))

(defn- maybe-anonymous [original current]
  (if (= original current)
    original
    (vary-meta
     current
     (fn [meta]
       (let [new-meta (clojure.core/dissoc meta :name :ns)]
         (if (empty? new-meta)
           nil
           new-meta))))))

(defn- transform-keys
  [schema f ks]
  (assert (or (not ks) (vector? ks)) "input should be nil or a vector of keys.")
  (maybe-anonymous
   schema
   (let [ks? (explicit-key-set ks)]
     (stu/map-keys
      (fn [k]
        (cond
          (and ks (not (ks? (explicit-key k)))) k
          (s/specific-key? k) (f (s/explicit-schema-key k))
          :else k))
      schema))))

(defn optional-keys
  "Makes given map keys optional. Defaults to all keys."
  ([m] (optional-keys m nil))
  ([m ks] (transform-keys m s/optional-key ks)))

(defn optional-keys-schema
  "Walks a schema making all keys optional in Map Schemas."
  [schema]
  (sw/prewalk
   (fn [x]
     (if (and (map? x) (not (record? x)))
       (optional-keys x)
       x))
   schema))
