(ns ctia.test-helpers.core
  (:refer-clojure :exclude [get])
  (:require [clj-momo.lib
             [net :as net]
             [time :as time]]
            [clj-momo.test-helpers
             [core :as mth]
             [http :as mthh]]
            [clojure.string :as str]
            [clojure.spec.alpha :as cs]
            [clojure.test.check.generators :as gen]
            [ctia
             [auth :as auth]
             [init :as init]
             [properties :refer [properties PropertiesSchema]]
             [store :as store]]
            [clojure.walk :refer [prewalk]]
            [ctia.auth.allow-all :as aa]
            [ctia.flows.crud :as crud]
            [ctia.flows.hooks :as hooks]
            [ctia.http.server :as http-server]
            [ctia.shutdown :as shutdown]
            [ctim.domain.id :as id]
            [ctim.generators.common :as cgc]
            [flanders
             [spec :as fs]
             [utils :as fu]]
            [schema.core :as schema])
  (:import java.net.ServerSocket))

(def fixture-property
  (mth/build-fixture-property-fn PropertiesSchema))

(def with-properties-vec
  (mth/build-with-properties-vec-fn PropertiesSchema))

(defmacro with-properties [properties-vec & sexprs]
  `(with-properties-vec ~properties-vec
     (fn [] ~@sexprs)))

(defn fixture-properties:clean [f]
  ;; Remove any set system properties, presumably from a previous test
  ;; run
  (mth/clear-properties PropertiesSchema)
  ;; Override any properties that are in the default properties file
  ;; yet are unsafe/undesirable for tests
  (with-properties ["ctia.auth.type"                  "allow-all"
                    "ctia.access-control.default-tlp" "green"
                    "ctia.access-control.min-tlp"     "white"
                    "ctia.events.enabled"             true
                    "ctia.events.log"                 false
                    "ctia.http.dev-reload"            false
                    "ctia.http.min-threads"           9
                    "ctia.http.max-threads"           10
                    "ctia.http.show.protocol"         "http"
                    "ctia.http.show.hostname"         "localhost"
                    "ctia.http.show.port"             "57254"
                    "ctia.http.show.path-prefix"      ""
                    "ctia.http.jwt.enabled"           true
                    "ctia.http.jwt.public-key-path"   "resources/cert/ctia-jwt.pub"
                    "ctia.nrepl.enabled"              false
                    "ctia.hook.redis.enabled"         false
                    "ctia.hook.redis.channel-name"    "events-test"
                    "ctia.metrics.riemann.enabled"    false
                    "ctia.metrics.console.enabled"    false
                    "ctia.metrics.jmx.enabled"        false]
    ;; run tests
    (f)))

(defn fixture-properties:events-logging [f]
  ;; Set properties to enable events file logging
  (with-properties ["ctia.events.log" "true"]
    (f)))

(defn fixture-properties:redis-hook [f]
  (with-properties ["ctia.hook.redis.enabled" true]
    (f)))

(defn fixture-properties:events-enabled [f]
  (with-properties ["ctia.events.enabled" true]
    (f)))

(defn fixture-properties:events-aliased-index [f]
  (with-properties ["ctia.events.enabled" true
                    "ctia.store.es.event.slicing.strategy" :aliased-index]
    (f)))

(defn fixture-properties:hook-classes [f]
  (with-properties ["ctia.hooks.before-create"
                    (str/join "," ["ctia.hook.AutoLoadedJar1"
                                   "hook-example.core/hook-example-1"
                                   "ctia.hook.AutoLoadedJar2"
                                   "hook-example.core/hook-example-2"])]
    (f)))

(defn fixture-ctia
  ([test] (fixture-ctia test true))
  ([test enable-http?]
   ;; Start CTIA
   ;; This starts the server on an available port (if enabled)
   (let [http-port
         (if enable-http?
           (net/available-port)
           3000)]
     (with-properties ["ctia.http.enabled" enable-http?
                       "ctia.http.port" http-port
                       "ctia.http.show.port" http-port]
       (try
         (init/start-ctia! :join? false)
         (test)
         (finally
           (shutdown/shutdown-ctia!)))))))

(defn fixture-ctia-fast [test]
  (fixture-ctia test false))

;; TODO - Convert this to a properties fixture
(defn fixture-allow-all-auth [f]
  (let [orig-auth-srvc @auth/auth-service]
    (reset! auth/auth-service (aa/->AuthService))
    (f)
    (reset! auth/auth-service orig-auth-srvc)))

(defn fixture-properties:static-auth [name secret]
  (fn [f]
    (with-properties ["ctia.auth.type" "static"
                      "ctia.auth.static.secret" secret
                      "ctia.auth.static.name" name
                      "ctia.auth.static.group" name]
      (f))))

(defn set-capabilities!
  [login groups role caps]
  (store/write-store :identity store/create-identity {:login login
                                                      :groups groups
                                                      :role role
                                                      :capabilities caps}))

(defmacro deftest-for-each-fixture [test-name fixture-map & body]
  `(do
     ~@(for [[name-key fixture-fn] fixture-map]
         `(clojure.test/deftest ~(with-meta (symbol (str test-name "-" (name name-key)))
                                   {(keyword name-key) true})
            (~fixture-fn (fn [] ~@body))))))

(defn get-http-port []
  (get-in @properties [:ctia :http :port]))

(def get
  (mthh/with-port-fn get-http-port mthh/get))

(def post
  (mthh/with-port-fn get-http-port mthh/post))

(defn post-entity-bulk [example plural x headers]
  (let [new-records
        (for [x (range 0 x)]
          (-> example
              (assoc :source (str "dotimes " x))
              (dissoc :id)))]
    (-> (post "ctia/bulk"
              :body {plural new-records}
              :headers headers)
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
           (get-in @properties [:ctia :http :show])))

(defn url-id
  ([type-kw]
   (url-id (crud/make-id (name type-kw)) type-kw))
  ([short-id type-kw]
   (id/long-id
    (id/short-id->id (name type-kw)
                     short-id
                     (get-in @properties [:ctia :http :show])))))

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
            (get-in @properties [:ctia :http :show]))))

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

(defn deep-dissoc
  "Dissoc a key in the given map recursively"
  [m k]
  (prewalk #(if (map? %)
              (dissoc % k)
              %)
           m))
