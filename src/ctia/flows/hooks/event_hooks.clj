(ns ctia.flows.hooks.event-hooks
  (:require
    [clojure.tools.logging :as log]
    [ctia.events :as events]
    [ctia.flows.hook-protocol :refer [Hook]]
    [ctia.lib.redis :as lr]
    [ctia.domain.entities :as entities]
    [ctia.events.producers.es.producer :as esp]
    [ctia.properties :refer [properties]]
    [ctia.properties.getters :as pg]
    [ctia.store :as store :refer [judgement-store verdict-store]]
    [ctim.schemas.judgement :as js]
    [ctim.schemas.verdict :as vs]
    [schema.core :as s]))

(defrecord ESEventProducer [conn]
  Hook
  (init [_]
    (reset! conn (esp/init-producer-conn)))
  (destroy [_]
    (reset! conn nil))
  (handle [_ event _]
    (try
      (when (some? @conn)
        (esp/handle-produce-event @conn event))
      (catch Exception e
        ;; Should we really be swallowing exceptions?
        (log/error "Exception while producing event" e)))
    event))

(defn es-event-producer []
  (->ESEventProducer (atom {})))

(defrecord RedisEventPublisher [conn publish-channel-name]
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (lr/publish conn
                publish-channel-name
                event)
    event))

(defn redis-event-publisher []
  (let [{:keys [channel-name timeout-ms] :as redis-config}
        (get-in @properties [:ctia :hook :redis])

        [host port]
        (pg/parse-host-port redis-config)]
    (->RedisEventPublisher (lr/server-connection host port timeout-ms)
                           channel-name)))

(defrecord ChannelEventPublisher []
  Hook
  (init [_]
    (assert (some? @events/central-channel)
            "Events central-channel was not setup"))
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (events/send-event event)
    event))

(defn- judgement?
  [{{t :type} :entity :as event}]
  (= "judgement" t))

(def judgement-prefix "judgement-")

(defn starts-with?
  "Added to clojure.string in Clojure 1.8"
  [^String s ^String ss]
  (.startsWith s ss))

(s/defn realize-verdict :- vs/StoredVerdict
  "Realizes a verdict, using the associated judgement ID, if available,
   to build the verdict ID"
  [verdict :- vs/Verdict
   {id :id :as judgement} :- js/StoredJudgement
   owner :- s/Str]
  (letfn [(verdict-id [i] (str "verdict-" (subs i (count judgement-prefix))))]
    (if (starts-with? id judgement-prefix)
      (entities/realize-verdict verdict (verdict-id id) owner)
      (entities/realize-verdict verdict owner))))

(defrecord VerdictGenerator []
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (if (and (judgement? event) @verdict-store)
      (try
        (let [{{observable :observable :as judgement} :entity owner :owner} event]
          (when-let [new-verdict (some-> judgement-store
                                         deref
                                         (store/calculate-verdict observable)
                                         (realize-verdict judgement owner))]
            (store/create-verdict @verdict-store new-verdict)
            new-verdict)
          event)
        (catch Exception e
          (.printStackTrace e)))
      event)))

(s/defn register-hooks :- {s/Keyword [(s/protocol Hook)]}
  [hooks-m :- {s/Keyword [(s/protocol Hook)]}]
  (let [{{redis-enabled? :enabled} :redis
         {es-enabled? :enabled} :es}
        (get-in @properties [:ctia :hook])]
    (cond-> hooks-m
      redis-enabled? (update :event #(conj % (redis-event-publisher)))
      es-enabled?    (update :event #(conj % (es-event-producer)))
      :always        (update :event #(conj % (->ChannelEventPublisher)))
      :always        (update :event #(conj % (->VerdictGenerator))))))
