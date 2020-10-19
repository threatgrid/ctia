(ns user
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-momo.lib.time :as time]
            [ctia.init :as init]
            [ctia.properties :as p]
            [ctim.schemas.vocabularies :as vocab]
            [puppetlabs.trapperkeeper.app :as app]))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;
;; Lifecycle management
;;;;;;;;;;;;;;;;;;;;;;;

;; implementation details for #'serially-alter-system
(doto (intern (create-ns 'ctia.repl.system-var) 'system)
  ;; set root binding of var to nil only once
  (alter-var-root 
    #(if (instance? clojure.lang.Var$Unbound %)
       nil
       %)))
(doto (intern 'ctia.repl.system-var 'semaphore)
  (alter-var-root #(if (instance? clojure.lang.Var$Unbound %)
                     (java.util.concurrent.Semaphore. 1)
                     %)))

(defn current-app
  "Returns the current app, or nil."
  []
  ctia.repl.system-var/system)

(defmacro serially-alter-system
  "Alters the current app (the 'system'), except throws if parallelism is
  detected during swap.
  
  Implementation is robust to namespace refreshes by defining a
  semaphore in a namespace that is unlikely to be refreshed. This
  is why we don't simply defonce the semaphore in this namespace."
  [f & args]
  `(let [^java.util.concurrent.Semaphore
         ;; interned above
         s# ctia.repl.system-var/semaphore
         has-lock# (.tryAcquire s#)]
     (try (if has-lock#
            (alter-var-root #'ctia.repl.system-var/system
                            ;; `constantly` to remove side effects
                            (constantly
                              (~f (current-app) ~@args)))
            (throw (ex-info "Lifecycle management parallelism!"
                            {})))
          (finally
            (when has-lock#
              (.release s#))))))

(defn start
  "Starts CTIA with given config and services, otherwise defaults
  to the same configuration as #'init/start-ctia."
  [& {:keys [config services] :as m}]
  (serially-alter-system 
    (fn [app]
      (println "Starting CTIA...")
      (if app
        (do (println "CTIA already started!")
            app)
        (init/start-ctia! m)))))

(defn stop
  "Stops CTIA."
  []
  (serially-alter-system
    (fn [app]
      (println "Stopping CTIA...")
      (if app
        (app/stop app)
        (println "CTIA already stopped!"))
      nil)))

(defn go
  "Restarts CTIA. Same args as #'start."
  [& {:keys [config services] :as m}]
  (serially-alter-system
    (fn [app]
      (println "Restarting CTIA...")
      (some-> app app/stop)
      (init/start-ctia! m))))

;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;

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
        :as :json
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
           :as :json
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
           :as :json
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
                    :as :json
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
          :as :json
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
           :as :json
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
