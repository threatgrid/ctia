(ns ctia.test-helpers.global-routes-service
  (:require [clj-http.fake :as fake]
            [puppetlabs.trapperkeeper.core :as tk]
            [puppetlabs.trapperkeeper.services :refer [service-context]])
  (:import [java.util UUID]))

;; {UUIDStr fake-routes}
(def ^:private ^:dynamic *fake-routes* {})
;; #{UUIDStr}
(def ^:private ^:dynamic *in-isolation* #{})

(defprotocol CTIATestGlobalRoutesService
  (wrap-fake-routes [_ handler])
  ;; same API as clj-http.fake
  (with-fake-routes-in-isolation [_ routes f])
  (with-fake-routes [_ routes f])
  (with-global-fake-routes-in-isolation [_ routes f])
  (with-global-fake-routes [_ routes f]))

(defn ^:private get-matching-route
  [request id]
  (let [flatten-routes @#'fake/flatten-routes]
    (->> (get *fake-routes* id)
         flatten-routes
         (filter #(fake/matches (:address %) (:method %) request))
         first)))

(defn ^:private this-id [this]
  {:post [(string? %)]}
  (-> this service-context :id))

(tk/defservice ctia-test-global-routes-service
  CTIATestGlobalRoutesService
  []
  (init [this context] {:id (str (UUID/randomUUID))})
  (stop [this context] {})
  (wrap-fake-routes
    [this handler]
    (let [id (this-id this)]
      (fn [request]
        ;; based on clj-http.fake/try-intercept
        (let [matching-route (get-matching-route request id)]
          (if matching-route
            (@#'fake/handle-request-for-route request matching-route)
            (if (*in-isolation* id)
              (@#'fake/throw-no-fake-route-exception request)
              (handler request)))))))
  (with-fake-routes-in-isolation
    [this routes f]
    (let [id (this-id this)]
      (binding [*in-isolation* (conj *in-isolation* id)]
        (with-fake-routes this routes f))))
  (with-fake-routes
    [this routes f]
    (let [id (this-id this)]
      (binding [*fake-routes* (assoc *fake-routes* id routes)]
        (f))))
  (with-global-fake-routes-in-isolation
    [this routes f]
    (let [id (this-id this)]
      (with-redefs [*in-isolation* (conj *in-isolation* id)]
        (with-global-fake-routes this routes f))))
  (with-global-fake-routes
    [this routes f]
    (let [id (this-id this)]
      (with-redefs [*fake-routes* (assoc *fake-routes* id routes)]
        (f)))))
