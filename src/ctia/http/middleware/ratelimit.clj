(ns ctia.http.middleware.ratelimit
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ctia
             [auth :as auth]
             [properties :refer [get-global-properties]]
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
  [conf]
  (let [group-limits-conf (get-in conf [:limits :group])
        group-limits (parse-group-limits (:customs group-limits-conf))]
    (fn [request]
      (when-let [[group limit]
                 (some-> (:identity request)
                         auth/groups
                         (with-limit
                           (:default group-limits-conf)
                           group-limits)
                         sort-group-limits
                         first)]
        {:nb-request-per-hour limit
         :rate-limit-key group
         :name-in-headers "GROUP"}))))

(s/defn with-identity-rate-limit-fn :- LimitFunction
  [limit-fn]
  (fn [request]
    (let [auth-identity (:identity request)]
      (when-let [identity-limit-fn
                 (auth/rate-limit-fn auth-identity limit-fn)]
        (identity-limit-fn request)))))

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
                 :limit-fns [(with-identity-rate-limit-fn
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
