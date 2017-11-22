(ns ^{:doc "Properties (aka configuration) of the application.\n
            Properties are stored in property files.  There is a\n
            default file which can be overridden by placing an\n
            alternative properties file on the classpath or by\n
            setting system properties."}
    ctia.properties
  (:require [clj-momo.lib.schema :as mls]
            [clj-momo.properties :as mp]
            [ctia.store :as store]
            [schema-tools.core :as st]
            [schema.core :as s]
            [ctia.schemas.core
             :refer [TLP]]))

(def files
  "Property file names, they will be merged, with last one winning"
  ["ctia-default.properties"
   "ctia.properties"])

(defonce properties
  (atom {}))

(defn default-store-properties [store]
  {(str "ctia.store." store) s/Str})

(defn es-store-impl-properties [store]
  {(str "ctia.store.es." store ".host") s/Str
   (str "ctia.store.es." store ".port") s/Int
   (str "ctia.store.es." store ".clustername") s/Str
   (str "ctia.store.es." store ".indexname") s/Str
   (str "ctia.store.es." store ".refresh") s/Bool
   (str "ctia.store.es." store ".replicas") s/Num
   (str "ctia.store.es." store ".shards") s/Num})

(s/defschema StorePropertiesSchema
  "All entity store properties for every implementation"
  (let [configurable-stores (map name (keys @store/stores))
        store-names (conj configurable-stores "default")]
    (st/optional-keys
     (reduce merge {}
             (map (fn [s] (merge (default-store-properties s)
                                 (es-store-impl-properties s)))
                  store-names)))))

(s/defschema PropertiesSchema
  "This is the schema used for value type coercion.
  It is also used for validating the properties that are read in so that required
   properties must be present.  Only the following properties may be
   set.  This is also used for selecting system properties to merge
   with the properties file."
  (st/merge
   StorePropertiesSchema
   (st/required-keys {"ctia.auth.type" s/Keyword})
   (st/optional-keys {"ctia.auth.threatgrid.cache" s/Bool
                      "ctia.auth.threatgrid.whoami-url" s/Str
                      "ctia.auth.static.secret" s/Str
                      "ctia.auth.static.name" s/Str
                      "ctia.auth.static.group" s/Str})

   (st/required-keys {"ctia.http.enabled" s/Bool
                      "ctia.http.port" s/Int
                      "ctia.http.access-control-allow-origin" s/Str
                      "ctia.http.access-control-allow-methods" s/Str
                      "ctia.http.min-threads" s/Int
                      "ctia.http.max-threads" s/Int})

   (st/optional-keys {"ctia.http.jwt.enabled" s/Bool
                      "ctia.http.jwt.public-key-path" s/Str
                      "ctia.http.jwt.local-storage-key" s/Str
                      "ctia.http.jwt.lifetime-in-sec" s/Num})

   (st/optional-keys {"ctia.http.dev-reload" s/Bool
                      "ctia.http.show.protocol" s/Str
                      "ctia.http.show.hostname" s/Str
                      "ctia.http.show.path-prefix" s/Str
                      "ctia.http.show.port" s/Int
                      "ctia.http.bulk.max-size" s/Int})

   (st/required-keys {"ctia.events.enabled" s/Bool
                      "ctia.nrepl.enabled" s/Bool
                      "ctia.hook.redis.enabled" s/Bool
                      "ctia.hook.redismq.enabled" s/Bool})

   (st/required-keys {"ctia.access-control.min-tlp" TLP
                      "ctia.access-control.default-tlp" TLP})

   (st/optional-keys {"ctia.events.log" s/Bool
                      "ctia.nrepl.port" s/Int
                      "ctia.hook.redis.host" s/Str
                      "ctia.hook.redis.port" s/Int
                      "ctia.hook.redis.channel-name" s/Str
                      "ctia.hook.redis.timeout-ms" s/Int

                      "ctia.hook.redismq.queue-name" s/Str
                      "ctia.hook.redismq.host" s/Str
                      "ctia.hook.redismq.port" s/Int
                      "ctia.hook.redismq.timeout-ms" s/Int
                      "ctia.hook.redismq.max-depth" s/Int

                      "ctia.hooks.before-create" s/Str
                      "ctia.hooks.after-create" s/Str
                      "ctia.hooks.before-update" s/Str
                      "ctia.hooks.after-update" s/Str
                      "ctia.hooks.before-delete" s/Str
                      "ctia.hooks.after-delete" s/Str

                      "ctia.metrics.console.enabled" s/Bool
                      "ctia.metrics.console.interval" s/Int
                      "ctia.metrics.jmx.enabled" s/Bool
                      "ctia.metrics.riemann.enabled" s/Bool
                      "ctia.metrics.riemann.host" s/Str
                      "ctia.metrics.riemann.port" s/Int
                      "ctia.metrics.riemann.interval" s/Int

                      "ctia.store.es.event.slicing.strategy"
                      (s/maybe (s/enum :aliased-index))
                      "ctia.store.es.event.slicing.granularity"
                      (s/enum :minute :hour :day :week :month :year)})))

(def configurable-properties
  (mls/keys PropertiesSchema))

(def init! (mp/build-init-fn files
                             PropertiesSchema
                             properties))

(defn get-http-show []
  (get-in @properties [:ctia :http :show]))

(defn get-access-control []
  (get-in @properties [:ctia :access-control]))
