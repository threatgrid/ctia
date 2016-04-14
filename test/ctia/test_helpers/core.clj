(ns ctia.test-helpers.core
  (:refer-clojure :exclude [get])
  (:require [ctia.auth :as auth]
            [ctia.auth.allow-all :as aa]
            [ctia.http.server :as http-server]
            [ctia.init :as init]
            [ctia.properties :as props]
            [ctia.store :as store]
            [ctia.stores.atom.store :as as]
            [ctia.events.producer :as producer]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.data :as cd]
            [clojure.edn :as edn]
            [clojure.test :as ct]
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
  (assert (valid-property? prop) "Tried to set unknown property")
  (System/setProperty prop (str val)))

(defn clear-property [prop]
  (assert (valid-property? prop) "Tried to clear unknown property")
  (System/clearProperty prop))

(defn with-properties- [properties-map f]
  (doseq [[property value] properties-map]
    (set-property property (str value)))
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
  (set-property "ctia.http.dev-reload" false)
  (set-property "ctia.http.min-threads" 9)
  (set-property "ctia.http.max-threads" 10)
  (set-property "ctia.nrepl.enabled" false)
  ;; Run tests
  (f))

(defn fixture-properties:atom-store [f]
  ;; Set properties to enable the atom store
  (with-properties ["ctia.store.type" "memory"]
    (f)))

(defn available-port []
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(defn fixture-ctia [test]
  ;; Start CTIA
  ;; This starts the server on a random port, once for each
  ;; (test), which _might_ briefly use up a lot of ports
  (with-properties ["ctia.http.port" (available-port)]
    (try
      (init/start-ctia! :join? false
                       :silent? true)
      (test)
      (finally
        ;; explicitly stop the http-server
        (http-server/stop!)))))

(defn fixture-schema-validation [f]
  (schema/with-fn-validation
    (f)))

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
   (format "http://localhost:%d/%s" port path)))

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
   (cond
     (edn? content-type) (edn/read-string body)
     (json? content-type) (json/parse-string body)
     :else default)))

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

(defmacro deftest-for-each-fixture [test-name fixture-map & body]
  `(do
     ~@(for [[name-key fixture-fn] fixture-map]
         `(clojure.test/deftest ~(with-meta (symbol (str test-name "-" (name name-key)))
                                   {(keyword name-key) true})
            (~fixture-fn (fn [] ~@body))))))
