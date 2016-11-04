(ns ctia.flows.hooks.event-hooks
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

(s/defn realize-verdict-wrapper :- StoredVerdict
  "Realizes a verdict, using the associated judgement ID, if available,
   to build the verdict ID"
  [verdict :- Verdict
   {j-lng-id :id :as judgement} :- StoredJudgement
   owner :- s/Str]
  (letfn [(verdict-id [i] (str "verdict-" (subs i (count judgement-prefix))))]
    (let [j-id-obj (id/long-id->id j-lng-id)
          j-shrt-id (:short-id j-id-obj)]
      (if (str/starts-with? j-shrt-id judgement-prefix)
        (entities/realize-verdict verdict (verdict-id j-shrt-id) owner)
        (entities/realize-verdict verdict owner)))))

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
         {es-enabled? :enabled} :es}
        (get-in @properties [:ctia :hook])]
    (cond-> hooks-m
      redis-enabled? (update :event #(conj % (redis-event-publisher)))
      :always        (update :event #(conj % (->ChannelEventPublisher)))
      :always        (update :event #(conj % (->VerdictGenerator))))))
