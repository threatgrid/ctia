(ns cia.test-helpers
  (:refer-clojure :exclude [get])
  (:require [cheshire.core :as json]
            [cia.store :as store]
            [cia.stores.memory :as mem]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [ring.adapter.jetty :as jetty]
            [schema.core :as schema]))

(defn fixture-schema-validation [f]
  (schema/with-fn-validation
    (f)))

(defn fixture-in-memory-store [f]
  (let [store-impls {store/actor-store mem/->ActorStore
                     store/judgement-store mem/->JudgementStore
                     store/feedback-store mem/->FeedbackStore
                     store/campaign-store mem/->CampaignStore
                     store/coa-store mem/->COAStore
                     store/exploit-target-store mem/->ExplitTargetStore
                     store/incident-store mem/->IncidentStore
                     store/indicator-store mem/->IndicatorStore
                     store/ttp-store mem/->TTPStore
                     store/verdict-store mem/->VerdictStore}]
    (doseq [[store impl-fn] store-impls]
      (reset! store (impl-fn (atom {}))))
    (f)
    (doseq  [store (keys store-impls)]
      (reset! store nil))))

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
  (let [response (http/get (url path)
                           (merge {:accept :edn
                                   :throw-exceptions false}
                                  options))]
    (assoc response :parsed-body (parse-body response))))

(defn post [path & {:as options}]
  (let [{:keys [body content-type]
         :as options}
        (merge {:content-type :edn
                :accept :edn
                :throw-exceptions false
                :socket-timeout 200
                :conn-timeout 200}
               options)
        response (http/post (url path)
                            (assoc options :body (encode-body body content-type)))]
    (assoc response :parsed-body (parse-body response))))

(defn delete [path & {:as options}]
  (http/delete (url path)
               (merge {:throw-exceptions false}
                      options)))
