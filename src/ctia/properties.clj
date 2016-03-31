(ns ^{:doc "Properties (aka configuration) of the application.
            Properties are stored in property files.  There is a
            default file which can be overridden by placing an
            alternative properties file on the classpath, or by
            setting system properties."}
    ctia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ctia.lib.map :as map]
            [schema.coerce :as c]
            [schema.core :as s]
            [clj-time.coerce :as coerce])
  (:import java.util.Properties))

(def property-files
  "Property file names, in order of preference"
  ["ctia.properties"
   "ctia-default.properties"])

(defonce properties
  (atom {}))

(s/defschema PropertiesSchema
  "This is the schema used for value type coercion.  It is also used
   for validating the properties that are read in so that required
   properties must be present.  Only the following properties may be
   set.  This is also used for selecting system properties to merge
   with the properties file."
  {(s/required-key "auth.service.type") s/Keyword
   (s/optional-key "auth.service.threatgrid.url") s/Str
   (s/optional-key "ctia.store.sql.db.classname") s/Str
   (s/optional-key "ctia.store.sql.db.subprotocol") s/Str
   (s/optional-key "ctia.store.sql.db.subname") s/Str
   (s/optional-key "ctia.store.sql.db.delimiters") s/Str
   (s/optional-key "ctia.store.es.host") s/Str
   (s/optional-key "ctia.store.es.port") s/Int
   (s/optional-key "ctia.store.es.clustername") s/Str
   (s/optional-key "ctia.store.es.indexname") s/Str})

(def configurable-properties
  "String keys from PropertiesSchema, used to select system properties."
  (map #(or (:k %) %) (keys PropertiesSchema)))

(def coerce-properties
  "Fn used to coerce property values using PropertiesSchema"
  (c/coercer! PropertiesSchema
              c/string-coercion-matcher))

(defn- read-property-file []
  (->> property-files
       (keep (fn [file]
               (when-let [rdr (some-> file io/resource io/reader)]
                 (with-open [rdr rdr]
                   (doto (Properties.)
                     (.load rdr))))))
       first
       (into {})))

(defn- transform [properties]
  (reduce (fn [accum [k v]]
            (let [parts (->> (str/split k #"\.")
                             (map keyword))]
              parts
              (cond
                (empty? parts) accum
                (= 1 (count parts)) (assoc accum (first parts) v)
                :else (map/rmerge accum
                                  (assoc-in {} parts v)))))
          {}
          properties))

(defn init!
  "Read a properties file, merge it with system properties, coerce and
   validate it, transform it into a nested map with keyword keys, and
   then store it in memory."
  []
  (->> (let [properties-from-file (read-property-file)]
         (merge properties-from-file
                (select-keys (System/getProperties)
                             configurable-properties)))
       coerce-properties
       transform
       (reset! properties)))
