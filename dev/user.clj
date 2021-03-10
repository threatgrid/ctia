(ns user
  (:require
   [clojure.tools.namespace.repl :refer [clear refresh refresh-dirs set-refresh-dirs]]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clj-momo.lib.time :as time]
   [ctia.init :as init]
   [ctia.properties :as p]
   [ctim.schemas.vocabularies :as vocab]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.core :as s]))

(set-refresh-dirs "src" "dev" "test")

;;;;;;;;;;;;;;;;;;;;;;;
;; Lifecycle management
;;;;;;;;;;;;;;;;;;;;;;;

;; To avoid losing the current system state, we manually
;; intern a var in some namespace that is unlikely to be
;; reloaded.

;; initialize #'ctia.repl.no-reload/system-state
(s/validate
  {:app s/Any ;; nil or a running App
   ;; coordination mechanism for #'serially-alter-app
   :lock java.util.concurrent.locks.Lock}
  (-> (create-ns 'ctia.repl.no-reload)
      (intern 'system-state)
      (alter-var-root 
        #(if (map? %)
           ;; set root binding exactly once
           %
           {:app nil
            :lock (java.util.concurrent.locks.ReentrantLock.)}))))

(defn get-system-state-var []
  {:post [(var? %)]}
  (resolve 'ctia.repl.no-reload/system-state))

;; we want all definitions of #'serially-alter-app to share the same lock,
;; so we use :lock in the system state. this robustly handles the case where
;; #'serially-alter-app is reloaded while we're calling it.
(defn serially-alter-app
  "Alters the current app, except throws if more than 1 thread
  attempts to alter it simultaneously."
  [f & args]
  (let [system-state-var (get-system-state-var)
        {:keys [^java.util.concurrent.locks.Lock lock]} @system-state-var
        has-lock (.tryLock lock)]
    (try (if has-lock
           (:app
             (alter-var-root system-state-var
                             ;; `constantly` to remove side effects
                             (constantly
                               (update @system-state-var
                                       :app #(apply f % args)))))
           (throw (ex-info "Lifecycle management parallelism!"
                           {})))
         (finally
           (when has-lock
             (.unlock lock))))))

(defn current-app
  "Returns the current app, or nil."
  []
  (:app @(get-system-state-var)))

(defn start
  "Starts CTIA with given config and services, otherwise defaults
  to the same configuration as #'init/start-ctia."
  [& {:keys [config services] :as m}]
  (serially-alter-app 
    (fn [app]
      (println "Starting CTIA...")
      (if app
        (do (println "CTIA already started! Use (go ...) to restart")
            app)
        (init/start-ctia! m)))))

(defn stop
  "Stops CTIA."
  []
  (serially-alter-app
    (fn [app]
      (println "Stopping CTIA...")
      (some-> app app/stop)
      nil)))

(defn go
  "Restarts CTIA. Same args as #'start."
  [& {:keys [config services] :as m}]
  (serially-alter-app
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

(def old-data
  (into {}
        (map (fn [[k v]]
               [(symbol k) v]))
        [[:ctia.task.migration.store-test {:elapsed-ns 241280282836}]
   [:ctia.task.migration.migrate-es-stores-test {:elapsed-ns 216161255484}]
   [:ctia.entity.feed-test {:elapsed-ns 167593429063}]
   [:ctia.entity.weakness-test {:elapsed-ns 159411424267}]
   [:ctia.entity.casebook-test {:elapsed-ns 141356558631}]
   [:ctia.entity.relationship-test {:elapsed-ns 138462950580}]
   [:ctia.entity.incident-test {:elapsed-ns 132405959788}]
   [:ctia.entity.actor-test {:elapsed-ns 128586751842}]
   [:ctia.bundle.routes-test {:elapsed-ns 123332460292}]
   [:ctia.entity.campaign-test {:elapsed-ns 115065205524}]
   [:ctia.entity.vulnerability-test {:elapsed-ns 107503531595}]
   [:ctia.http.routes.graphql-test {:elapsed-ns 104190019274}]
   [:ctia.entity.judgement-test {:elapsed-ns 101169831882}]
   [:ctia.entity.coa-test {:elapsed-ns 98762971212}]
   [:ctia.entity.sighting-test {:elapsed-ns 91641954633}]
   [:ctia.entity.tool-test {:elapsed-ns 91214098085}]
   [:ctia.entity.malware-test {:elapsed-ns 90932327465}]
   [:ctia.entity.indicator-test {:elapsed-ns 89248833508}]
   [:ctia.entity.attack-pattern-test {:elapsed-ns 88016846440}]
   [:ctia.http.routes.graphql.weakness-test {:elapsed-ns 78554450474}]
   [:ctia.http.routes.observable.verdict-test {:elapsed-ns 76192609212}]
   [:ctia.entity.investigation-test {:elapsed-ns 72814355273}]
   [:ctia.stores.es.crud-test {:elapsed-ns 66933613941}]
   [:ctia.entity.target-record-test {:elapsed-ns 61311906675}]
   [:ctia.http.routes.graphql.vulnerability-test {:elapsed-ns 52736569053}]
   [:ctia.http.routes.graphql.incident-test {:elapsed-ns 41443349964}]
   [:ctia.entity.asset-test {:elapsed-ns 41175472645}]
   [:ctia.entity.web-test {:elapsed-ns 40776683713}]
   [:ctia.entity.asset-mapping-test {:elapsed-ns 40712886465}]
   [:ctia.http.routes.pagination-test {:elapsed-ns 38452238547}]
   [:ctia.entity.asset-properties-test {:elapsed-ns 38131349538}]
   [:ctia.http.middleware.ratelimit-test {:elapsed-ns 35210761683}]
   [:ctia.bulk.routes-test {:elapsed-ns 32153918670}]
   [:ctia.http.routes.graphql.attack-pattern-test {:elapsed-ns 31784005312}]
   [:ctia.entity.event-test {:elapsed-ns 31488740734}]
   [:ctia.stores.es.init-test {:elapsed-ns 29843247217}]
   [:ctia.entity.identity-assertion-test {:elapsed-ns 29453243815}]
   [:ctia.http.routes.graphql.tool-test {:elapsed-ns 28352355219}]
   [:ctia.task.rollover-test {:elapsed-ns 27311789209}]
   [:ctia.http.routes.graphql.malware-test {:elapsed-ns 26914322408}]
   [:ctia.http.routes.version-test {:elapsed-ns 26432668009}]
   [:ctia.http.routes.graphql.asset-properties-test {:elapsed-ns 24984713620}]
   [:ctia.http.routes.graphql.asset-test {:elapsed-ns 20695189769}]
   [:ctia.http.routes.graphql.asset-mapping-test {:elapsed-ns 16304985230}]
   [:ctia.http.routes.graphql.investigation-test {:elapsed-ns 15951759338}]
   [:ctia.http.routes.observable.judgements-indicators-test {:elapsed-ns 15744686168}]
   [:ctia.bundle.core-test {:elapsed-ns 15674516436}]
   [:ctia.http.server-test {:elapsed-ns 14020932889}]
   [:ctia.flows.hooks-test {:elapsed-ns 13962627607}]
   [:ctia.http.routes.graphql.target-record-test {:elapsed-ns 13640116873}]
  [:ctia.entity.data-table-test {:elapsed-ns 12735161398}]
  [:ctia.entity.feedback-test {:elapsed-ns 11873962550}]
  [:ctia.flows.hooks.kafka-event-hook-test {:elapsed-ns 10616960997}]
  [:ctia.task.check-es-stores-test {:elapsed-ns 10226903378}]
  [:ctia.events-test {:elapsed-ns 9054702772}]
  [:ctia.stores.identity-store-test {:elapsed-ns 9042105585}]
  [:ctia.lib.redis-test {:elapsed-ns 8033413068}]
  [:ctia.task.update-mapping-test {:elapsed-ns 6635339997}]
  [:ctia.http.routes.observable.sightings-incidents-test {:elapsed-ns 6619672883}]
  [:ctia.http.routes.observable.sightings-indicators-test {:elapsed-ns 6450416862}]
  [:ctia.http.handler.static-auth-test {:elapsed-ns 6340110589}]
  [:ctia.features-service-test {:elapsed-ns 6271897384}]
  [:ctia.http.handler.allow-all-test {:elapsed-ns 5726855064}]
  [:ctia.http.routes.observable.judgements-test {:elapsed-ns 5649802600}]
  [:ctia.http.routes.observable.sightings-test {:elapsed-ns 5346098687}]
  [:ctia.flows.events-test {:elapsed-ns 5191247064}]
  [:ctia.http.routes.status-test {:elapsed-ns 4617831840}]
  [:ctia.entity.entities-test {:elapsed-ns 4581049509}]
  [:ctia.store-test {:elapsed-ns 4333632297}]
  [:ctia.task.settings-test {:elapsed-ns 4272330853}]
  [:ctia.encryption-test {:elapsed-ns 3646975808}]
  [:ctia.http.middleware.cache-control-test {:elapsed-ns 3483306988}]
  [:ctia.http.routes.swagger-json-test {:elapsed-ns 3478538273}]
  [:ctia.flows.hooks.redis-event-hook-test {:elapsed-ns 2708292015}]
  [:ctia.flows.hooks.redismq-event-hook-test {:elapsed-ns 2586551672}]
  [:ctia.http.handler-test {:elapsed-ns 2452206368}]
  [:ctia.http.handler.static-auth-anonymous-test {:elapsed-ns 2402849783}]
  [:ctia.graphql.schemas-test {:elapsed-ns 2059056600}]
  [:ctia.stores.es.mapping-test {:elapsed-ns 1693447780}]
  [:ctia.logger-test {:elapsed-ns 1652178452}]
  [:ctia.schemas.utils-test {:elapsed-ns 276826937}]
  [:ctia.auth.threatgrid-test {:elapsed-ns 222013274}]
  [:ctia.test-helpers.core-test {:elapsed-ns 195672980}]
  [:ctia.bulk.core-test {:elapsed-ns 100785842}]
  [:ctia.domain.access-control-test {:elapsed-ns 95973521}]
  [:ctia.encryption.default-test {:elapsed-ns 74330744}]
  [:ctia.schemas.graphql.helpers-test {:elapsed-ns 50728722}]
  [:ctia.http.middleware.auth-test {:elapsed-ns 28270224}]
  [:ctia.auth.jwt-test {:elapsed-ns 16136632}]
  [:ctia.http.routes.common-test {:elapsed-ns 13243025}]
  [:ctia.task.migration.migrations.describe-test {:elapsed-ns 13108825}]
  [:ctia.task.migration.migrations-test {:elapsed-ns 12383154}]
  [:ctia.schemas.graphql.pagination-test {:elapsed-ns 12089253}]
  [:ctia.lib.utils-test {:elapsed-ns 9709019}]
  [:ctia.flows.crud-test {:elapsed-ns 7720334}]
  [:ctia.task.migration.migrations.investigation-actions-test {:elapsed-ns 7351714}]
  [:ctia.lib.keyword-test {:elapsed-ns 5044022}]
  [:ctia.test-helpers.aggregate {:elapsed-ns 3480715}]
  [:ctia.entity.event.obj-to-event-test {:elapsed-ns 2556705}]
  [:ctia.lib.collection-test {:elapsed-ns 1897508}]
  [:ctia.properties-test {:elapsed-ns 1797807}]
  [:ctia.schemas.graphql.flanders-test {:elapsed-ns 1706203}]
  [:ctia.schemas.graphql.sorting-test {:elapsed-ns 1004302}]
  [:ctia.schemas.test-generators {:elapsed-ns 470602}]
  [:ctia.test-helpers.es {:elapsed-ns 464102}]
  [:ctia.test-helpers.crud {:elapsed-ns 460202}]
  [:ctia.test-helpers.field-selection {:elapsed-ns 458802}]
  [:ctia.test-helpers.pagination {:elapsed-ns 453202}]
  [:ctia.test-helpers.http {:elapsed-ns 434202}]
  [:ctia.test-helpers.auth {:elapsed-ns 426102}]
  [:ctia.store-service-test {:elapsed-ns 405402}]
  [:ctia.test-helpers.core {:elapsed-ns 224800}]
  [:ctia.http.generative.es-store-spec {:elapsed-ns 186300}]
  [:ctia.http.generative.properties {:elapsed-ns 183100}]
  [:ctia.test-helpers.fake-whoami-service {:elapsed-ns 170401}]
  [:ctia.test-helpers.access-control {:elapsed-ns 165900}]
  [:ctia.test-helpers.search {:elapsed-ns 162901}]
  [:ctia.test-helpers.graphql {:elapsed-ns 155501}]
  [:ctia.test-helpers.fixtures {:elapsed-ns 151201}]
  [:ctia.task.purge-es-stores {:elapsed-ns 146401}]
  [:ctia.test-helpers.store {:elapsed-ns 145801}]
  [:ctia.test-helpers.benchmark {:elapsed-ns 143800}]
  [:ctia.test-helpers.migration {:elapsed-ns 143301}]]))

(def new-data
  '{ctia.http.routes.graphql.attack-pattern-test
 {:elapsed-ns 30231811220},
 ctia.task.migration.migrations-test {:elapsed-ns 28015570},
 ctia.test-helpers.pagination {:elapsed-ns 221301},
 ctia.http.routes.observable.judgements-indicators-test
 {:elapsed-ns 6318896073},
 ctia.http.routes.graphql.investigation-test
 {:elapsed-ns 14082101690},
 ctia.http.routes.graphql.weakness-test {:elapsed-ns 64595895377},
 ctia.http.routes.graphql.incident-test {:elapsed-ns 41124056006},
 ctia.lib.keyword-test {:elapsed-ns 4590019},
 ctia.bulk.routes-test {:elapsed-ns 36574878974},
 ctia.entity.sighting-test {:elapsed-ns 99408439695},
 ctia.entity.identity-assertion-test {:elapsed-ns 31594929484},
 ctia.http.routes.graphql-test {:elapsed-ns 92310954406},
 ctia.stores.es.init-test {:elapsed-ns 35697935645},
 ctia.store-service-test {:elapsed-ns 156100},
 ctia.entity.target-record-test {:elapsed-ns 41269435561},
 ctia.lib.utils-test {:elapsed-ns 10823099},
 ctia.flows.events-test {:elapsed-ns 5647582526},
 ctia.task.purge-es-stores {:elapsed-ns 228201},
 ctia.http.routes.observable.verdict-test {:elapsed-ns 24023574028},
 ctia.task.migration.store-test {:elapsed-ns 221446962237},
 ctia.test-helpers.core-test {:elapsed-ns 391806276},
 ctia.http.routes.swagger-json-test {:elapsed-ns 4426677673},
 ctia.encryption.default-test {:elapsed-ns 60368197},
 ctia.entity.entities-test {:elapsed-ns 6607893543},
 ctia.lib.redis-test {:elapsed-ns 3937676896},
 ctia.entity.data-table-test {:elapsed-ns 17870992571},
 ctia.test-helpers.benchmark {:elapsed-ns 267701},
 ctia.http.routes.graphql.asset-test {:elapsed-ns 26990459091},
 ctia.http.handler-test {:elapsed-ns 4562538406},
 ctia.flows.hooks.redis-event-hook-test {:elapsed-ns 3558406219},
 ctia.http.routes.graphql.vulnerability-test
 {:elapsed-ns 51653570899},
 ctia.task.migration.migrations.describe-test {:elapsed-ns 11647279},
 ctia.http.generative.properties {:elapsed-ns 183500},
 ctia.http.routes.graphql.target-record-test
 {:elapsed-ns 13882893214},
 ctia.test-helpers.auth {:elapsed-ns 84000},
 ctia.schemas.graphql.pagination-test {:elapsed-ns 9428539},
 ctia.entity.asset-test {:elapsed-ns 43449021144},
 ctia.test-helpers.access-control {:elapsed-ns 351301},
 ctia.entity.asset-mapping-test {:elapsed-ns 44662019966},
 ctia.entity.judgement-test {:elapsed-ns 99058468633},
 ctia.entity.attack-pattern-test {:elapsed-ns 95416307417},
 ctia.flows.hooks-test {:elapsed-ns 13130193574},
 ctia.encryption-test {:elapsed-ns 4213307164},
 ctia.entity.campaign-test {:elapsed-ns 107725577810},
 ctia.auth.threatgrid-test {:elapsed-ns 240913876},
 ctia.schemas.graphql.sorting-test {:elapsed-ns 1924613},
 ctia.stores.es.crud-test {:elapsed-ns 62973927347},
 ctia.http.routes.graphql.asset-mapping-test
 {:elapsed-ns 18329386061},
 ctia.http.routes.observable.sightings-test {:elapsed-ns 6877338991},
 ctia.test-helpers.migration {:elapsed-ns 353702},
 ctia.task.migration.migrate-es-stores-test
 {:elapsed-ns 225798812325},
 ctia.entity.web-test {:elapsed-ns 89882096002},
 ctia.store-test {:elapsed-ns 3377794618},
 ctia.schemas.graphql.helpers-test {:elapsed-ns 53040058},
 ctia.http.handler.allow-all-test {:elapsed-ns 3892724328},
 ctia.test-helpers.fake-whoami-service {:elapsed-ns 245800},
 ctia.features-service-test {:elapsed-ns 10340150559},
 ctia.entity.relationship-test {:elapsed-ns 134267144989},
 ctia.http.generative.es-store-spec {:elapsed-ns 405201},
 ctia.http.routes.observable.sightings-incidents-test
 {:elapsed-ns 6285089852},
 ctia.schemas.test-generators {:elapsed-ns 86800},
 ctia.http.server-test {:elapsed-ns 8548051483},
 ctia.http.routes.graphql.asset-properties-test
 {:elapsed-ns 12638998922},
 ctia.entity.vulnerability-test {:elapsed-ns 89581305603},
 ctia.http.handler.static-auth-anonymous-test
 {:elapsed-ns 3015871214},
 ctia.graphql.schemas-test {:elapsed-ns 2816476139},
 ctia.entity.feed-test {:elapsed-ns 70441677444},
 ctia.test-helpers.crud {:elapsed-ns 342603},
 ctia.entity.actor-test {:elapsed-ns 121156340983},
 ctia.flows.hooks.redismq-event-hook-test {:elapsed-ns 2364730161},
 ctia.flows.hooks.kafka-event-hook-test {:elapsed-ns 14438020022},
 ctia.auth.jwt-test {:elapsed-ns 25672364},
 ctia.entity.feedback-test {:elapsed-ns 9959260410},
 ctia.test-helpers.store {:elapsed-ns 86900},
 ctia.entity.malware-test {:elapsed-ns 97198422382},
 ctia.task.check-es-stores-test {:elapsed-ns 10412418804},
 ctia.entity.investigation-test {:elapsed-ns 89327576455},
 ctia.task.migration.migrations.investigation-actions-test
 {:elapsed-ns 6973747},
 ctia.test-helpers.field-selection {:elapsed-ns 370103},
 ctia.http.routes.observable.sightings-indicators-test
 {:elapsed-ns 7063516903},
 ctia.entity.casebook-test {:elapsed-ns 133673768690},
 ctia.entity.event.obj-to-event-test {:elapsed-ns 3523500},
 ctia.http.handler.static-auth-test {:elapsed-ns 3239593996},
 ctia.entity.weakness-test {:elapsed-ns 138490141186},
 ctia.test-helpers.graphql {:elapsed-ns 148400},
 ctia.test-helpers.aggregate {:elapsed-ns 3116000},
 ctia.http.routes.pagination-test {:elapsed-ns 31716904999},
 ctia.domain.access-control-test {:elapsed-ns 85373596},
 ctia.task.rollover-test {:elapsed-ns 31447600622},
 ctia.properties-test {:elapsed-ns 1961705},
 ctia.flows.crud-test {:elapsed-ns 9777524},
 ctia.entity.event-test {:elapsed-ns 33195559230},
 ctia.http.routes.common-test {:elapsed-ns 12428984},
 ctia.test-helpers.search {:elapsed-ns 169200},
 ctia.entity.indicator-test {:elapsed-ns 65151071983},
 ctia.lib.collection-test {:elapsed-ns 1688200},
 ctia.stores.es.mapping-test {:elapsed-ns 1080722805},
 ctia.schemas.utils-test {:elapsed-ns 237337587},
 ctia.test-helpers.core {:elapsed-ns 153000},
 ctia.logger-test {:elapsed-ns 3540760766},
 ctia.stores.identity-store-test {:elapsed-ns 4592783154},
 ctia.events-test {:elapsed-ns 8374844572},
 ctia.bulk.core-test {:elapsed-ns 107916728},
 ctia.http.routes.graphql.tool-test {:elapsed-ns 32037244931},
 ctia.http.routes.graphql.malware-test {:elapsed-ns 34647773914},
 ctia.task.update-mapping-test {:elapsed-ns 7008473616},
 ctia.schemas.graphql.flanders-test {:elapsed-ns 1687912},
 ctia.http.routes.status-test {:elapsed-ns 14934007233},
 ctia.test-helpers.fixtures {:elapsed-ns 230601},
 ctia.task.settings-test {:elapsed-ns 4443980558},
 ctia.bundle.core-test {:elapsed-ns 17716646059},
 ctia.http.middleware.cache-control-test {:elapsed-ns 2501354855},
 ctia.entity.asset-properties-test {:elapsed-ns 36872920371},
 ctia.bundle.routes-test {:elapsed-ns 140394568319},
 ctia.entity.tool-test {:elapsed-ns 112449356123},
 ctia.entity.incident-test {:elapsed-ns 105660972287},
 ctia.http.routes.observable.judgements-test
 {:elapsed-ns 5941195715},
 ctia.test-helpers.es {:elapsed-ns 363103},
 ctia.http.middleware.ratelimit-test {:elapsed-ns 28057554885},
 ctia.test-helpers.http {:elapsed-ns 223200},
 ctia.entity.coa-test {:elapsed-ns 90508807212},
 ctia.http.middleware.auth-test {:elapsed-ns 63860859},
 ctia.http.routes.version-test {:elapsed-ns 24607632413}})

(comment
  (/ (/ (apply + (map (comp :elapsed-ns second) old-data))
        1e+9)
     60)
  (/ (/ (apply + (map (comp :elapsed-ns second) new-data))
        1e+9)
     60)
  (sort-by (comp :seconds-speedup val)
           >
           (merge-with (fn [old new]
                {:seconds-speedup
                 (/ (:elapsed-ns (merge-with - old new))
                    1e+9)})
              old-data new-data))
  )
