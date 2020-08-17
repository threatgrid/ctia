(ns ctia.test-helpers.core
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib.net :as net]
            [clj-momo.properties :refer [coerce-properties read-property-files]]
            [clj-momo.test-helpers
             [core :as mth]
             [http :as mthh]]
            [clojure
             [string :as str]
             [walk :refer [prewalk]]]
            [clojure.spec.alpha :as cs]
            [clojure.test :as test]
            [clojure.test.check.generators :as gen]
            [clojure.tools.logging :as log]
            [clojure.tools.logging.test :as tlog]
            [ctia
             [auth :as auth]
             [init :as init]
             [properties :as p :refer [PropertiesSchema]]
             [store :as store]
             [store-service :as store-svc]]
            [ctia.auth.allow-all :as aa]
            [ctia.flows.crud :as crud]
            [ctim.domain.id :as id]
            [ctim.generators.common :as cgc]
            [flanders
             [spec :as fs]
             [utils :as fu]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.config :as tk-config]))

(def ^:dynamic ^:private *current-app*)
(def ^:dynamic ^:private *props-transformers* [])
(def ^:dynamic ^:private *config-transformers* [])

(defn ^:private with-props-transformer*
  "For use in a test fixture to dynamically transform an (uncoerced!) value of
  PropertiesSchema before being converted into a Trapperkeeper config.

  Used via `with-properties`."
  [tf body-fn]
  (assert (not (thread-bound? #'*current-app*))
          "Cannot transform props after TK app has started!")
  (binding [*props-transformers* (conj *props-transformers* tf)]
    (body-fn)))

(defn with-config-transformer*
  "For use in a test fixture to dynamically transform a Trapperkeeper
  config before creating an app."
  [tf body-fn]
  (assert (not (thread-bound? #'*current-app*))
          "Cannot transform config after TK app has started!")
  (binding [*config-transformers* (conj *config-transformers* tf)]
    (body-fn)))

(defn with-properties-vec [properties-vec body-fn]
  (assert (even? (count properties-vec)))
  (with-props-transformer*
    ;; fuses extra traversal of properties-vec and avoids extra map creation
    #(into % (map vec) (partition 2 properties-vec))
    body-fn))

;; Note: named for historical reasons, does NOT set System properties.
;;       instead uses `with-props-transformer*` to build an immutable map of properties
;;
;; same remark for all fixture-properties:* functions below
(defmacro with-properties [properties-vec & sexprs]
  `(with-properties-vec ~properties-vec
     (fn [] (do ~@sexprs))))

(defn fixture-properties:clean [f]
  ;; Override any properties that are in the default properties file
  ;; yet are unsafe/undesirable for tests
  (with-properties ["ctia.auth.type"                            "allow-all"
                    "ctia.access-control.default-tlp"           "green"
                    "ctia.access-control.min-tlp"               "white"
                    "ctia.access-control.max-record-visibility" "everyone"
                    "ctia.encryption.key.filepath"              "resources/cert/ctia-encryption.key"
                    "ctia.events.enabled"                        true
                    "ctia.events.log"                            false
                    "ctia.http.dev-reload"                       false
                    "ctia.http.min-threads"                      9
                    "ctia.http.max-threads"                      10
                    "ctia.http.show.protocol"                    "http"
                    "ctia.http.show.hostname"                    "localhost"
                    "ctia.http.show.port"                        "57254"
                    "ctia.http.show.path-prefix"                 ""
                    "ctia.http.jwt.enabled"                      true
                    "ctia.http.jwt.public-key-path"              "resources/cert/ctia-jwt.pub"
                    "ctia.http.bulk.max-size"                    30000
                    "ctia.hook.redis.enabled"                    false
                    "ctia.hook.redis.channel-name"               "events-test"
                    "ctia.metrics.riemann.enabled"               false
                    "ctia.metrics.console.enabled"               false
                    "ctia.metrics.jmx.enabled"                   false
                    "ctia.versions.config"                       "test"]
    ;; run tests
    (f)))

(defn fixture-properties:cors [f]
  (with-properties
    ["ctia.http.access-control-allow-origin" ".*cisco.com.*"
     "ctia.http.access-control-allow-methods" "get,post,put,patch,delete"]
    (f)))

(defn fixture-properties:events-logging [f]
  ;; Set properties to enable events file logging
  (with-properties ["ctia.events.log" "true"]
    (f)))

(defn fixture-properties:redis-hook [f]
  (with-properties ["ctia.hook.redis.enabled" true]
    (f)))

(defn fixture-properties:kafka-hook [f]
  (with-properties ["ctia.hook.kafka.enabled" true
                    "ctia.hook.kafka.compression.type" "gzip"
                    "ctia.hook.kafka.ssl.enabled" true
                    "ctia.hook.kafka.ssl.truststore.location" "containers/dev/truststore/kafka.truststore.jks"
                    "ctia.hook.kafka.ssl.truststore.password" "Cisco42"
                    "ctia.hook.kafka.ssl.keystore.location" "containers/dev/keystore/kafka.keystore.jks"
                    "ctia.hook.kafka.ssl.keystore.password" "Cisco42"
                    "ctia.hook.kafka.ssl.key.password" "Cisco42"
                    "ctia.hook.kafka.request-size" 307200
                    "ctia.hook.kafka.zk.address" "localhost:2181"
                    "ctia.hook.kafka.topic.name" "ctia-events"
                    "ctia.hook.kafka.topic.num-partitions" 1
                    "ctia.hook.kafka.topic.replication-factor" 1]
    (f)))

(defn fixture-properties:events-enabled [f]
  (with-properties ["ctia.events.enabled" true]
    (f)))

(defn fixture-properties:events-aliased-index [f]
  (with-properties ["ctia.events.enabled" true
                    "ctia.store.es.event.slicing.strategy" :aliased-index]
    (f)))

(defn atomic-log
  "Returns a StatefulLog, appending to an atom the result of invoking
  log-entry-fn with the same args as append!
  But also print the log.
  "
  [log-entry-fn]
  (let [log (atom [])]
    (reify
      tlog/StatefulLog
      (entries [_]
        (deref log))
      (append! [this logger-ns level throwable message]
        #_(do ;; Uncomment to see logs while also capturing them for debug purpose
            (println "!DO NOT FORGET TO COMMENT ctia.test-helpers.core/atomic-log println BEFORE PUSHING YOUR PR")
            (when (log/enabled? level logger-ns)
              (println (format "CAPTURED LOG: [%s] [%s] %s" logger-ns level message))
              (println throwable)))
        (swap! log (fnil conj []) (log-entry-fn logger-ns level throwable message))
        this))))

(defn fixture-log
  [f]
  (comment
    ;; The first version was this one.
    ;; There is bug that make it not work accross process/thread
    ;; I'm not sure where the problem occurs.
    ;; I might take more time to inspect the issue later.
    ;; I think that binding might have issue accross process while with-redefs not.
    ;; It could be any other problem as well.
    (tlog/with-log (f)))
  (log/warn "Logs are hidden by the fixture-log fixture. To see them check ctia.test-helpers.core/atomic-log function")
  (let [sl (atomic-log tlog/->LogEntry)
        lf (tlog/logger-factory sl (constantly true))]
    (with-redefs [tlog/*stateful-log* sl
                  log/*logger-factory* lf]
      (f))))

(defn- split-property-to-keywords [prop]
  {:pre [(string? prop)]
   :post [(seq %)]}
  (map keyword (str/split prop #"\.")))

(defn build-transformed-init-config
  "Builds a `config` map using just p/files, *props-transformers*,
  and *config-transformers*. Ignores System properties and env vars.
  
  Prefer over p/build-init-config during tests."
  []
  (let [init-props (read-property-files p/files)
        transformed+coerced-props (->> (reduce #(%2 %1)
                                               init-props
                                               *props-transformers*)
                                       (coerce-properties PropertiesSchema))
        init-config (reduce (fn [config [prop v]]
                              (assoc-in config (split-property-to-keywords prop) v))
                            {}
                            transformed+coerced-props)
        transformed-config (reduce #(%2 %1)
                                   init-config
                                   *config-transformers*)]
    transformed-config))

(defn build-get-in-config-fn []
  (let [config (build-transformed-init-config)
        get-in-config #(apply get-in config %&)]
    get-in-config))

(defn bind-current-app* [app f]
  (let [_ (assert (not (thread-bound? #'*current-app*)) "Rebound app!")
        _ (assert app)]
    (binding [*current-app* app]
      (f))))

(defn get-current-app []
  {:post [%]}
  (assert (thread-bound? #'*current-app*) "App not bound!")
  (let [app *current-app*]
    app))

(defn current-get-in-config-fn []
  (let [app (get-current-app)
        ConfigService (app/get-service app :ConfigService)
        get-in-config #(apply tk-config/get-in-config ConfigService %&)]
    get-in-config))

(defn fixture-ctia
  ([t] (fixture-ctia t true))
  ([t enable-http?]
   ;; Start CTIA
   ;; This starts the server on an available port (if enabled)
   (let [http-port
         (if enable-http?
           (net/available-port)
           3000)]
     (with-properties ["ctia.http.enabled" enable-http?
                       "ctia.http.port" http-port
                       "ctia.http.show.port" http-port]
       (let [app (let [config (build-transformed-init-config)]
                   (init/start-ctia!*
                     {:services (init/default-services config)
                      :config config}))]
         (try
           (bind-current-app* app t)
           (finally
             (app/stop app))))))))

(defn fixture-ctia-fast [t]
  (fixture-ctia t false))

(defn fixture-allow-all-auth [f]
  (with-properties ["ctia.auth.type" "allow-all"]
    (f)))

(defn fixture-properties:static-auth [name secret]
  (fn [f]
    (with-properties ["ctia.auth.type" "static"
                      "ctia.auth.static.secret" secret
                      "ctia.auth.static.name" name
                      "ctia.auth.static.group" name]
      (f))))

(defn fixture-with-fixed-time [time f]
  (with-redefs [clj-momo.lib.clj-time.core/now
                (fn [] time)
                clj-momo.lib.time/now
                (fn [] time)
                clj-momo.lib.clj-time.core/internal-now
                (fn [] (clj-momo.lib.clj-time.coerce/to-date time))]
    (f)))

(defn set-capabilities!
  [login groups role caps]
  (let [app (get-current-app)
        store-svc (app/get-service app :StoreService)
        write-store (-> #(store-svc/write-store store-svc %1 %2)
                        store-svc/store-service-fn->varargs)]
    (write-store :identity store/create-identity {:login login
                                                      :groups groups
                                                      :role role
                                                      :capabilities caps})))

(defmacro deftest-for-each-fixture [test-name fixture-map & body]
  `(do
     ~@(for [[name-key fixture-fn] fixture-map]
         `(test/deftest ~(with-meta (symbol (str test-name "-" (name name-key)))
                                    {(keyword name-key) true})
            (~fixture-fn (fn [] (do ~@body)))))))

(defn get-http-port []
  (p/get-in-global-properties [:ctia :http :port]))

(def get
  (mthh/with-port-fn get-http-port mthh/get))

(def post
  (mthh/with-port-fn get-http-port mthh/post))

(defn post-bulk [examples]
  (let [{bulk-res :parsed-body}
        (post "ctia/bulk"
              :body examples
              :socket-timeout (* 5 60000)
              :headers {"Authorization" "45c1f5e3f05d0"})]
    bulk-res))

(defn post-entity-bulk [example plural x headers]
  (let [new-records
        (for [y (range 0 x)]
          (-> example
              (dissoc :id)
              (assoc :revision y)))]
    (-> (post "ctia/bulk"
              :body {plural new-records}
              :headers headers
              :socket-timeout (* 5 60000))
        :parsed-body
        plural)))

(def delete
  (mthh/with-port-fn get-http-port mthh/delete))

(def put
  (mthh/with-port-fn get-http-port mthh/put))

(def patch
  (mthh/with-port-fn get-http-port mthh/patch))

(defn fixture-spec-validation [t]
  (with-redefs [cs/registry-ref (atom (cs/registry))]
    (cs/check-asserts true)
    (t)
    (cs/check-asserts false)))

(defn fixture-spec [node-to-spec ns]
  (fn [t]
    (fs/->spec node-to-spec ns)
    (t)))

(defn fixture-max-spec [node-to-spec ns]
  (fixture-spec (fu/require-all node-to-spec) ns))

(defn fixture-fast-gen [t]
  (with-redefs [gen/vector cgc/vector]
    (t)))

(defn make-id
  "Make a long style ID using CTIA code (eg with a random UUID).
  Returns an ID object."
  [type-kw]
  (id/->id type-kw
           (crud/make-id (name type-kw))
           (p/get-in-global-properties [:ctia :http :show])))

(defn entity->short-id
  [entity]
  (-> (:id entity)
      id/long-id->id
      :short-id))

(defn url-id
  ([type-kw]
   (url-id (crud/make-id (name type-kw)) type-kw))
  ([short-id type-kw]
   (id/long-id
    (id/short-id->id (name type-kw)
                     short-id
                     (p/get-in-global-properties [:ctia :http :show])))))

(def zero-uuid "00000000-0000-0000-0000-000000000000")

(defn fake-short-id
  "Make a fake short style ID with a deterministic UUID.  Returns a
  string."
  [entity-name id]
  (let [id-str (str id)
        id-cnt (count id-str)]
    (assert (<= id-cnt 8)
            "ID must be 8 chars or less")
    (str entity-name "-" id-str (subs zero-uuid id-cnt))))

(defn fake-long-id
  "Make a fake long style ID with a deterministic UUID.  Returns a
  string."
  [entity-name id]
  (id/long-id
   (id/->id (keyword entity-name)
            (fake-short-id entity-name id)
            (p/get-in-global-properties [:ctia :http :show]))))

(defmacro with-atom-logger
  [atom-logger & body]
  `(let [patched-log#
         (fn [logger#
              level#
              throwable#
              message#]
           (swap! ~atom-logger conj message#))]
     (with-redefs [clojure.tools.logging/log* patched-log#]
       ~@body)))

(defn deep-dissoc-entity-ids
  "Dissoc all entity ID in the given map recursively"
  [m]
  (prewalk #(if (and (map? %)
                     ;; Do not remove the id of a nested openc2 coa
                     (not= (:type %) "structured_coa"))
              (dissoc % :id)
              %)
           m))

(defn with-sequential-uuid [f]
  (let [uuid-counter-start 111111111111
        uuid-counter (atom uuid-counter-start)]
    (with-redefs [crud/gen-random-uuid
                  (fn []
                    (swap! uuid-counter inc)
                    (str "00000000-0000-0000-0000-" @uuid-counter))]
      (f)
      (reset! uuid-counter
              uuid-counter-start))))
