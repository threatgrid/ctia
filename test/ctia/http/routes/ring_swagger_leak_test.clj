(ns ctia.http.routes.ring-swagger-leak-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.swagger.coerce :as sut]))

(defmacro when-class [cls & body]
  (when (try (Class/forName (str cls))
             (catch Exception _))
    body))

;;JDK 9+
(when-class java.lang.ref.Cleaner
  (deftest ring-swagger-leak-test
    (let [cleaner (java.lang.ref.Cleaner/create)]
      (let [released? (atom false)
            collectable (doto (fn [])
                          sut/time-matcher
                          sut/custom-matcher
                          sut/coercer)
            _ (.register cleaner collectable (reify
                                               Runnable
                                               (run [_] (reset! released? true))))]
        (reduce (fn [_ i] 
                  (System/gc)
                  (System/runFinalization)
                  (Thread/sleep 100)
                  (if @released?
                    (reduced nil)
                    (println "WARNING: potential memory leak in" `ring-swagger-leak-test)))
                nil (range 100))
        (is @released?)))))
