(ns ctia.bulk.routes-test
  (:require [cheshire.core :refer [parse-string]]
            [ductile.index :as es-index]
            [clj-momo.test-helpers
             [core :as mth]
             [http :refer [encode]]]
            [clojure.java.io :as io]
            [clojure
             [string :as str]
             [test :refer [deftest is join-fixtures testing use-fixtures]]]
            [clj-http.fake :refer [with-global-fake-routes]]
            [ctia.properties :refer [get-http-show]]
            [ctia.bulk.core
             :refer
             [bulk-size gen-bulk-from-fn get-bulk-max-size]]
            [ctia.test-helpers
             [es :as es-helpers]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [GET POST DELETE]]
             [fake-whoami-service :as whoami-helpers]
             [http :refer [app->HTTPShowServices]]
             [store :refer [test-for-each-store-with-app
                            test-selected-stores-with-app]]]
            [ctim.domain.id :as id]
            [ctim.examples.incidents :refer [new-incident-maximal]]))

(defn fixture-properties:small-max-bulk-size [t]
  ;; Note: These properties may be overwritten by ENV variables
  (helpers/with-properties ["ctia.http.bulk.max-size" 100]
    (t)))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    fixture-properties:small-max-bulk-size
                                    whoami-helpers/fixture-server]))

(defn mk-new-actor [n]
  {:id (str "transient:actor-" n)
   :title (str "actor-" n)
   :description (str "description: actor-" n)
   :short_description (str "short_description: actor-" n)
   :actor_type "Hacker"
   :source "a source"
   :confidence "High"
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-attack-pattern [n]
  {:id (str "transient:attack-pattern-" n)
   :title (str "attack-pattern-" n)
   :description (str "description: attack-pattern-" n)
   :short_description (str "short_description: attack-pattern-" n)})

(defn mk-new-campaign [n]
  {:id (str "transient:campaign-" n)
   :title (str "campaign" n)
   :description "description"
   :short_description "short_description"
   :campaign_type "anything goes here"
   :intended_effect ["Theft"]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-coa [n]
  {:id (str "transient:coa-" n)
   :title (str "coa-" n)
   :description (str "description: coa-" n)
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-data-table [n]
  {:id (str "transient:data-table-" n)
   :description (str "description: datatable-" n)
   :row_count 1
   :columns [{:name "Column1"
              :type "string"}
             {:name "Column2"
              :type "string"}]
   :rows [["foo"] ["bar"]]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-feedback [n]
  {:id (str "transient:feedback-" n)
   :entity_id (str "transient:foo-" n)
   :feedback -1
   :reason "false positive"})

(defn mk-new-incident [n]
  (-> new-incident-maximal
      (dissoc :id :schema_version :tlp :type)
      (into {:id (str "transient:incident-" n)
             :title (str "incident-" n)
             :description (str "description: incident-" n)})))

(defn mk-new-indicator [n]
  {:id (str "transient:indicator-" n)
   :title (str "indicator-" n)
   :description (str "description: indicator-" n)
   :producer "producer"
   :indicator_type ["C2" "IP Watchlist"]
   :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-judgement [n]
  {:id (str "transient:judgement-" n)
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}
   :observable {:value (str "10.0.0." n)
                :type "ip"}
   :disposition 2
   :source "test"
   :priority 100
   :severity "High"
   :confidence "Low"})

(defn mk-new-malware [n]
  {:id (str "transient:malware-" n)
   :title (str "malware-" n)
   :description (str "description: malware-" n)
   :short_description (str "short_description: malware-" n)
   :labels [(str "malware-label-" n)]})

(defn mk-new-relationship [n source_ref target_ref]
  {:id (str "transient:relationship-" n)
   :title (str "title" n)
   :description (str "description-" n)
   :short_description "short desc"
   :revision 1
   :external_ids ["foo" "bar"]
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :language "language"
   :source "source"
   :source_uri "transient:0"
   :relationship_type "targets"
   :source_ref source_ref
   :target_ref target_ref})

(defn mk-new-sighting [n]
  {:id (str "transient:sighting-" n)
   :description (str "description: sighting-" n)
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :observed_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"}
   :count 1
   :source "source"
   :sensor "endpoint.sensor"
   :confidence "High"})

(defn mk-new-identity-assertion [n]
  {:id (str "transient:identity-aasertion-" n)
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :valid_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"}
   :identity {:observables [{:type "ip" :value "100.213.110.122"}]}
   :assertions [{:name "cisco:ctr:device:owner" :value "Bob"}]
   :source "source"})

(defn mk-new-tool [n]
  {:id (str "transient:tool-" n)
   :title (str "tool-" n)
   :description (str "description: tool-" n)
   :short_description (str "short_description: tool-" n)
   :labels [(str "tool-label-" n)]})

(defn mk-new-vulnerability [n]
  {:id (str "transient:vulnerability" n)
   :description "Improper Neutralization of Directives"})

(deftest testing-gen-bulk-from-fn
  (let [new-bulk {:actors (map mk-new-actor (range 6))
                  :campaigns (map mk-new-campaign (range 6))}]
    (testing "testing gen-bulk-from-fn with 2 args"
      (is (= (gen-bulk-from-fn (fn [lst _] (map (fn [_] :s) lst))
                               new-bulk)
             {:actors [:s :s :s :s :s :s]
              :campaigns [:s :s :s :s :s :s]})))
    (testing "testing gen-bulk-from-fn with 3 args"
      (is (= (gen-bulk-from-fn (fn [lst _ x] (map (fn [_] x) lst))
                               new-bulk
                               :x)
             {:actors [:x :x :x :x :x :x]
              :campaigns [:x :x :x :x :x :x]})))))

(def tst-bulk {:actors (map #(str "actor-" %) (range 6))
               :campaigns (map #(str "campaign-" %) (range 6))})

(defn make-get-query-str-from-bulkrefs
  "Given a BulkRefs returns the string of query-params"
  [bulk-ids]
  (str/join "&"
            (map
             (fn [type]
               (str/join "&"
                         (map (fn [id]
                                (let [id (if (vector? id) (last id) id)
                                      short-id (:short-id (id/long-id->id id))]
                                  (str (encode (name type)) "=" (encode short-id))))
                              (get bulk-ids type))))
             (keys bulk-ids))))


(deftest test-bulk-wait_for-test
  (test-selected-stores-with-app
   #{:es-store}
   (fn [app]
     (testing "POST /ctia/bulk with wait_for"
       (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)

             _ (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
             _ (whoami-helpers/set-whoami-response app
                                                   "45c1f5e3f05d0"
                                                   "foouser"
                                                   "foogroup"
                                                   "user")

             default-es-refresh (->> (get-in-config
                                       [:ctia :store :es :default :refresh])
                                     (str "refresh="))
             es-params (atom nil)
             fake-routes
             {#".*_bulk.*"
              {:post (fn [{:keys [query-string body]}]
                       (let [mapping-type (-> (io/reader body)
                                              line-seq
                                              first
                                              (parse-string true)
                                              (get-in [:index :_type]))]
                         (when-not (= "event" mapping-type)
                           (reset! es-params query-string))
                         {:status 200
                          :headers {"Content-Type" "application/json"}
                          :body "{}"}))}}
             check-refresh (fn [wait_for msg]
                             (let [nb 2
                                   indicators (map mk-new-indicator (range nb))
                                   judgements (map mk-new-judgement (range nb))
                                   new-bulk {:indicators indicators
                                             :judgements judgements
                                             :relationships (map #(mk-new-relationship %
                                                                                       (-> indicators (nth %) :id)
                                                                                       (-> judgements (nth %) :id))
                                                                 (range nb))}
                                   expected (cond
                                              (nil? wait_for) default-es-refresh
                                              (true? wait_for) "refresh=wait_for"
                                              (false? wait_for) "refresh=false")
                                   path (cond-> "ctia/bulk"
                                          (boolean? wait_for) (str "?wait_for=" wait_for))]

                               (with-global-fake-routes fake-routes
                                 (POST app
                                       path
                                       :body new-bulk
                                       :headers {"Authorization" "45c1f5e3f05d0"}))

                               (is (some-> @es-params
                                           (str/includes? expected))
                                   msg)
                               (reset! es-params nil)))]
         (check-refresh true "Bulk import should wait for index refresh when wait_for is true")
         (check-refresh false "Bulk imports should not wait for index refresh when wait_for is false")
         (check-refresh nil "Configured ctia.store.bundle-refresh value is applied when wait_for is not specified"))))))

(deftest test-bulk-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (testing "POST /ctia/bulk"
       (let [nb 7
             indicators (map mk-new-indicator (range nb))
             judgements (map mk-new-judgement (range nb))
             new-bulk {:actors (map mk-new-actor (range nb))
                       :feedbacks (map mk-new-feedback (range nb))
                       :indicators indicators
                       :judgements judgements
                       :malwares (map mk-new-malware (range nb))
                       :relationships (map #(mk-new-relationship %
                                                                 (-> indicators (nth %) :id)
                                                                 (-> judgements (nth %) :id))
                                           (range nb))
                       :sightings (map mk-new-sighting (range nb))
                       :tools (map mk-new-tool (range nb))}
             response (POST app
                            "ctia/bulk"
                            :body new-bulk
                            :headers {"Authorization" "45c1f5e3f05d0"})
             bulk-ids (:parsed-body response)
             show-props (get-http-show (app->HTTPShowServices app))]
         (is (= 201 (:status response)))

         (doseq [type (keys new-bulk)]
           (testing (str "number of created " (name type))
             (is (= (count (get new-bulk type))
                    (count (get bulk-ids type))))))

         (testing "GET /ctia/bulk"
           (let [{status :status
                  response :parsed-body}
                 (GET app
                      (str "ctia/bulk?"
                           (make-get-query-str-from-bulkrefs bulk-ids))
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (is (= 200 status))

             (doseq [k (keys new-bulk)]
               (testing (str "retrieved " (name k))
                 (is (= (map #(dissoc % :id :timestamp :source_ref :target_ref)
                             (get new-bulk k))
                        (map #(dissoc % :id :timestamp :type :tlp :schema_version :disposition_name :source_ref :target_ref :owner :groups)
                             (get response k))))

                 (let [id (id/long-id->id (:id (first (get response k))))]
                   (is (= (:hostname id)         (:hostname show-props)))
                   (is (= (:protocol id)         (:protocol show-props)))
                   (is (= (:port id)             (:port show-props)))
                   (is (= (:path-prefix id) (seq (:path-prefix show-props)))))))))

         (testing "DELETE /ctia/bulk"
           ;; edge cases are tested in bulk/core-test
           (let [deleted-bulk-ids (into {}
                                        (map (fn [[k ids]]
                                               {k (take 2 ids)}))
                                        (dissoc bulk-ids :tempids))
                 expected-delete-res (into {}
                                        (map (fn [[k ids]]
                                               {k {:deleted ids}}))
                                        deleted-bulk-ids)
                 {status :status delete-res :parsed-body :as res}
                 (DELETE app
                         (str "ctia/bulk?"
                              (make-get-query-str-from-bulkrefs deleted-bulk-ids))
                         :headers {"Authorization" "45c1f5e3f05d0"})
                 {get-res :parsed-body}
                 (GET app
                      (str "ctia/bulk?"
                           (make-get-query-str-from-bulkrefs bulk-ids))
                      :headers {"Authorization" "45c1f5e3f05d0"})]
             (clojure.pprint/pprint res)
             (is (= 200 status))
             (doseq [[k res] delete-res]
               (is (= (-> (get expected-delete-res k)
                          :deleted
                          set)
                      (set (:deleted res)))))
             (doseq [[_k entity-res] (dissoc get-res :tempids)]
               (is (= [nil nil] (take 2 entity-res))
                   "the deleted entities must not be found")))))))))

(deftest get-bulk-max-size-test
  (let [nb 10
        indicators (map mk-new-indicator (range nb))
        judgements (map mk-new-judgement (range nb))
        new-bulk {:actors (map mk-new-actor (range nb))
                  :attack_patterns (map mk-new-attack-pattern (range nb))
                  :campaigns (map mk-new-campaign (range nb))
                  :coas (map mk-new-coa (range nb))
                  :data_tables (map mk-new-data-table (range nb))
                  :feedbacks (map mk-new-feedback (range nb))
                  :incidents (map mk-new-incident (range nb))
                  :indicators indicators
                  :judgements judgements
                  :malwares (map mk-new-malware (range nb))
                  :relationships (map #(mk-new-relationship %
                                                            (-> indicators (nth %) :id)
                                                            (-> judgements (nth %) :id))
                                      (range nb))
                  :sightings (map mk-new-sighting (range nb))
                  :tools (map mk-new-tool (range nb))}]
    (is (= (bulk-size new-bulk)
           (* nb (count new-bulk))))))

(deftest bulk-max-size-post-test
  (test-for-each-store-with-app
   (fn [app]
    (let [{:keys [get-in-config]} (helpers/get-service-map app :ConfigService)]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     ;; Check changing the properties change the computed bulk max size
     (is (= 100 (get-bulk-max-size get-in-config)))
     (let [nb 7
           indicators (map mk-new-indicator (range nb))
           judgements (map mk-new-judgement (range nb))
           new-ok-bulk {:actors (map mk-new-actor (range nb))
                        :attack_patterns (map mk-new-attack-pattern (range nb))
                        :campaigns (map mk-new-campaign (range nb))
                        :coas (map mk-new-coa (range nb))
                        :data_tables (map mk-new-data-table (range nb))
                        :feedbacks (map mk-new-feedback (range nb))
                        :incidents (map mk-new-incident (range nb))
                        :indicators indicators
                        :judgements judgements
                        :malwares (map mk-new-malware (range nb))
                        :relationships (map #(mk-new-relationship
                                              %
                                              (-> indicators (nth %) :id)
                                              (-> judgements (nth %) :id))
                                            (range nb))
                        :sightings (map mk-new-sighting (range nb))
                        :tools (map mk-new-tool (range nb))}
           incidents (map mk-new-incident (range nb))
           sightings (map mk-new-sighting (range nb))
           new-too-big-bulk {:actors (map mk-new-actor (range (+ nb 5 7)))
                             :attack_patterns (map mk-new-attack-pattern (range nb))
                             :campaigns (map mk-new-campaign (range nb))
                             :coas (map mk-new-coa (range nb))
                             :data_tables (map mk-new-data-table (range nb))
                             :feedbacks (map mk-new-feedback (range nb))
                             :incidents incidents
                             :indicators (map mk-new-indicator (range nb))
                             :judgements (map mk-new-judgement (range nb))
                             :malwares (map mk-new-malware (range nb))
                             :relationships (map #(mk-new-relationship
                                                   %
                                                   (-> incidents (nth %) :id)
                                                   (-> sightings (nth %) :id))
                                                 (range nb))
                             :sightings sightings
                             :tools (map mk-new-tool (range nb))}
           {status-ok :status
            response :body
            response-ok :parsed-body} (POST app
                                            "ctia/bulk"
                                            :body new-ok-bulk
                                            :headers {"Authorization" "45c1f5e3f05d0"})
           {status-too-big :status
            response-too-big :parsed-body} (POST app
                                                 "ctia/bulk"
                                                 :body new-too-big-bulk
                                                 :headers {"Authorization" "45c1f5e3f05d0"})]
       (testing "POST of right size bulk are accepted"
         (is (empty? (:errors response-ok)) "No errors")
         (is (= 201 status-ok)))
       (testing "POST of too big bulks are rejected"
         (is (empty? (:errors response-too-big)) "No errors")
         (is (= 400 status-too-big))))))))

(defn get-entity
  "Finds an entity in a collection by its ID"
  [entities id]
  (some->> entities
           (filter #(= id (:id %)))
           first))

(deftest bulk-with-transient-ids
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [[tool1 tool2 :as tools] (->> (map mk-new-tool (range 2))
                                        (map #(assoc % :id (id/make-transient-id nil))))
           relationship (assoc (mk-new-relationship 1 (:id tool2) (:id tool1))
                               :id (id/make-transient-id nil))
           ;; Submit all entities to create
           {status-create :status
            bulk-ids :parsed-body} (POST app
                                         "ctia/bulk"
                                         :body {:tools tools
                                                :relationships [relationship]}
                                         :headers {"Authorization" "45c1f5e3f05d0"})
           ;; Retrieve all entities that have been created
           {status-get :status
            {:keys [relationships tools]} :parsed-body}
           (GET app
                (str "ctia/bulk?"
                     (make-get-query-str-from-bulkrefs (dissoc bulk-ids :tempids)))
                :headers {"Authorization" "45c1f5e3f05d0"})
           {:keys [target_ref source_ref]} (first relationships)
           stored-tool-1 (get-entity tools target_ref)
           stored-tool-2 (get-entity tools source_ref)]
       (is (= 201 status-create) "The bulk create should be successfull")
       (is (= 200 status-get) "All entities should be retrieved")
       (is (= (:title tool1) (:title stored-tool-1))
           "The target ref should be the ID of the stored entity")
       (is (= (:title tool2) (:title stored-tool-2))
           "The source ref should be the ID of the stored entity")
       (is (= (hash-map (:id tool1) (:id stored-tool-1)
                        (:id tool2) (:id stored-tool-2)
                        (:id relationship) (:id (first relationships)))
              (:tempids bulk-ids))
           (str "The :tempid field should contain the mapping between all "
                "transient and real IDs"))))))

(deftest bulk-spec-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [vulnerability (assoc-in (mk-new-vulnerability 1)
                                   [:impact :cvss_v2]
                                   {:base_severity "Low"
                                    :base_score 1
                                    :vector_string "CLEARLY INVALID STRING"})
           {status-create :status
            bulk-ids :parsed-body}
           (POST app
                 "ctia/bulk"
                 :body {:vulnerabilities [vulnerability]}
                 :headers {"Authorization" "45c1f5e3f05d0"})]

       (is (= 201 status-create) "The bulk create status should be 201")
       (is (= {:vulnerabilities
               '({:msg "\"CLEARLY INVALID STRING\" - failed: cvss-v2-vector-string? in: [:impact :cvss_v2 :vector_string] at: [:impact :cvss_v2 :vector_string] spec: :new-vulnerability.impact.cvss_v2/vector_string\n",
                  :error "Entity validation Error",
                  :type :spec-validation-error,
                  :entity
                  {:description "Improper Neutralization of Directives",
                   :id "transient:vulnerability1",
                   :impact
                   {:cvss_v2
                    {:base_severity "Low",
                     :base_score 1,
                     :vector_string "CLEARLY INVALID STRING"}}}})} bulk-ids)
           "The bulk create status should report errors")))))

(deftest bulk-error-test
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [{:keys [get-store]} (helpers/get-service-map app :StoreService)

           {:keys [index conn]} (-> (get-store :tool) :state)
       ;; close tool index to produce ES errors on that store
           _ (es-index/close! conn index)
           tools (->> [(mk-new-tool 1)
                       (mk-new-tool 2)]
                      (map #(assoc % :id (id/make-transient-id nil))))
           sighting (assoc (mk-new-sighting 1)
                           :id
                           (id/make-transient-id nil))
           vulnerability (assoc-in (mk-new-vulnerability 1)
                                   [:impact :cvss_v2]
                                   {:base_severity "Low"
                                    :base_score 1
                                    :vector_string "CLEARLY INVALID STRING"})
           ;; Submit all entities to create
           {status-create :status
            bulk-ids :parsed-body}
           (POST app
                 "ctia/bulk"
                 :body {:tools tools
                        :sightings [sighting]}
                 :headers {"Authorization" "45c1f5e3f05d0"})
           ;; Retrieve all entities that have been created
           query-string (make-get-query-str-from-bulkrefs
                         (dissoc bulk-ids :tempids :tools :vulnerabilities))
           {status-get :status
            {:keys [sightings] :as body} :parsed-body}
           (GET app
                (str "ctia/bulk?"
                     query-string)
                :headers {"Authorization" "45c1f5e3f05d0"})]
       (is (= 201 status-create) "The bulk create should be successfull")
       (is (= 200 status-get) "All valid entities should be retrieved")
       (is (not (empty? (:tools bulk-ids))))
       (is (every? #(contains? % :error) (:tools bulk-ids)))
       (is (every? #(contains? % :error) (:vulnerabilities bulk-ids)))
       (is (= (:description sighting)
              (:description (first sightings))))
       ;; reopen index to enable cleaning
       (es-index/open! conn index)))))
