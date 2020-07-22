(ns ^{:doc "Properties (aka configuration) of the application.\n
            Properties are stored in property files.  There is a\n
            default file which can be overridden by placing an\n
            alternative properties file on the classpath or by\n
            setting system properties."}
    ctia.properties
  (:require [clj-momo.lib.es.schemas :refer [Refresh]]
            [clj-momo.lib.schema :as mls]
            [clj-momo.properties :as mp]
            [ctia.store :as store]
            [ctia.store-service-core :as store-svc-core]
            [schema-tools.core :as st]
            [schema.core :as s]
            [ctia.schemas.core
             :refer [TLP]]))

(def files
  "Property file names, they will be merged, with last one winning"
  ["ctia-default.properties"
   "ctia.properties"])

(defonce ^:private properties
  (atom {}))

;; Note: this var is redefined in these places:
;; - ctia.bundle.routes-test/with-tlp-property-setting
;; - ctia.task.migration.migrate-es-stores-test
(defn get-global-properties []
  properties)

(defn default-store-properties [store]
  {(str "ctia.store." store) s/Str})

(defn es-store-impl-properties [store]
  {(str "ctia.store.es." store ".host") s/Str
   (str "ctia.store.es." store ".port") s/Int
   (str "ctia.store.es." store ".transport") s/Keyword
   (str "ctia.store.es." store ".clustername") s/Str
   (str "ctia.store.es." store ".indexname") s/Str
   (str "ctia.store.es." store ".refresh") Refresh
   (str "ctia.store.es." store ".refresh_interval")  s/Str
   (str "ctia.store.es." store ".replicas") s/Num
   (str "ctia.store.es." store ".shards") s/Num
   (str "ctia.store.es." store ".rollover.max_docs") s/Num
   (str "ctia.store.es." store ".rollover.max_age") s/Str
   (str "ctia.store.es." store ".aliased")  s/Bool
   (str "ctia.store.es." store ".default_operator") (s/enum "OR" "AND")
   (str "ctia.store.es." store ".timeout") s/Num})

(s/defschema StorePropertiesSchema
  "All entity store properties for every implementation"
  (let [configurable-stores (map name (keys store-svc-core/empty-stores))
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
                      "ctia.auth.entities.scope" s/Str
                      "ctia.auth.casebook.scope" s/Str
                      "ctia.auth.threatgrid.whoami-url" s/Str
                      "ctia.auth.static.secret" s/Str
                      "ctia.auth.static.name" s/Str
                      "ctia.auth.static.group" s/Str
                      "ctia.auth.static.readonly-for-anonymous" s/Bool})

   (st/optional-keys {"ctia.encryption.type" (s/enum :default)
                      "ctia.encryption.secret" s/Str
                      "ctia.encryption.key.filepath" s/Str})

   (st/required-keys {"ctia.http.enabled" s/Bool
                      "ctia.http.port" s/Int
                      "ctia.http.access-control-allow-origin" s/Str
                      "ctia.http.access-control-allow-methods" s/Str
                      "ctia.http.min-threads" s/Int
                      "ctia.http.max-threads" s/Int})

   (st/optional-keys {"ctia.http.rate-limit.enabled" s/Bool
                      "ctia.http.rate-limit.key-prefix" s/Str
                      "ctia.http.rate-limit.unlimited.client-ids" s/Str
                      "ctia.http.rate-limit.limits.group.default" s/Int
                      "ctia.http.rate-limit.limits.group.customs" s/Str
                      "ctia.http.rate-limit.redis.host" s/Str
                      "ctia.http.rate-limit.redis.port" s/Int
                      "ctia.http.rate-limit.redis.ssl" s/Bool
                      "ctia.http.rate-limit.redis.password" s/Str
                      "ctia.http.rate-limit.redis.db" s/Int
                      "ctia.http.rate-limit.redis.timeout-ms" s/Int})

   (st/optional-keys {"ctia.http.send-server-version" s/Bool})

   (st/optional-keys {"ctia.http.swagger.oauth2.enabled" s/Bool
                      "ctia.http.swagger.oauth2.entry-key" s/Str
                      "ctia.http.swagger.oauth2.scopes" s/Str
                      "ctia.http.swagger.oauth2.authorization-url" s/Str
                      "ctia.http.swagger.oauth2.token-url" s/Str
                      "ctia.http.swagger.oauth2.refresh-url" s/Str
                      "ctia.http.swagger.oauth2.flow" s/Str
                      "ctia.http.swagger.oauth2.client-id" s/Str
                      "ctia.http.swagger.oauth2.app-name" s/Str
                      "ctia.http.swagger.oauth2.realm" s/Str})

   (st/optional-keys {"ctia.http.jwt.enabled" s/Bool
                      "ctia.http.jwt.public-key-path" s/Str
                      "ctia.http.jwt.public-key-map" s/Str
                      "ctia.http.jwt.local-storage-key" s/Str
                      "ctia.http.jwt.lifetime-in-sec" s/Num
                      "ctia.http.jwt.claim-prefix" s/Str
                      "ctia.http.jwt.http-check.endpoints" s/Str
                      "ctia.http.jwt.http-check.timeout" s/Num
                      "ctia.http.jwt.http-check.cache-ttl" s/Num})

   (st/optional-keys {"ctia.http.dev-reload" s/Bool
                      "ctia.http.show.protocol" s/Str
                      "ctia.http.show.hostname" s/Str
                      "ctia.http.show.path-prefix" s/Str
                      "ctia.http.show.port" s/Int
                      "ctia.http.bulk.max-size" s/Int
                      "ctia.http.bundle.export.max-relationships" s/Int})

   (st/required-keys {"ctia.events.enabled" s/Bool
                      "ctia.hook.redis.enabled" s/Bool
                      "ctia.hook.redismq.enabled" s/Bool})

   (st/required-keys {"ctia.access-control.min-tlp" TLP
                      "ctia.access-control.default-tlp" TLP})

   (st/optional-keys {"ctia.access-control.max-record-visibility" (s/enum "group" "everyone")})

   (st/optional-keys {"ctia.hook.kafka.enabled" s/Bool
                      "ctia.hook.kafka.compression.type" (s/enum "none" "gzip" "snappy" "lz4" "zstd")
                      "ctia.hook.kafka.ssl.enabled" s/Bool
                      "ctia.hook.kafka.ssl.truststore.location" s/Str
                      "ctia.hook.kafka.ssl.truststore.password" s/Str
                      "ctia.hook.kafka.ssl.keystore.location" s/Str
                      "ctia.hook.kafka.ssl.keystore.password" s/Str
                      "ctia.hook.kafka.ssl.key.password" s/Str

                      "ctia.hook.kafka.request-size" s/Num
                      "ctia.hook.kafka.zk.address" s/Str
                      "ctia.hook.kafka.topic.name" s/Str
                      "ctia.hook.kafka.topic.num-partitions" s/Int
                      "ctia.hook.kafka.topic.replication-factor" s/Int})

   (st/optional-keys {"ctia.events.log" s/Bool
                      "ctia.http.events.timeline.max-seconds" s/Int
                      "ctia.hook.redis.host" s/Str
                      "ctia.hook.redis.port" s/Int
                      "ctia.hook.redis.ssl" s/Bool
                      "ctia.hook.redis.password" s/Str
                      "ctia.hook.redis.channel-name" s/Str
                      "ctia.hook.redis.timeout-ms" s/Int

                      "ctia.hook.redismq.queue-name" s/Str
                      "ctia.hook.redismq.host" s/Str
                      "ctia.hook.redismq.port" s/Int
                      "ctia.hook.redismq.ssl" s/Bool
                      "ctia.hook.redismq.password" s/Str
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
                      "ctia.metrics.riemann.interval-in-ms" s/Int

                      "ctia.log.riemann.enabled" s/Bool
                      "ctia.log.riemann.host" s/Str
                      "ctia.log.riemann.port" s/Int
                      "ctia.log.riemann.interval-in-ms" s/Int
                      "ctia.log.riemann.batch-size" s/Int
                      "ctia.log.riemann.service-prefix" s/Str

                      "ctia.store.external-key-prefixes" s/Str
                      "ctia.store.bulk-refresh" Refresh
                      "ctia.store.bundle-refresh" Refresh

                      "ctia.store.es.migration.indexname" s/Str
                      "ctia.store.es.event.slicing.granularity"
                      (s/enum :minute :hour :day :week :month :year)})
   (st/optional-keys {"ctia.versions.config" s/Str})))

(def configurable-properties
  (mls/keys PropertiesSchema))

(def init! (mp/build-init-fn files
                             PropertiesSchema
                             ;; TOP-LEVEL STATE!!
                             (get-global-properties)))

(defn get-http-show []
  (get-in @(get-global-properties) [:ctia :http :show]))

(defn get-http-swagger []
  (get-in @(get-global-properties) [:ctia :http :swagger]))

(defn get-access-control []
  (get-in @(get-global-properties) [:ctia :access-control]))
