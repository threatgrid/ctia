(ns ^{:doc "Properties (aka configuration) of the application.
            Properties are stored in property files.  There is a
            default file which can be overridden by placing an
            alternative properties file on the classpath, or by
            setting system properties."}
    ctia.properties
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ctia.lib.map :as map]
            [schema.coerce :as c]
            [schema.core :as s]
            [schema-tools.core :as st])
  (:import java.util.Properties))

(def files
  "Property file names, they will be merged, with last one winning"
  ["ctia-default.properties"
   "ctia.properties"])

(defonce properties
  (atom {}))

(s/defschema PropertiesSchema
  "This is the schema used for value type coercion.  It is also used
   for validating the properties that are read in so that required
   properties must be present.  Only the following properties may be
   set.  This is also used for selecting system properties to merge
   with the properties file."
  (st/merge
   (st/required-keys {"ctia.auth.type" s/Keyword})
   (st/optional-keys {"ctia.auth.threatgrid.cache" s/Bool
                      "ctia.auth.threatgrid.whoami-url" s/Str})

   (st/required-keys {"ctia.http.enabled" s/Bool
                      "ctia.http.port" s/Int
                      "ctia.http.min-threads" s/Int
                      "ctia.http.max-threads" s/Int})

   (st/optional-keys {"ctia.http.dev-reload" s/Bool
                      "ctia.http.show.protocol" s/Str
                      "ctia.http.show.hostname" s/Str
                      "ctia.http.show.path-prefix" s/Str
                      "ctia.http.show.port" s/Int})

   (st/required-keys {"ctia.nrepl.enabled" s/Bool

                      "ctia.store.actor" s/Keyword
                      "ctia.store.feedback" s/Keyword
                      "ctia.store.campaign" s/Keyword
                      "ctia.store.coa" s/Keyword
                      "ctia.store.exploit-target" s/Keyword
                      "ctia.store.identity" s/Keyword
                      "ctia.store.incident" s/Keyword
                      "ctia.store.indicator" s/Keyword
                      "ctia.store.judgement" s/Keyword
                      "ctia.store.sighting" s/Keyword
                      "ctia.store.ttp" s/Keyword

                      "ctia.hook.es.enabled" s/Bool
                      "ctia.hook.redis.enabled" s/Bool})

   (st/optional-keys {"ctia.nrepl.port" s/Int

                      "ctia.store.sql.db.classname" s/Str
                      "ctia.store.sql.db.subprotocol" s/Str
                      "ctia.store.sql.db.subname" s/Str
                      "ctia.store.sql.db.delimiters" s/Str

                      "ctia.store.es.uri" s/Str
                      "ctia.store.es.host" s/Str
                      "ctia.store.es.port" s/Int
                      "ctia.store.es.clustername" s/Str
                      "ctia.store.es.indexname" s/Str
                      "ctia.store.es.refresh" s/Bool

                      "ctia.hook.redis.uri" s/Str
                      "ctia.hook.redis.host" s/Str
                      "ctia.hook.redis.port" s/Int
                      "ctia.hook.redis.channel-name" s/Str
                      "ctia.hook.redis.timeout-ms" s/Int

                      "ctia.hook.es.uri" s/Str
                      "ctia.hook.es.host" s/Str
                      "ctia.hook.es.port" s/Int
                      "ctia.hook.es.clustername" s/Str
                      "ctia.hook.es.indexname" s/Str
                      "ctia.hook.es.slicing.strategy" (s/enum :filtered-alias :aliased-index)
                      "ctia.hook.es.slicing.granularity" (s/enum :minute :hour :day :week :month :year)

                      "ctia.hooks.before-create" s/Str
                      "ctia.hooks.after-create" s/Str
                      "ctia.hooks.before-update" s/Str
                      "ctia.hooks.after-update" s/Str
                      "ctia.hooks.before-delete" s/Str
                      "ctia.hooks.after-delete" s/Str
                      "ctia.hooks.event" s/Str})))

(def configurable-properties
  "String keys from PropertiesSchema, used to select system properties."
  (map #(or (:k %) %) (keys PropertiesSchema)))

(def coerce-properties
  "Fn used to coerce property values using PropertiesSchema"
  (c/coercer! PropertiesSchema
              c/string-coercion-matcher))

(defn read-property-files
  "Read all the property files (that exist) and merge the properties
   into a single map"
  []
  (->> files
       (keep (fn [file]
               (when-let [rdr (some-> file io/resource io/reader)]
                 (with-open [rdr rdr]
                   (doto (Properties.)
                     (.load rdr))))))
       concat
       (into {})))

(defn prop->env
  "Convert a property name into an environment variable name"
  [prop]
  (-> prop
      str/upper-case
      (str/replace #"[-.]" "_")))

(def property-env-map
  "Map of property name to environment variable name"
  (into {}
        (map (fn [prop]
               [(prop->env prop) prop])
             configurable-properties)))

(defn read-env-variables
  "Get a map of properties from environment variables"
  []
  (into {}
        (map (fn [[env val]]
               [(get property-env-map env) val])
             (select-keys (System/getenv)
                          (keys property-env-map)))))

(defn transform
  "Convert a flat map of property->value into a nested map with keyword
   keys, splitting on '.'"
  [properties]
  (reduce (fn [accum [k v]]
            (let [parts (->> (str/split k #"\.")
                             (map keyword))]
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
  (->> (merge (read-property-files)
              (select-keys (System/getProperties)
                           configurable-properties)
              (read-env-variables))
       coerce-properties
       transform
       (reset! properties)))
