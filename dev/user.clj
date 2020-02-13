(ns user
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-momo.lib.time :as time]
            [ctim.schemas.vocabularies :as vocab]))

(defn- judgement-search-url
  ([]
   (judgement-search-url "https://tenzin-beta.amp.cisco.com/elasticsearch/"
                         "ctia_judgement"))
  ([url-prefix index]
   (str url-prefix index "/_search")))

(defn- query-es-observables [& {:keys [auth-str max-per-type search-url]
                                :or {max-per-type 1000
                                     search-url (judgement-search-url)}}]
  (assert (re-matches #"\w+:\w+" auth-str) "Bad auth string")

  (-> (http/post
       search-url
       {:basic-auth auth-str
        :headers {"kbn-xsrf" ""}
        :as :json-strict
        :content-type :json
        :throw-exceptions false
        :body (json/generate-string
               {:aggs
                {:types
                 {:terms {:field "observable.type"
                          :size (count vocab/observable-type-identifier)}
                  :aggs {:values
                         {:terms {:field "observable.value"
                                  :size max-per-type}}}}}
                :size 0})})
      :body))

(defn- fixup-observables [query-response]
  (for [{obs-type :key, :as type-bucket} (get-in query-response
                                                 [:aggregations :types :buckets])
        {obs-value :key :as value-bucket} (get-in type-bucket [:values :buckets])]
    {:type obs-type
     :value obs-value}))

(defn find-observables [auth-str max-per-type]
  (-> (query-es-observables :auth-str auth-str
                            :max-per-type max-per-type)
      fixup-observables))

(defn find-judgement-for-verdict [{:keys [type value] :as _observable_}
                                  & {:keys [auth-str search-url]
                                     :or {search-url (judgement-search-url)}}]
  (assert (re-matches #"\w+:\w+" auth-str) "Bad auth string")

  (let [now (time/now)]
    (->> (http/post
          search-url
          {:basic-auth auth-str
           :headers {"kbn-xsrf" ""}
           :as :json-strict
           :content-type :json
           :throw-exceptions false
           :body (json/generate-string
                  {:query
                   {:bool
                    {:must [{:term {"observable.type" type}}
                            {:term {"observable.value" value}}]
                     :filter [{:range {"valid_time.start_time"
                                       {"lte" (time/format-date-time now)}}}
                              {:range {"valid_time.end_time"
                                       {"gt" (time/format-date-time now)}}}]}}
                   :sort
                   [{:priority "desc"}
                    {:disposition "asc"}
                    {"valid_time.start_time" "asc"}]
                   :size 1})})
         :body
         :hits
         :hits
         first
         :_source)))

(defn find-all-judgements [{:keys [type value] :as _observable_}
                           & {:keys [auth-str search-url size]
                              :or {search-url (judgement-search-url)
                                   size 1000}}]
  (assert (re-matches #"\w+:\w+" auth-str) "Bad auth string")

  (let [now (time/now)]
    (->> (http/post
          search-url
          {:basic-auth auth-str
           :headers {"kbn-xsrf" ""}
           :as :json-strict
           :content-type :json
           :throw-exceptions false
           :body (json/generate-string
                  {:query
                   {:bool
                    {:must [{:term {"observable.type" type}}
                            {:term {"observable.value" value}}]}}
                   :sort
                   [{:priority "desc"}
                    {:disposition "asc"}
                    {"valid_time.start_time" "asc"}]
                   :size size})})
         :body
         :hits
         :hits
         (map :_source))))

(defn- ctia-verdict-url [{:keys [type value] :as _observable_}]
  (str "https://tenzin-beta.amp.cisco.com/ctia/" type "/" value "/verdict"))

#_(defn- get-ctia-verdict [observable & {:keys [auth-str]}]
  (clojure.pprint/pprint ['observable observable])
  (assert (re-matches #"\w+:\w+" auth-str) "Bad auth string")

  (let [{:keys [body status]}
        (loop [tries 3]
          (when (< tries 3)
            (Thread/sleep (+ 1 (rand-int 1000))))
          (clojure.pprint/pprint ['try tries])
          (when (< 0 tries)
            (let [{:keys [status] :as response}
                  (http/get
                   (ctia-verdict-url observable)
                   {:basic-auth auth-str
                    :as :json-strict
                    :content-type :json
                    :throw-exceptions false})]
              (if (= status 200)
                response
                (recur (dec tries))))))]
    (assert (= 200 status))
    body))

(defn- verdict-search-url
  ([]
   (judgement-search-url "https://tenzin-beta.amp.cisco.com/elasticsearch/"
                         "ctia_verdict"))
  ([url-prefix index]
   (str url-prefix index "/_search")))

(defn find-verdict [{:keys [type value] :as _observable_}
                     & {:keys [auth-str search-url]
                        :or {search-url (verdict-search-url)}}]
  (assert (re-matches #"\w+:\w+" auth-str) "Bad auth string")

  (let [now (time/now)]
    (-> (http/post
         search-url
         {:basic-auth auth-str
          :headers {"kbn-xsrf" ""}
          :as :json-strict
          :content-type :json
          :throw-exceptions false
          :body (json/generate-string
                 {:query
                  {:bool
                   {:must [{:term {"observable.type" type}}
                           {:term {"observable.value" value}}]
                    :filter [{:range {"valid_time.start_time"
                                      {"lte" (time/format-date-time now)}}}
                             {:range {"valid_time.end_time"
                                      {"gt" (time/format-date-time now)}}}]}}
                  :sort {:created "desc"}
                  :size 1})})
        :body
        :hits
        :hits
        first
        :_source)))

(defn find-all-verdicts [{:keys [type value] :as _observable_}
                         & {:keys [auth-str search-url size]
                            :or {search-url (verdict-search-url)
                                 size 1000}}]
  (assert (re-matches #"\w+:\w+" auth-str) "Bad auth string")

  (let [now (time/now)]
    (->> (http/post
          search-url
          {:basic-auth auth-str
           :headers {"kbn-xsrf" ""}
           :as :json-strict
           :content-type :json
           :throw-exceptions false
           :body (json/generate-string
                  {:query
                   {:bool
                    {:must [{:term {"observable.type" type}}
                            {:term {"observable.value" value}}]}}
                   :sort {:created "desc"}
                   :size size})})
         :body
         :hits
         :hits
         (map :_source))))

(defn- bad-verdict? [expected-judgement actual-verdict]
  (not= (:judgement_id actual-verdict) (:id expected-judgement)))

(defn find-bad-verdicts [observables & {:keys [auth-str]}]
  (letfn [(lz:find-bad-verdicts [observables]
            (if (seq observables)
              (let [observable (first observables)
                    judgement (find-judgement-for-verdict observable
                                                          :auth-str auth-str)
                    verdict (find-verdict observable
                                          :auth-str auth-str)]
                (println "Testing" observable)
                (if (bad-verdict? judgement verdict)
                  (cons observable
                        (lazy-seq
                         (lz:find-bad-verdicts (rest observables))))
                  (lazy-seq
                   (lz:find-bad-verdicts (rest observables)))))
              observables))]
    (lz:find-bad-verdicts observables)))
