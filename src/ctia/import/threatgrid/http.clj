(ns ctia.import.threatgrid.http
  (:require [clj-http.client :as http]
            [clj-http.conn-mgr :as conn]
            [clojure.core.async :as async]
            [clojure.java.io :as io]))

(defn tg-uri [feed-file-name]
  (str "https://panacea.threatgrid.com/api/v3/feeds/" feed-file-name))

;; get feed data via direct http call
(defn feed-file-names [feed-names dates]
  (for [feed-name feed-names
        date-str dates]
    (str feed-name "_" date-str ".json")))

(defn body-empty? [body]
  (or (empty? body)
      (= "[]" body)))

(defn download-feeds
  [{:keys [feed-path feed-names dates tg-api-key] :as cli-options}]
  (let [options {:query-params {"api_key" tg-api-key}}]
    ;; don't download a feed if we already have it
    (doseq [feed-file-name (filter #(not (.exists (io/as-file (str feed-path "/" %))))
                                   (feed-file-names feed-names dates))]
      (Thread/sleep 1000) ;; a courtesy
      (let [body (:body (http/get (tg-uri feed-file-name) options))]
        (if-not (body-empty? body)
          (let [file-path (str feed-path "/" feed-file-name)]
            (println "Downloading" file-path)
            (with-open [wrtr (io/writer file-path)]
              (.write wrtr body)))
          (println feed-file-name "is empty."))))))

(defn retry
  [retries f & args]
  (let [res (try {:value (apply f args)}
                 (catch Exception e
                   (if (zero? retries)
                     (throw e)
                     {:exception e})))]
    (if (:exception res)
      (recur (dec retries) f args)
      (:value res))))

(defn post-to-ctia
  [expressions xtype ctia-uri]
  (let [target-uri (str ctia-uri "/ctia/bulk")
        conn-mgr (conn/make-reusable-conn-manager {:timeout 5
                                                   :threads 16
                                                   :default-per-route 16})]
    (doall
     (pmap (fn [entities]
            (let [options {:connection-manager conn-mgr
                           :content-type :edn
                           :accept :edn
                           :throw-exceptions false
                           :body (pr-str {xtype entities})
                           :query-params {"api_key" "importer"}}
                  results (try
                            (retry 5 http/post target-uri options)
                            (catch java.net.SocketTimeoutException e
                              (println "Socket timed out after 5 retries.")))]
              (get-in results [:body :id])))
           (partition-all 1000 expressions)))))
