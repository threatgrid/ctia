(ns ctia.http.routes.bulk-test
  (:refer-clojure :exclude [get])
  (:require [clojure
             [string :as str]
             [test :refer [deftest is join-fixtures testing use-fixtures]]]
            [ctia.auth :refer [all-capabilities]]
            [ctia.http.routes.bulk :refer [bulk-size gen-bulk-from-fn get-bulk-max-size]]
            [ctia.lib.url :refer [encode]]
            [ctia.test-helpers
             [core :as helpers :refer [get post]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))


(defn fixture-properties:small-max-bulk-size [test]
  ;; Note: These properties may be overwritten by ENV variables
  (helpers/with-properties ["ctia.http.bulk.max-size" 100]
    (test)))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    fixture-properties:small-max-bulk-size
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(defn mk-new-actor [n]
  {:title (str "actor-" n)
   :description (str "description: actor-" n)
   :actor_type "Hacker"
   :source "a source"
   :confidence "High"
   :associated_actors [{:actor_id "actor-123"}
                       {:actor_id "actor-456"}]
   :associated_campaigns [{:campaign_id "campaign-444"}
                          {:campaign_id "campaign-555"}]
   :observed_TTPs [{:ttp_id "ttp-333"}
                   {:ttp_id "ttp-999"}]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-campaign [n]
  {:title (str "campaign" n)
   :description "description"
   :campaign_type "anything goes here"
   :intended_effect ["Theft"]
   :indicators [{:indicator_id "indicator-foo"}
                {:indicator_id "indicator-bar"}]
   :attribution [{:confidence "High"
                  :source "source"
                  :relationship "relationship"
                  :actor_id "actor-123"}]
   :related_incidents [{:confidence "High"
                        :source "source"
                        :relationship "relationship"
                        :incident_id "incident-222"}]
   :related_TTPs [{:confidence "High"
                   :source "source"
                   :relationship "relationship"
                   :ttp_id "ttp-999"}]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-coa [n]
  {:title (str "coa-" n)
   :description (str "description: coa-" n)
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-exploi-target [n]
  {:title (str "exploi-target-" n)
   :description (str "description: exploi-target-" n)
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-feedback [n]
  {:entity_id (str "judgement-" n)
   :feedback -1
   :reason "false positive"})

(defn mk-new-incident [n]
  {:title (str "incident-" n)
   :description (str "description: incident-" n)
   :confidence "Low"
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}})

(defn mk-new-indicator [n]
  {:title (str "indicator-" n)
   :description (str "description: indicator-" n)
   :producer "producer"
   :indicator_type ["C2" "IP Watchlist"]
   :valid_time {:start_time #inst "2016-05-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}
   :judgements [{:judgement_id "judgement-1234"}
                {:judgement_id "judgement-5678"}]})

(defn mk-new-judgement [n]
  {:valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}
   :observable {:value (str "10.0.0." n)
                :type "ip"}
   :disposition 2
   :source "test"
   :priority 100
   :severity 100
   :confidence "Low"
   :indicators [{:confidence "High"
                 :source "source"
                 :relationship "relationship"
                 :indicator_id "indicator-123"}]})

(defn mk-new-sighting [n]
  {:description (str "description: sighting-" n)
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :observed_time {:start_time #inst "2016-02-01T00:00:00.000-00:00"}
   :count 1
   :source "source"
   :sensor "endpoint.sensor"
   :confidence "High"
   :indicators [{:indicator_id "indicator-22334455"}]})

(defn mk-new-ttp [n]
  {:title (str "ttp-" n)
   :description (str "description: ttp-" n)
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2016-07-11T00:40:48.212-00:00"}
   :ttp_type "foo"
   :indicators [{:indicator_id "indicator-1"}
                {:indicator_id "indicator-2"}]
   :exploit_targets [{:exploit_target_id "exploit-target-123"}
                     {:exploit_target_id "exploit-target-234"}]})

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

(def tst-bulk{:actors (map #(str "actor-" %) (range 6))
              :campaigns (map #(str "campaign-" %) (range 6))})

(defn make-get-query-str-from-bulkrefs
  "Given a BulkRefs returns the string of query-params"
  [bulk-ids]
  (str/join "&"
            (map
             (fn [type]
               (str/join "&"
                         (map (fn [id] (str (encode (name type)) "=" (encode id)))
                              (get-in bulk-ids [type]))))
             (keys bulk-ids))))

(deftest-for-each-store test-bulk-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (testing "POST /ctia/bulk"
    (let [nb 10
          new-bulk {:actors (map mk-new-actor (range nb))
                    :campaigns (map mk-new-campaign (range nb))
                    :coas (map mk-new-coa (range nb))
                    :exploit-targets (map mk-new-exploi-target (range nb))
                    :feedbacks (map mk-new-feedback (range nb))
                    :incidents (map mk-new-incident (range nb))
                    :indicators (map mk-new-indicator (range nb))
                    :judgements (map mk-new-judgement (range nb))
                    :sightings (map mk-new-sighting (range nb))
                    :ttps (map mk-new-ttp (range nb))
                    }
          response (post "ctia/bulk"
                         :body new-bulk
                         :headers {"api_key" "45c1f5e3f05d0"})
          bulk-ids (:parsed-body response)]
      (is (= 201 (:status response)))
      (doseq [type (keys new-bulk)]
        (testing (str "number of created " (name type))
          (is (= (count (get-in new-bulk [type]))
                 (count (get-in bulk-ids [type]))))))
      (testing "GET /ctia/bulk"
        (let [resp (get (str "ctia/bulk?"
                             (make-get-query-str-from-bulkrefs bulk-ids))
                        :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 200 (:status resp)))
          (doseq [k (keys new-bulk)]
            (testing (str "retrieved " (name k))
              (is (= (get-in new-bulk [k])
                     (map #(dissoc % :created :id :type :modified :owner :tlp :schema_version :disposition_name)
                          (get-in (:parsed-body resp) [k])))))))))))

(deftest get-bulk-max-size-test
  (let [nb 10
        new-bulk {:actors (map mk-new-actor (range nb))
                  :campaigns (map mk-new-campaign (range nb))
                  :coas (map mk-new-coa (range nb))
                  :exploit-targets (map mk-new-exploi-target (range nb))
                  :feedbacks (map mk-new-feedback (range nb))
                  :incidents (map mk-new-incident (range nb))
                  :indicators (map mk-new-indicator (range nb))
                  :judgements (map mk-new-judgement (range nb))
                  :sightings (map mk-new-sighting (range nb))
                  :ttps (map mk-new-ttp (range nb))}]
    (is (= (bulk-size new-bulk)
           (* nb 10)))))

(deftest-for-each-store bulk-max-size-post-test
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  ;; Check changing the properties change the computed bulk max size
  (is (= 100 (get-bulk-max-size)))
  (let [nb 10
        new-ok-bulk {:actors (map mk-new-actor (range nb))
                     :campaigns (map mk-new-campaign (range nb))
                     :coas (map mk-new-coa (range nb))
                     :exploit-targets (map mk-new-exploi-target (range nb))
                     :feedbacks (map mk-new-feedback (range nb))
                     :incidents (map mk-new-incident (range nb))
                     :indicators (map mk-new-indicator (range nb))
                     :judgements (map mk-new-judgement (range nb))
                     :sightings (map mk-new-sighting (range nb))
                     :ttps (map mk-new-ttp (range nb))}
        new-too-big-bulk {:actors (map mk-new-actor (range (+ nb 1)))
                          :campaigns (map mk-new-campaign (range nb))
                          :coas (map mk-new-coa (range nb))
                          :exploit-targets (map mk-new-exploi-target (range nb))
                          :feedbacks (map mk-new-feedback (range nb))
                          :incidents (map mk-new-incident (range nb))
                          :indicators (map mk-new-indicator (range nb))
                          :judgements (map mk-new-judgement (range nb))
                          :sightings (map mk-new-sighting (range nb))
                          :ttps (map mk-new-ttp (range nb))}
        {status-ok :status
         response-ok :parsed-body} (post "ctia/bulk"
                                         :body new-ok-bulk
                                         :headers {"api_key" "45c1f5e3f05d0"})
        {status-too-big :status
         response-too-big :parsed-body} (post "ctia/bulk"
                                             :body new-too-big-bulk
                                             :headers {"api_key" "45c1f5e3f05d0"})]
    (testing "POST of right size bulk are accepted"
      (is (empty? (:errors response-ok)) "No errors")
      (is (= 201 status-ok)))
    (testing "POST of too big bulks are rejected"
      (is (empty? (:errors response-too-big)) "No errors")
      (is (= 400 status-too-big)))))
