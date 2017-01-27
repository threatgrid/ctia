(ns ctia.flows.hooks.event-hooks
  (:require [clojure.tools.logging :as log])
  (:require
    [clojure.string :as str]
    [ctia.domain.entities :as entities]
    [ctia.events :as events]
    [ctia.flows.hook-protocol :refer [Hook]]
    [ctia.lib.redis :as lr]
    [ctia.properties :refer [properties]]
    [ctia.schemas.core :refer [Verdict
                               StoredVerdict
                               StoredJudgement]]
    [ctia.store :as store]
    [ctim.domain.id :as id]
    [ctim.events.schemas :refer [CreateEventType
                                 DeleteEventType]]
    [redismq.core :as rmq]
    [schema.core :as s]))

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
  [{{t :type} :entity :as _event_}]
  (= "judgement" t))

(defn- create-event?
  [{type :type :as _event_}]
  (= type CreateEventType))

(defn- delete-event?
  [{type :type :as _event_}]
  (= type DeleteEventType))

(def judgement-prefix "judgement-")

(s/defn realize-verdict-wrapper :- StoredVerdict
  "Realizes a verdict, using the associated judgement ID, if available,
   to build the verdict ID"
  [verdict :- Verdict
   {j-lng-id :id :as judgement} :- StoredJudgement
   owner :- s/Str
   verdict-id :- s/Str]
  (let [j-id-obj (id/long-id->id j-lng-id)
        j-shrt-id (:short-id j-id-obj)]
    (if (str/starts-with? j-shrt-id judgement-prefix)
      (entities/realize-verdict verdict verdict-id owner)
      (entities/realize-verdict verdict owner))))

(defrecord VerdictGenerator []
  Hook
  (init [_]
    :nothing)
  (destroy [_]
    :nothing)
  (handle [_ event _]
    (log/info "VERDICT HANDLER FIRED")
    (when (judgement? event)
      (let [{{:keys [id observable] :as judgement} :entity owner :owner} event
            judgement-id (id/str->short-id id)
            verdict-id (str "verdict-" (subs judgement-id (count judgement-prefix)))]
        (cond
          (create-event? event)
          (when-let [new-verdict
                     (some->
                      (store/read-store :judgement
                                        store/calculate-verdict
                                        observable)
                      (realize-verdict-wrapper judgement owner verdict-id))]
            (log/info "Generated New Verdict:" (pr-str new-verdict))
            (store/write-store :verdict store/create-verdicts [new-verdict]))

          (delete-event? event)
          (do (store/write-store :verdict
                                 store/delete-verdict
                                 verdict-id)
              (log/info "Deleted Verdict:" verdict-id)))))
    event))

(s/defn register-hooks :- {s/Keyword [(s/protocol Hook)]}
  [hooks-m :- {s/Keyword [(s/protocol Hook)]}]
  (let [{{redis? :enabled} :redis
         {redismq? :enabled} :redismq}
        (get-in @properties [:ctia :hook])]
    (cond-> hooks-m
      ;;true (update :event #(conj % (->ChannelEventPublisher)))
      true (update :event #(conj % (->VerdictGenerator)))
      redis?   (update :event #(conj % (redis-event-publisher)))
      redismq? (update :event #(conj % (redismq-publisher))))))
