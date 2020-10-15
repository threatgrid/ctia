(ns ctia.test-helpers.core
  (:require [clj-momo.lib.net :as net]
            [clj-momo.properties :refer [coerce-properties read-property-files]]
            [clj-momo.test-helpers.http :as mthh]
            [clojure
             [walk :refer [prewalk]]]
            [clojure.spec.alpha :as cs]
            [clojure.string :as str]
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
            [ctia.encryption :as encryption]
            [ctia.flows.crud :as crud]
            [ctim.domain.id :as id]
            [ctim.generators.common :as cgc]
            [flanders
             [spec :as fs]
             [utils :as fu]]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s]))

(def ^:dynamic ^:private *current-app*)

(def
  ^:dynamic ^:private
  *properties-overrides*
  "An even-sized vector of property key-val flattened pairs (like the
  first argument to #'with-properties) that will be
  used to override the default properties."
  ;; Default overrides for any properties that are in the default properties file
  ;; yet are unsafe/undesirable for tests
  ["ctia.auth.type"                            "allow-all"
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
   "ctia.versions.config"                       "test"])
(assert (even? (count *properties-overrides*)))

(def ^:dynamic ^:private *config-transformers* [])

(defn get-service-map [app svc-kw]
  {:pre [(keyword? svc-kw)]}
  (let [graph (app/service-graph app)
        m (svc-kw graph)]
    (assert (map? m) (str "No service " svc-kw ", found " (keys graph)))
    m))

(defn with-config-transformer*
  "For use in a test fixture to dynamically transform a Trapperkeeper
  config before creating an app."
  [tf body-fn]
  (assert (not (thread-bound? #'*current-app*))
          "Cannot transform config after TK app has started!")
  (binding [*config-transformers* (conj *config-transformers* tf)]
    (body-fn)))

(s/defn with-properties*
  [properties-vec :- (s/pred vector?)
   f :- (s/=> s/Any)]
  (assert (not (thread-bound? #'*current-app*))
          "Cannot override properties after TK app has started!")
  (assert (even? (count properties-vec))
          (str "Even count required for properties-vec, actual " (count properties-vec)))
  (binding [*properties-overrides* (into *properties-overrides* properties-vec)]
    (f)))

(defmacro with-properties
  "Simulates setting the specified System properties to configure
  the creation of a TK app, except thread-locally.
  
  Note: Does not actually set System properties!"
  [properties-vec & sexprs]
  `(with-properties* ~properties-vec
     (fn [] (do ~@sexprs))))

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

(defn split-property-to-keywords [prop]
  (map keyword (str/split prop #"\.")))

(defn build-transformed-init-config
  "Builds a `config` map using just p/files, *properties-overrides*,
  and *config-transformers*.
  
  Note that p/build-init-config uses p/files, System properties, and env vars.
  This function emulates p/build-init-config in a thread-local fashion.
  
  Prefer over p/build-init-config during tests."
  []
  (assert (not (thread-bound? #'*current-app*))
          "Building custom config while app bound!")
  (let [init-props (read-property-files p/files)
        properties-overrides *properties-overrides*
        _ (assert (even? (count properties-overrides)))
        transformed+coerced-props (->> (reduce (fn [m [k v]]
                                                 (assoc m k v))
                                               init-props
                                               (partition 2 *properties-overrides*))
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
  (assert (not (thread-bound? #'*current-app*)) "Building custom config while app bound!")
  (partial get-in (build-transformed-init-config)))

(defn bind-current-app* [app f]
  (assert (not (thread-bound? #'*current-app*)) "Rebound app!")
  (assert app)
  (binding [*current-app* app]
    (f)))

(defn get-current-app []
  {:post [%]}
  (assert (thread-bound? #'*current-app*) "App not bound!")
  *current-app*)

(defn current-get-in-config-fn
  ([] (current-get-in-config-fn (get-current-app)))
  ([app]
   {:post [%]}
   (-> app
       (get-service-map :ConfigService)
       :get-in-config)))

(s/defn fixture-ctia-with-app
  ([t-with-app :- (s/=> s/Any
                        (s/=> s/Any
                              (s/named s/Any 'app)))]
   (fixture-ctia-with-app t-with-app true))
  ([t-with-app :- (s/=> s/Any
                        (s/=> s/Any
                              (s/named s/Any 'app)))
    enable-http?]
   ;; Start CTIA
   ;; This starts the server on an available port (if enabled)
   (let [http-port
         (if enable-http?
           (net/available-port)
           3000)]
     (with-properties ["ctia.http.enabled" enable-http?
                       "ctia.http.port" http-port
                       "ctia.http.show.port" http-port]
       (let [config (build-transformed-init-config)
             services-map (cond-> (init/default-services-map config)
                            (#{:threatgrid} (get-in config [:ctia :auth :type]))
                            ;; dynamic requires can be removed when #'with-properties is phased out or moved
                            (assoc
                              :ThreatgridAuthWhoAmIURLService
                              @(requiring-resolve
                                 'ctia.test-helpers.fake-whoami-service/fake-threatgrid-auth-whoami-url-service)
                              :IFakeWhoAmIServer
                              @(requiring-resolve
                                 'ctia.test-helpers.fake-whoami-service/fake-whoami-service)))
             app (init/start-ctia!*
                   {:services (vals services-map)
                    :config config})]
         (try
           ;; both bind app thread-locally and pass as argument.
           ;; in the future, we should move to just an argument.
           (bind-current-app*
             app
             #(t-with-app app))
           (finally
             (app/stop app))))))))

(defn fixture-ctia
  ([t] (fixture-ctia t true))
  ([t enable-http?]
   (fixture-ctia-with-app (fn [_app_]
                            ;; app bound thread-locally
                            (t))
                          enable-http?)))

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
  [app login groups role caps]
  (let [{:keys [write-store]} (-> (get-service-map app :StoreService)
                                  store-svc/lift-store-service-fns)]
    (write-store :identity store/create-identity {:login login
                                                  :groups groups
                                                  :role role
                                                  :capabilities caps})))

(defmacro deftest-for-each-fixture-with-app [test-name fixture-map app & body]
  (assert (simple-symbol? app) (pr-str app))
  `(do
     ~@(for [[name-key fixture-fn] fixture-map]
         `(test/deftest ~(with-meta (symbol (str test-name "-" (name name-key)))
                                    {(keyword name-key) true})
            (~fixture-fn
              (fn []
                (let [~app (get-current-app)]
                  ~@body)))))))

(s/defn get-http-port :- s/Int
  [app]
  (let [{{:keys [get-in-config]} :ConfigService} (app/service-graph app)]
    (get-in-config [:ctia :http :port])))

(s/defn GET [app path :- s/Str & kw-options]
  (apply (mthh/with-port-fn (partial get-http-port app) mthh/get)
         path
         kw-options))

(s/defn POST [app path :- s/Str & kw-options]
  (apply (mthh/with-port-fn (partial get-http-port app) mthh/post)
         path
         kw-options))

(defn POST-bulk
  ([app examples] (POST-bulk app examples true))
  ([app examples check?]
   (let [{{:keys [error message] :as bulk-res} :parsed-body}
         (POST app
               "ctia/bulk"
               :body examples
               :socket-timeout (* 5 60000)
               :headers {"Authorization" "45c1f5e3f05d0"})]
     (when check?
       (assert (nil? error)
               (format "POST-bulk error: %s, message: \"%s\"" error message)))
     bulk-res)))

(defn POST-entity-bulk [app example plural x headers]
  (let [new-records
        (for [y (range 0 x)]
          (-> example
              (dissoc :id)
              (assoc :revision y)))]
    (-> (POST app
              "ctia/bulk"
              :body {plural new-records}
              :headers headers
              :socket-timeout (* 5 60000))
        :parsed-body
        plural)))

(s/defn DELETE [app path :- s/Str & kw-options]
  (apply (mthh/with-port-fn (partial get-http-port app) mthh/delete)
         path
         kw-options))

(s/defn PUT [app path :- s/Str & kw-options]
  (apply (mthh/with-port-fn (partial get-http-port app) mthh/put)
         path
         kw-options))

(s/defn PATCH [app path :- s/Str & kw-options]
  (apply (mthh/with-port-fn (partial get-http-port app) mthh/patch)
         path
         kw-options))

(defn fixture-spec-validation [t]
  (with-redefs [cs/registry-ref (atom (cs/registry))]
    (let [old (cs/check-asserts?)]
      (try
        (cs/check-asserts true)
        (t)
        (finally
          (cs/check-asserts old))))))

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
  [type-kw get-in-config]
  (id/->id type-kw
           (crud/make-id (name type-kw))
           (get-in-config [:ctia :http :show])))

(defn entity->short-id
  [entity]
  (-> (:id entity)
      id/long-id->id
      :short-id))

(defn url-id
  ([type-kw get-in-config]
   (url-id (crud/make-id (name type-kw)) type-kw get-in-config))
  ([short-id type-kw get-in-config]
   (id/long-id
    (id/short-id->id (name type-kw)
                     short-id
                     (get-in-config [:ctia :http :show])))))

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
