(ns ctia.http.middleware.ratelimit
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia
             [auth :as auth]
             [properties :refer [properties]]]
            [ctia.lib
             [collection :refer [fmap]]
             [redis :refer [server-connection]]]
            [ring.middleware.turnstile :as turnstile
             :refer
             [default-rate-limit-handler LimitFunction]]
            [schema.core :as s])
  (:import clojure.lang.ExceptionInfo))

(def rate-limited-request-handler
  "Handler called when a request is rate limited"
  (fn [request
       next-slot-in-sec
       {:keys [nb-request-per-hour name-in-headers
               rate-limit-key] :as limit}]
    (let [ctx {:next-slot-in-sec next-slot-in-sec
               :nb-request-per-hour nb-request-per-hour
               :name-in-headers name-in-headers
               :rate-limit-key rate-limit-key}]
      (if (log/enabled? :debug)
        (log/debugf "Rate limited request (request: %s, rate limit:%s)"
                    (pr-str request)
                    ctx)
        (log/infof "Rate limited request, path: %s, identity: %s"
                   (:uri request)
                   (:identity request))))
    (default-rate-limit-handler request next-slot-in-sec limit)))

(defn with-limit
  [groups default-group-limit custom-group-limits]
  (map (fn [group-id]
         [group-id
          (get custom-group-limits
               group-id
               default-group-limit)])
       groups))

(defn parse-group-limits
  "Parses group limits defined in the properties

   Ex: group1|25000,group2|80000"
  [limits]
  (some->> (when limits (string/split limits #","))
           (map #(string/split % #"\|"))
           (fmap #(Integer/parseInt %))))

(defn sort-group-limits
  "Sorts group limits by nb of requests by hour and by group name
   in descending order."
  [group-limits]
  (sort-by (juxt second first) #(compare %2 %1) group-limits))

(s/defn ^:always-validate group-limit-fn :- LimitFunction
  "Builds the group limit function"
  [{:keys [default-group-limit custom-group-limits]}]
  (let [group-limits (parse-group-limits custom-group-limits)]
    (fn [request]
      (when-let [[group limit]
                 (some-> (:identity request)
                         auth/groups
                         (with-limit default-group-limit group-limits)
                         sort-group-limits
                         first)]
        {:nb-request-per-hour limit
         :rate-limit-key group
         :name-in-headers "GROUP"}))))

(s/defn with-rate-limited :- LimitFunction
  [limit-fn]
  (fn [request]
    (let [auth-identity (:identity request)]
      (when (auth/rate-limited? auth-identity)
        (limit-fn request)))))

(defn wrap-rate-limit
  [handler]
  (let [{:keys [redis enabled key-prefix] :as conf}
        (get-in @properties [:ctia :http :rate-limit])]
    (if enabled
      (let [turnstile-mw
            (turnstile/wrap-rate-limit
             handler
             (do
               {:redis-conn (server-connection redis)
                 :limit-fns [(with-rate-limited
                               (group-limit-fn conf))]
                 :rate-limit-handler rate-limited-request-handler
                 :key-prefix key-prefix}))]
        (fn [request]
          (try
            (turnstile-mw request)
            (catch ExceptionInfo e
              (let [origin (-> (ex-data e) :origin)]
                (if (= origin :ring-turnstile-middleware)
                  (do
                    (log/error e "Unexpected error in the rate limit middleware")
                    (handler request))
                  (throw e)))))))
      (do
        (log/warn "Rate limit is disabled")
        handler))))
