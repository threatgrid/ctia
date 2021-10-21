(ns ctia.http.routes.pagination-test
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as cs]
            [clojure.spec.gen.alpha :as csg]
            [clojure.test :refer [deftest testing use-fixtures]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [ctia.auth.capabilities :refer [all-capabilities]]
            [ctia.properties :as p]
            [ctia.entity.entities :as entities]
            [ctia.entity.feed-test :refer [new-feed-maximal]]
            [ctia.schemas.core :refer [Entity]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.field-selection :as field-selection]
            [ctia.test-helpers
             [core :as helpers :refer [url-id assert-post]]
             [http :as http :refer [app->HTTPShowServices]]
             [pagination :as pagination
              :refer [pagination-test
                      pagination-test-no-sort]]
             [store :as store :refer [test-for-each-store-with-app]]]
            [ctim.domain.id :as id]
            [ctim.examples.incidents :refer [incident-minimal]]
            [schema.test :refer [validate-schemas]]
            [schema.core :as s]
            [schema-tools.core :as st]))

(use-fixtures :once
  validate-schemas
  helpers/fixture-allow-all-auth
  whoami-helpers/fixture-server)

(defn establish-user! [app]
  (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (all-capabilities))
  (whoami-helpers/set-whoami-response app
                                      http/api-key
                                      "foouser"
                                      "foogroup"
                                      "user"))

(def headers {"Authorization" http/api-key})

(deftest test-default-sort-next
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (let [nb 1000]
       (assert (= nb
                (count (helpers/POST-entity-bulk app incident-minimal :incidents nb headers)))))
     (pagination/x-next-test app  (str "ctia/incident/search?query=*") headers))))

(deftest ^:slow test-pagination-lists
  ;;generate an observable and many records of all listable entities"
  (test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)

           http-show (p/get-http-show (app->HTTPShowServices app))
           observable {:type "ip"
                       :value "1.2.3.4"}
           title "test"
           new-indicators (->> (csg/sample (cs/gen :new-indicator/map 5))
                               (map #(assoc % :title title))
                               (map #(assoc % :id (url-id :indicator (app->HTTPShowServices app)))))
           created-indicators (map #(assert-post app "ctia/indicator" %)
                                   new-indicators)
           new-judgements (->> (csg/sample (cs/gen :new-judgement/map) 5)
                               (map #(assoc %
                                            :observable observable
                                            :disposition 5
                                            :disposition_name "Unknown"))
                               (map #(assoc % :id (url-id :judgement (app->HTTPShowServices app)))))
           new-sightings (->> (csg/sample (cs/gen :new-sighting/map) 5)
                              (map #(-> (assoc %
                                               :observables [observable])
                                        (dissoc % :relations :data)))
                              (map #(assoc % :id (url-id :sighting (app->HTTPShowServices app)))))
           route-pref (str "ctia/" (:type observable) "/" (:value observable))]

       (testing "setup: create sightings and their relationships with indicators"
         (doseq [new-sighting new-sightings
                 :let [{id :id} (assert-post app "ctia/sighting" new-sighting)
                       sighting-id (id/->id :sighting id http-show)]
                 {id :id} created-indicators
                 :let [indicator-id (id/->id :indicator id http-show)]]
           (assert-post app
                        "ctia/relationship"
                        {:source_ref (id/long-id sighting-id)
                         :relationship_type "indicates"
                         :target_ref (id/long-id indicator-id)})))

       (testing "setup: create judgements and their relationships with indicators"
         (doseq [new-judgement new-judgements
                 :let [{id :id} (assert-post app "ctia/judgement" new-judgement)
                       judgement-id (id/->id :judgement id http-show)]
                 {id :id} created-indicators
                 :let [indicator-id (id/->id :indicator id http-show)]]
           (assert-post app
                        "ctia/relationship"
                        {:source_ref (id/long-id judgement-id)
                         :relationship_type "observable-of"
                         :target_ref (id/long-id indicator-id)})))

       (testing "indicators with query (ES only)"
         (when (= "es" (get-in-config [:ctia :store :indicator]))
           (pagination-test app
                            (str "/ctia/indicator/search?query=" title)
                            headers
                            [:id :title])))

       (testing "sightings by observable"
         (pagination-test app
                          (str route-pref "/sightings")
                          headers
                          [:id
                           :timestamp
                           :confidence
                           :observed_time.start_time]))

       (testing "sightings/indicators by observable"
         (pagination-test-no-sort app
                                  (str route-pref "/sightings/indicators")
                                  headers))

       (testing "judgements by observable"
         (pagination-test app
                          (str route-pref "/judgements")
                          headers
                          [:id
                           :disposition
                           :priority
                           :severity
                           :confidence
                           :valid_time.start_time]))

       (testing "judgements/indicators by observable"
         (pagination-test-no-sort app
                                  (str route-pref "/judgements/indicators")
                                  headers))))))

(s/defn new-maximal-by-entity :- {(s/pred simple-keyword?) (s/pred map?)}
  "Returns a map from entity name to new-*-maximal for those entities that have one."
  []
  (into {}
        (comp 
          (remove (comp #{:identity :event} key))
          (map (fn [[entity {:keys [plural]}]]
                 [entity (case entity
                           :feed new-feed-maximal
                           @(requiring-resolve
                              (symbol (format "ctim.examples.%s/new-%s-maximal"
                                              (name plural)
                                              (name entity)))))])))
        (entities/all-entities)))

(s/defn pagination+field-selection-test-cases
  :- {(s/pred simple-keyword?) (st/assoc Entity
                                         :endpoint s/Str
                                         :new-maximal (s/pred map?)
                                         :snake-plural s/Keyword)}
  []
  {:post [(seq %)]}
  (into {}
        ;; skip these entities for this test
        (comp (remove (some-fn
                        (comp #{:data-table :event :feedback :identity} key)
                        (comp :no-api? val)))
              (map (fn [[entity {:keys [fields plural route-context sort-fields] :as test-case}]]
                     (let [_ (assert (seq fields) (str entity " missing :fields"))
                           _ (assert (seq sort-fields) (str entity " missing :sort-fields"))
                           new-maximal (get (new-maximal-by-entity) entity)
                           _ (assert (map? new-maximal) (str entity " missing maximal"))
                           new-maximal (cond-> new-maximal
                                         (#{:actor :campaign :casebook :feed :incident
                                            :investigation :vulnerability :weakness}
                                           entity)
                                         (assoc :title "foo")

                                         (= :judgement entity)
                                         (assoc :observable {:value "1.2.3.4", :type "ip"}))]
                       [entity (assoc test-case
                                      :endpoint (format "ctia%s/search?query=*"
                                                        ;; includes leading slash
                                                        route-context)
                                      :new-maximal new-maximal
                                      :snake-plural (keyword (str/replace (name plural) \- \_)))]))))
        (entities/all-entities)))

(deftest pagination+field-selection-test
  (store/test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (let [test-cases (-> (pagination+field-selection-test-cases)
                          ;; these entities have non-default search fields
                          (select-keys #{:judgement
                                         :indicator
                                         :asset-properties})
                          sort)
           _ (assert (= 3 (count test-cases))
                     (mapv first test-cases))]
       (doseq [[entity {:keys [fields endpoint new-maximal snake-plural sort-fields]} :as test-case] test-cases]
         ;; progress reporting for slow test
         (println (str "Testing pagination: " entity))
         ;; ensure good coverage via non-default fields
         (assert (some #{:reason :title :asset_ref} fields) [entity fields])
         (assert (some #{:reason :title :asset_ref} sort-fields) [entity sort-fields])
         (testing test-case
           (let [sample-size 30
                 ids (helpers/POST-entity-bulk
                       app
                       new-maximal
                       snake-plural
                       sample-size
                       headers)]
             (field-selection/field-selection-tests
               app
               [endpoint
                (http/doc-id->rel-url (first ids))]
               headers
               fields)

             (pagination/pagination-test
               app
               endpoint
               headers
               sort-fields))))))))

(deftest ^:generative generative-pagination+field-selection-test
  (store/test-for-each-store-with-app
   (fn [app]
     (establish-user! app)
     (checking
       "pagination for any entity"
       {;; :seed ...
        :num-tests 5}
       [;; note: may check same entity multiple times
        [entity {:keys [fields plural endpoint new-maximal snake-plural sort-fields]}]
        (-> (pagination+field-selection-test-cases)
            sort
            gen/elements
            gen/no-shrink)
        sample-size (gen/large-integer* {:min 30 :max 345})]
       ;; progress reporting for slow test
       (println (str "Testing pagination: " entity))
       (let [ids (case entity
                   :feed (mapv #(-> (helpers/POST
                                      app
                                      "/ctia/feed"
                                      :body (dissoc % :id)
                                      :headers headers)
                                    :parsed-body
                                    :id)
                               (repeat sample-size new-maximal))
                   (helpers/POST-entity-bulk
                     app
                     new-maximal
                     snake-plural
                     sample-size
                     headers))
             _ (assert (= sample-size (count ids)) [sample-size (count ids)])
             _ (case entity
                 :sighting (let [sample (dissoc new-maximal :id)
                                 first-sighting (-> sample
                                                    (assoc-in [:observed_time :start_time]
                                                              #inst "2016-01-01T01:01:01.000Z"))
                                 second-sighting (-> sample
                                                     (assoc-in [:observed_time :start_time]
                                                               #inst "2016-01-02T01:01:01.000Z"))
                                 third-sighting (-> sample
                                                    (assoc :timestamp
                                                           #inst "2016-01-03T01:01:01.000Z")
                                                    (assoc-in [:observed_time :start_time]
                                                              #inst "2016-01-02T01:01:01.000Z"))
                                 custom-samples (helpers/POST-bulk
                                                  app
                                                  {:sightings [first-sighting
                                                               second-sighting
                                                               third-sighting]}
                                                  true)])
                 nil)]
         (field-selection/field-selection-tests
           app
           [endpoint
            (http/doc-id->rel-url (first ids))]
           headers
           fields)

         (pagination/pagination-test
           app
           endpoint
           headers
           sort-fields))))))
