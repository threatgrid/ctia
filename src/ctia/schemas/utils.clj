(ns ctia.schemas.utils
  (:require [schema-tools.walk :as sw]
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
