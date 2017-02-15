(ns ctia.http.middleware.un-store)

(defn un-store [m]
  (dissoc m :created :modified :owner))

(defn un-store-all [x]
  (if (sequential? x)
    (map un-store x)
    (un-store x)))

(defn wrap-un-store [handler]
  (fn [request]
    (update (handler request) :body un-store-all)))
