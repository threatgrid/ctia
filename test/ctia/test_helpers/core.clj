(ns ctia.test-helpers.core
  (:refer-clojure :exclude [get])
  (:require [ctia.auth :as auth]
            [ctia.auth.allow-all :as aa]
            [ctia.properties :as props]
            [ctia.store :as store]
            [ctia.stores.atom.store :as as]
            [ctia.events.producer :as producer]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.data :as cd]
            [clojure.edn :as edn]
            [clojure.test :as ct]
            [ring.adapter.jetty :as jetty]
            [schema.core :as schema]))

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

(defn fixture-properties [f]
  (props/init! "ctia-test.properties")
  (f))


(defn fixture-schema-validation [f]
  (schema/with-fn-validation
    (f)))

(defn fixture-allow-all-auth [f]
  (let [orig-auth-srvc @auth/auth-service]
    (reset! auth/auth-service (aa/->AuthService))
    (f)
    (reset! auth/auth-service orig-auth-srvc)))

(defn init-atom [f]
  (fn []
    (f (atom {}))))

(def atom-stores
  {store/actor-store          (init-atom as/->ActorStore)
   store/judgement-store      (init-atom as/->JudgementStore)
   store/feedback-store       (init-atom as/->FeedbackStore)
   store/campaign-store       (init-atom as/->CampaignStore)
   store/coa-store            (init-atom as/->COAStore)
   store/exploit-target-store (init-atom as/->ExploitTargetStore)
   store/incident-store       (init-atom as/->IncidentStore)
   store/indicator-store      (init-atom as/->IndicatorStore)
   store/sighting-store       (init-atom as/->SightingStore)
   store/ttp-store            (init-atom as/->TTPStore)
   store/identity-store       (init-atom as/->IdentityStore)})

(defn fixture-store [store-map]
  (fn [f]
    (doseq [[store impl-fn] store-map]
      (reset! store (impl-fn)))
    (f)
    (doseq  [store (keys store-map)]
      (reset! store nil))))

(defn fixture-producers [producers]
  (fn [f]
    (dorun
     (map #(swap! producer/event-producers conj (%)) producers))
    (f)
    (reset! producer/event-producers [])))


(def fixture-atom-store (fixture-store atom-stores))

(def http-port 3000)

(defn fixture-server [app & {:keys [port]
                             :or {port http-port}}]
  (fn [f]
    (let [server (jetty/run-jetty app
                                  {:host "localhost"
                                   :port port
                                   :join? false})]
      (f)
      (.stop server))))

(defn set-capabilities! [login role caps]
  (store/create-identity @store/identity-store
                         {:login login
                          :role role
                          :capabilities caps}))

(defn url
  ([path]
   (url path http-port))
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
                :throw-exceptions false}
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
                :socket-timeout 2000
                :conn-timeout 2000}
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
                :socket-timeout 2000
                :conn-timeout 2000}
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
