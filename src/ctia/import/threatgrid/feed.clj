(ns ctia.import.threatgrid.feed
  (:require [cheshire.core :as cjson]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.core :refer [date-time days]]
            [clj-time.format :refer [unparse formatter]]
            [ctia.lib.time :refer [date-range] :as ctime]
            [ctia.import.threatgrid.feed.indicators :as fi]
            [ctia.import.threatgrid.feed.sightings :as fs]
            [ctia.import.threatgrid.feed.judgements :as fj]
            [clj-http.client :as http]
            [clj-http.conn-mgr :as conn]))

(defn tg-feed-names []
  [;;"autorun-registry" ;; always empty
   "banking-dns"
   "dll-hijacking-dns"
   "doc-net-com-dns"
   "downloaded-pe-dns"
   "dynamic-dns"
   "irc-dns"
   "modified-hosts-dns"
   "parked-dns"
   "public-ip-check-dns"
   "rat-dns"
   ;;"scheduled-tasks" ;; always empty
   "sinkholed-ip-dns"
   "stolen-cert-dns"])

(defn dates
  []
  (mapv
   (fn [dt] (unparse (formatter "yyyy-MM-dd") dt))
   (date-range (date-time 2016 03 01)
               (date-time 2016 03 02)
               (days 1))))

(defn tg-uri [feed-file-name & {:keys [api-key]
                                :or {api-key nil}}]
  (str
   "https://panacea.threatgrid.com/api/v3/feeds/"
   feed-file-name
   (if api-key (str "?api_key=" api-key))))

;; get feed data via direct http call
(defn feed-file-names [feed-names dates]
  (for [feed-name feed-names
        date-str dates]
    (str feed-name "_" date-str ".json")))

(defn body-empty? [body]
  (or (empty? body)
      (= "[]" body)))

(defn file-path [s]
  (str "/var/tg-feeds/" s))

(defn dump-file [file-name file-contents]
  (with-open [wrtr (io/writer file-name)]
    (.write wrtr file-contents)))

(defn download-feeds []
  (let [api-key (or (System/getenv "APIKEY") "")
        tg-feed-uri "https://panacea.threatgrid.com/api/v3/feeds/"]
    (doseq [feed-file-name (filter #(not (.exists (io/as-file (file-path %))))
                                   (feed-file-names (tg-feed-names) (dates)))]
      (Thread/sleep 1000) ;; a courtesy
      (let [body (:body (http/get (tg-uri feed-file-name :api-key api-key)))]
        (if (not (body-empty? body))
          (dump-file (file-path feed-file-name) body))))))

(defn retry
  [retries f & args]
  (let [res (try {:value (apply f args)}
                 (catch Exception e
                   (if (= 0 retries)
                     (throw e)
                     {:exception e})))]
    (if (:exception res)
      (do
        (recur (dec retries) f args))
            (:value res))))

(defn post-to-ctia
  [expressions xtype ctia-uri conn-mgr]
  (let [target-uri (str ctia-uri "/ctia/" xtype)]
    (doall
     (map (fn [expression]
            (let [options {:connection-manager conn-mgr
                           :content-type :edn
                           :accept :edn
                           :throw-exceptions false
                           :body (pr-str expression)
                           :query-params {"api_key" "importer"}}
                  response (try
                             (retry 5 http/post target-uri options)
                             (catch java.net.SocketTimeoutException e
                               (println "Socket timed out after 5 retries.")))]
              response))
          expressions))))

(comment
  (let [indicators (atom {})]
    (doseq [date (dates)
            name (tg-feed-names)
            file-name (filter #(.exists (io/as-file (file-path %)))
                              (feed-file-names [name] [date]))]
      (println "Start loading " file-name)
      (let [uri (tg-uri file-name)
            entries (cjson/parse-string (slurp (file-path file-name)) true)
            description (-> entries first :description)
            ctia-uri "http://localhost:3000"
            conn-mgr (conn/make-reusable-conn-manager {:timeout 5 :threads 8})
            indicator (fi/feed-indicator name description ctia-uri indicators)
            feed {:title name
                  :description description
                  :source file-name
                  :source-uri uri
                  :indicator indicator
                  :observable {:type "domain" :field :domain}
                  :entries entries}]
        (let [judgements (fj/feed->judgements feed)]
          (println "Posting" (count judgements) "judgements to ctia")
          (post-to-ctia judgements "judgement" ctia-uri conn-mgr))
        (let [sightings (fs/feed->sightings feed)]
          (println "Posting" (count sightings) "sightings to ctia")
          (post-to-ctia sightings "sighting" ctia-uri conn-mgr)))
      (println "Done loading " file-name))))
