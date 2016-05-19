(ns ctia.test-helpers.core
  (:refer-clojure :exclude [get])
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure
             [data :as cd]
             [edn :as edn]
             [string :as str]
             [test :as ct]]
            [com.rpl.specter :refer [transform]]
            [ctia
             [auth :as auth]
             [events :as events]
             [init :as init]
             [properties :as props]
             [store :as store]]
            [ctia.auth.allow-all :as aa]
            [ctia.flows.hooks :as hooks]
            [ctia.http.server :as http-server]
            [ctia.lib
             [map :as map]
             [time :as time]
             [url :as url]]
            [ctia.lib.specter.paths :as path]
            [schema.core :as schema])
  (:import java.net.ServerSocket))

(defmethod ct/assert-expr 'deep= [msg form]
  (let [a (second form)
        b (nth form 2)]
    `(let [[only-a# only-b# _] (cd/diff ~a ~b)]
       (if (or only-a# only-b#)
         (let [only-msg# (str (when only-a# (str "Only in A: " only-a#))
                              (when (and only-a# only-b#) ", ")
                              (when only-b# (str "Only in B: " only-b#)))]
           (ct/do-report {:type :fail, :message ~msg,
                          :expected '~form, :actual only-msg#}))
         (ct/do-report {:type :pass, :message ~msg,
                        :expected '~form, :actual nil})))))

(defn valid-property? [prop]
  (some #{prop} props/configurable-properties))

(defn set-property [prop val]
  (assert (valid-property? prop) (str "Tried to set unknown property '" prop "'"))
  (System/setProperty prop (if (keyword? val)
                             (name val)
                             (str val))))

(defn clear-property [prop]
  (assert (valid-property? prop) (str "Tried to clear unknown property '" prop "'"))
  (System/clearProperty prop))

(defn with-properties- [properties-map f]
  (doseq [[property value] properties-map]
    (set-property property value))
  (f)
  (doseq [property (keys properties-map)]
    (clear-property property)))

(defmacro with-properties [properties-vec & sexprs]
  `(with-properties- ~(apply hash-map properties-vec)
     (fn [] ~@sexprs)))

(defn fixture-properties:clean [f]
  ;; Remove any set system properties, presumably from a previous test
  ;; run
  (for [configurable-property props/configurable-properties]
    (clear-property configurable-property))
  ;; Override any properties that are in the default properties file
  ;; yet are unsafe/undesirable for tests
  (with-properties ["ctia.http.dev-reload" false
                    "ctia.http.min-threads" 9
                    "ctia.http.max-threads" 10
                    "ctia.nrepl.enabled" false
                    "ctia.hook.es.enabled" false
                    "ctia.hook.redis.enabled" false
                    "ctia.hook.redis.channel-name" "events-test"]
    ;; run tests
    (f)))

(defn fixture-property [prop val]
  (fn [test]
    (set-property prop val)
    (test)))

(defn fixture-properties:atom-store [f]
  ;; Set properties to enable the atom store
  (with-properties ["ctia.store.actor" "memory"
                    "ctia.store.feedback" "memory"
                    "ctia.store.campaign" "memory"
                    "ctia.store.coa" "memory"
                    "ctia.store.exploit-target" "memory"
                    "ctia.store.identity" "memory"
                    "ctia.store.incident" "memory"
                    "ctia.store.indicator" "memory"
                    "ctia.store.judgement" "memory"
                    "ctia.store.sighting" "memory"
                    "ctia.store.ttp" "memory"]
    (f)))

(defn fixture-properties:redis-hook [f]
  (with-properties ["ctia.hook.redis.enabled" true]
    (f)))

(defn fixture-properties:es-hook-aliased-index [f]
  (with-properties ["ctia.hook.es.enabled" true
                    "ctia.hook.es.slicing.strategy" :aliased-index]
    (f)))

(defn fixture-properties:es-hook-filtered-alias [f]
  (with-properties ["ctia.hook.es.enabled" true
                    "ctia.hook.es.slicing.strategy" :filtered-alias]
    (f)))

(defn fixture-properties:hook-classes [f]
  (with-properties ["ctia.hooks.before-create"
                    (str/join "," ["ctia.hook.AutoLoadedJar1"
                                   "hook-example.core/hook-example-1"
                                   "ctia.hook.AutoLoadedJar2"
                                   "hook-example.core/hook-example-2"])]
    (f)))

(defn available-port []
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(defn fixture-ctia
  ([test] (fixture-ctia test true))
  ([test enable-http?]
   ;; Start CTIA
   ;; This starts the server on a random port (if enabled)
   (let [http-port (if enable-http?
                     (available-port)
                     3000)]
     (with-properties ["ctia.http.enabled" enable-http?
                       "ctia.http.port" http-port
                       "ctia.http.show.port" http-port]
       (try
         (init/start-ctia! :join? false
                           :silent? true)
         (test)
         (finally
           ;; explicitly stop the http-server
           (http-server/stop!)
           (hooks/shutdown!)
           (events/shutdown!)))))))

(defn fixture-ctia-fast [test]
  (fixture-ctia test false))

(defn fixture-schema-validation [f]
  (schema/with-fn-validation
    (f)))

;; TODO - Convert this to a properties fixture
(defn fixture-allow-all-auth [f]
  (let [orig-auth-srvc @auth/auth-service]
    (reset! auth/auth-service (aa/->AuthService))
    (f)
    (reset! auth/auth-service orig-auth-srvc)))

(defn set-capabilities! [login role caps]
  (store/create-identity @store/identity-store
                         {:login login
                          :role role
                          :capabilities caps}))

(defn url
  ([path]
   (url path (get-in @props/properties [:ctia :http :port])))
  ([path port]
   (let [url (format "http://localhost:%d/%s" port path)]
     (assert (url/encoded? url)
             (format "URL '%s' is not encoded" url))
     url)))

;; Replace this with clojure.string/includes? once we are at Clojure 1.8
(defn includes?
  [^CharSequence s ^CharSequence substr]
  (.contains (.toString s) substr))

(defn content-type? [expected-str]
  (fn [test-str]
    (if (some? test-str)
      (includes? (name test-str) expected-str)
      false)))

(def json? (content-type? "json"))

(def edn? (content-type? "edn"))

(defn parse-body
  ([http-response]
   (parse-body http-response nil))
  ([{{content-type "Content-Type"} :headers
     body :body}
    default]
   (try
     (cond
       (edn? content-type) (edn/read-string body)
       (json? content-type) (json/parse-string body)
       :else default)
     (catch Exception e
       (binding [*out* *err*]
         (println "------- BODY ----------")
         (clojure.pprint/pprint body)
         (println "------- EXCEPTION ----------")
         (clojure.pprint/pprint e))))))

(defn encode-body
  [body content-type]
  (cond
    (edn? content-type) (pr-str body)
    (json? content-type) (json/generate-string body)
    :else body))

(defn get [path & {:as options}]
  (let [options
        (merge {:accept :edn
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               options)

        response
        (http/get (url path)
                  options)]
    (assoc response :parsed-body (parse-body response))))

(defn post [path & {:as options}]
  (let [{:keys [body content-type]
         :as options}
        (merge {:content-type :edn
                :accept :edn
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               options)

        response
        (http/post (url path)
                   (-> options
                       (cond-> body (assoc :body (encode-body body content-type)))))]
    (assoc response :parsed-body (parse-body response))))

(defn delete [path & {:as options}]
  (http/delete (url path)
               (merge {:throw-exceptions false}
                      options)))

(defn put [path & {:as options}]
  (let [{:keys [body content-type]
         :as options}
        (merge {:content-type :edn
                :accept :edn
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               options)

        response
        (http/put (url path)
                  (-> options
                      (cond-> body (assoc :body (encode-body body content-type)))))]
    (assoc response :parsed-body (parse-body response))))

(defn common= [& ms]
  (let [key-paths (apply map/keys-in-all ms)
        common (fn [m]
                 (reduce (fn [accum key-path]
                           (assoc-in accum key-path (get-in m key-path)))
                         {}
                         key-paths))]
    (assert (seq key-paths), "No common paths between maps")
    (apply = (map common ms))))

(defn normalize [entity]
  (->> entity
       (transform path/walk-dates
                  time/format-date-time)))

(defn encode [s]
  {:pre [(string? s) (seq s)]}
  (url/encode s))

(defn decode [s]
  {:pre [(string? s) (seq s)]}
  (url/decode s))

(defmacro deftest-for-each-fixture [test-name fixture-map & body]
  `(do
     ~@(for [[name-key fixture-fn] fixture-map]
         `(clojure.test/deftest ~(with-meta (symbol (str test-name "-" (name name-key)))
                                   {(keyword name-key) true})
            (~fixture-fn (fn [] ~@body))))))
