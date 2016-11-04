(ns ctia.flows.hooks.event-hooks
  (:require
   [clojure.tools.logging :as log]
   [ctia.events :as events]
   [ctia.flows.hook-protocol :refer [Hook]]
   [ctia.lib.redis :as lr]
   [ctia.domain.entities :as entities]
   [ctia.events.producers.es.producer :as esp]
   [ctia.properties :refer [properties]]
   [ctia.store :as store]
   [schema.core :as s]
   [ctia.schemas.core :refer [Verdict
                              StoredVerdict
                              StoredJudgement]]
   [redismq.core :as rmq]))

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
  (let [{:keys [channel-name timeout-ms host port] :as redis-config}
        (get-in @properties [:ctia :hook :redis])]
    (->RedisEventPublisher (lr/server-connection host port timeout-ms)
                           channel-name)))

(defrecord RedisMQPublisher [queue]
  Hook
  (init [self]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [self event _]
    (rmq/enqueue (get self :queue) event)
    event))

(defn redismq-publisher []
  (let [{:keys [queue-name host port timeout-ms max-depth enabled]
         :as config
         :or {queue-name "ctim-event-queue"
              host "localhost"
              port 6379}}
        (get-in @properties [:ctia :hook :redismq])]
    (->RedisMQPublisher (rmq/make-queue queue-name
                                        {:host host
                                         :port port
                                         :timeout-ms timeout-ms}
                                        {:max-depth max-depth}))))

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
  (and s (.startsWith s ss)))

(s/defn realize-verdict-wrapper :- StoredVerdict
  "Realizes a verdict, using the associated judgement ID, if available,
   to build the verdict ID"
  [verdict :- Verdict
   {id :id :as judgement} :- StoredJudgement
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
    (when (judgement? event)
      (let [{{observable :observable :as judgement} :entity owner :owner} event]
        (when-let [new-verdict
                   (some->
                    (store/read-store :judgement
                                      store/calculate-verdict
                                      observable)
                    (realize-verdict-wrapper judgement owner))]
          (store/write-store :verdict store/create-verdicts [new-verdict]))))
    event))

(s/defn register-hooks :- {s/Keyword [(s/protocol Hook)]}
  [hooks-m :- {s/Keyword [(s/protocol Hook)]}]
  (let [{{redis-enabled? :enabled} :redis
         {redismq-enabled? :enabled} :redismq
         {es-enabled? :enabled} :es}
        (get-in @properties [:ctia :hook])]
    (cond-> hooks-m
      redis-enabled? (update :event #(conj % (redis-event-publisher)))
      redismq-enabled? (update :event #(conj % (redismq-publisher)))
      es-enabled?    (update :event #(conj % (es-event-producer)))
      :always        (update :event #(conj % (->ChannelEventPublisher)))
      :always        (update :event #(conj % (->VerdictGenerator))))))
