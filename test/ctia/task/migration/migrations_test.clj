(ns ctia.task.migration.migrations-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.entity.incident.schemas :refer [StoredIncident]]
            [ctia.task.migration.migrations :as sut]
            [schema.core :as s]))

(deftest add-groups-test
  (is (= (transduce sut/add-groups conj [{}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups []}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups ["foo"]}])
         [{:groups ["foo"]}])))

(deftest fix-end-time-test
  (is (= (transduce sut/fix-end-time conj [{}]) [{}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:start_time "foo"}}])
         [{:valid_time
           {:start_time "foo"}}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time #inst "2535-01-01T00:00:00.000-00:00"}}])
         [{:valid_time
           {:end_time #inst "2525-01-01T00:00:00.000-00:00"}}]))

  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time #inst "2524-01-01T00:00:00.000-00:00"}}])
         [{:valid_time
           {:end_time #inst "2524-01-01T00:00:00.000-00:00"}}])))

(deftest pluralize-target-test
  (is (= (transduce sut/pluralize-target conj
                    [{:type "sighting"
                      :target []}]) [{:type "sighting"
                                      :targets []}])))

(deftest target-observed_time-test
  (is (= (transduce sut/target-observed_time conj
                    [{:type "sighting"
                      :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                      :end_time #inst "2016-02-11T00:40:48.212-00:00"}
                      :target {}}])
         [{:type "sighting"
           :observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                           :end_time #inst "2016-02-11T00:40:48.212-00:00"}
           :target {:observed_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                    :end_time #inst "2016-02-11T00:40:48.212-00:00"}}}])))

(deftest test-zero-4-twenty-height-sighting-targets
  (is (= (transduce (:0.4.28 sut/available-migrations) conj
                    '({:schema_version "0.4.24",
                       :observables [{:value "22.11.22.11", :type "ip"}],
                       :type "sighting",
                       :created "2018-03-15T13:40:33.786Z",
                       :modified "2018-03-15T13:40:33.786Z",
                       :id "sighting-185a01dc-ac0b-4c98-950f-772f85285f63",
                       :count 1,
                       :tlp "green",
                       :target {:type "endpoint.sensor",
                                :observables [{:value "33.231.32.43", :type "ip"}],
                                :os "ubuntu"},
                       :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
                       :timestamp "2018-03-15T02:36:00.632Z",
                       :confidence "Unknown",
                       :observed_time {:start_time "2018-03-15T02:36:00.632Z", :end_time "2018-03-15T02:36:00.632Z"},
                       :observables_hash ["ip:22.11.22.11"],
                       :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}
                      {:schema_version "0.4.24",
                       :observables [{:value "22.11.22.11", :type "ip"}],
                       :type "sighting",
                       :created "2018-03-15T03:26:27.002Z",
                       :modified "2018-03-15T03:26:27.002Z",
                       :id "sighting-130d84b7-0794-4b94-b9aa-279162d17a47",
                       :count 1,
                       :tlp "green",
                       :target {:type "endpoint.sensor",
                                :observables [{:value "33.231.32.43", :type "ip"}],
                                :os "ubuntu"},
                       :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
                       :timestamp "2018-03-15T02:36:00.632Z",
                       :confidence "Unknown",
                       :observed_time {:start_time "2018-03-15T02:36:00.632Z",
                                       :end_time "2018-03-15T02:36:00.632Z"},
                       :observables_hash ["ip:22.11.22.11"],
                       :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}))
         [{:schema_version "0.4.28",
           :observables [{:value "22.11.22.11", :type "ip"}],
           :type "sighting",
           :created "2018-03-15T13:40:33.786Z",
           :modified "2018-03-15T13:40:33.786Z",
           :targets
           [{:type "endpoint.sensor",
             :observables [{:value "33.231.32.43", :type "ip"}],
             :os "ubuntu",
             :observed_time
             {:start_time "2018-03-15T02:36:00.632Z",
              :end_time "2018-03-15T02:36:00.632Z"}}],
           :id "sighting-185a01dc-ac0b-4c98-950f-772f85285f63",
           :count 1,
           :tlp "green",
           :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
           :timestamp "2018-03-15T02:36:00.632Z",
           :confidence "Unknown",
           :observed_time
           {:start_time "2018-03-15T02:36:00.632Z",
            :end_time "2018-03-15T02:36:00.632Z"},
           :observables_hash ["ip:22.11.22.11"],
           :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}
          {:schema_version "0.4.28",
           :observables [{:value "22.11.22.11", :type "ip"}],
           :type "sighting",
           :created "2018-03-15T03:26:27.002Z",
           :modified "2018-03-15T03:26:27.002Z",
           :targets
           [{:type "endpoint.sensor",
             :observables [{:value "33.231.32.43", :type "ip"}],
             :os "ubuntu",
             :observed_time
             {:start_time "2018-03-15T02:36:00.632Z",
              :end_time "2018-03-15T02:36:00.632Z"}}],
           :id "sighting-130d84b7-0794-4b94-b9aa-279162d17a47",
           :count 1,
           :tlp "green",
           :groups ["4ca63e42-4a71-4c85-9a28-06afac0c2273"],
           :timestamp "2018-03-15T02:36:00.632Z",
           :confidence "Unknown",
           :observed_time
           {:start_time "2018-03-15T02:36:00.632Z",
            :end_time "2018-03-15T02:36:00.632Z"},
           :observables_hash ["ip:22.11.22.11"],
           :owner "b3eab96a-ed74-4195-9c2b-f8b3dd447ccd"}])))

(def old-incident
  {:id "http://ex.tld/ctia/incident/incident-e1b8afdf-e3dd-45d9-961c-dd84f37a8587"
   :external_ids ["http://ex.tld/ctia/incident/incident-e1b8afdf-e3dd-45d9-961c-dd84f37a8587"
                  "http://ex.tld/ctia/incident/incident-456"]
   :external_references
   [{:source_name "source"
     :external_id "T1067"
     :url "https://ex.tld/wiki/T1067"
     :hashes ["#section1"]
     :description "Description text"}]
   :type "incident"
   :title "incident"
   :description "description"
   :short_description "short desc"
   :tlp "green"
   :schema_version "0.4.28"
   :revision 1
   :timestamp #inst "2016-02-11T00:40:48.212-00:00"
   :language "language"
   :source "source"
   :source_uri "http://example.com"
   :confidence "High"
   :categories ["Denial of Service"
                "Improper Usage"]
   :valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                :end_time #inst "2525-01-01T00:00:00.000-00:00"}
   :status "Open"
   :incident_time {:first_malicious_action #inst "2016-02-11T00:40:48.212-00:00"
                   :initial_compromise #inst "2016-02-11T00:40:48.212-00:00"
                   :first_data_exfiltration #inst "2016-02-11T00:40:48.212-00:00"
                   :incident_discovery #inst "2016-02-11T00:40:48.212-00:00"
                   :incident_opened #inst "2016-02-11T00:40:48.212-00:00"
                   :containment_achieved #inst "2016-02-11T00:40:48.212-00:00"
                   :restoration_achieved #inst "2016-02-11T00:40:48.212-00:00"
                   :incident_reported #inst "2016-02-11T00:40:48.212-00:00"
                   :incident_closed #inst "2016-02-11T00:40:48.212-00:00"}
   :reporter "reporter"
   :responder "responder"
   :coordinator "coordinator"
   :victim "victim"
   :affected_assets [{:type "thing"
                      :description "description"
                      :ownership_class "Partner-Owned"
                      :management_class "CO-Management"
                      :location_class "Mobile"
                      :property_affected {:property "Non-Repudiation"
                                          :description_of_effect "foo"
                                          :type_of_availability_loss "foo"
                                          :duration_of_availability_loss "Seconds"
                                          :non_public_data_compromised {:security_compromise "Yes"
                                                                        :data_encrypted false}}
                      :identifying_observables [{:type "domain" :value "foo.com"}]}]
   :impact_assessment {:direct_impact_summary {:asset_losses "Moderate"
                                               :business_mission_distruption "Major"
                                               :response_and_recovery_costs "Minor"}
                       :indirect_impact_summary {:loss_of_competitive_advantage "Yes"
                                                 :brand_and_market_damage "No"
                                                 :increased_operating_costs "Yes"
                                                 :local_and_regulatory_costs "Yes"}
                       :total_loss_estimation {:initial_reported_total_loss_estimation {:amount 100
                                                                                        :iso_currency_code "foo"}
                                               :actual_total_loss_estimation {:amount 100
                                                                              :iso_currency_code "foo"}}
                       :impact_qualification "Painful"
                       :effects ["Data Breach or Compromise"]}
   :security_compromise "Yes"
   :discovery_method "Log Review"
   :contact "contact"
   :history [{:action_entry [{:COA "http://example.com/ctia/coa/coa-2e4eb53f-cfa3-4957-86ab-6eaf02fd0587"
                              :time #inst "2016-02-11T00:40:48.212-00:00"
                              :contributors [{:role "role"
                                              :name "name"
                                              :email "email"
                                              :phone "phone"
                                              :organization "org"
                                              :date #inst "2016-02-11T00:40:48.212-00:00"
                                              :contribution_location "location"}]}]
              :journal_entry "history"}]
   :intended_effect "Extortion"
   :related_indicators [{:confidence "High"
                         :source "source"
                         :relationship "related-to"
                         :indicator_id "http://example.com/ctia/indicator/indicator-6e279a0d-6788-4cdf-957f-4e4b73823d6c"}]
   :related_incidents [{:confidence "High"
                        :source "source"
                        :relationship "related-to"
                        :incident_id "http://example.com/ctia/incident/incident-6e279a0d-6788-4cdf-957f-4e4b73823d6f"}]
   :related_observables [{:type "domain" :value "example.com"}]
   :attributed_actors [{:confidence "High"
                        :source "source"
                        :relationship "attributed-to"
                        :actor_id "http://example.com/ctia/actor/actor-6e279a0d-6788-4cdf-957f-4e4b73823d6d"}]
   :COA_taken [{:COA "http://example.com/ctia/coa/coa-6e279a0d-6788-4cdf-957f-4e4b73823f6d"
                :time #inst "2016-02-11T00:40:48.212-00:00"
                :contributors [{:role "role"
                                :name "name"
                                :email "name@foo.com"
                                :phone "+33 82222"
                                :organization "org"
                                :date #inst "2016-02-11T00:40:48.212-00:00"
                                :contribution_location "location"}]}]
   :COA_requested [{:COA "http://example.com/ctia/coa/coa-6e279a0d-6788-4ddf-957f-4e4b73823f6d"
                    :time #inst "2016-02-11T00:40:48.212-00:00"
                    :contributors [{:role "role"
                                    :name "name"
                                    :email "name@foo.com"
                                    :phone "+33 82222"
                                    :organization "org"
                                    :date #inst "2016-02-11T00:40:48.212-00:00"
                                    :contribution_location "location"}]}]
   :owner "foouser"
   :groups ["bar"]
   :created #inst "2016-02-11T00:40:48.212-00:00"
   :modified #inst "2016-02-11T00:40:48.212-00:00"})

(deftest simplify-incident-model-test
  (is (nil? (->> (transduce sut/simplify-incident
                            conj
                            [old-incident])
                 first
                 (s/check StoredIncident)))))

(deftest rename-observable-type-test
  (let [sighting {:type "sighting"
                  :observables [{:type "domain" :value "example.com"}
                                {:type "pki-serial" :value "12345"}]
                  :relations [{:source {:type "pki-serial" :value "12345"}
                               :related {:type "pki-serial" :value "12345"}}]}
        judgement {:type "judgement"
                   :observable {:type "pki-serial" :value "12345"}}
        verdict {:type "verdict"
                 :observable {:type "pki-serial" :value "12345"}}
        casebook {:type "casebook"
                  :observables (:observables sighting)
                  :bundle {:type "bundle"
                           :sightings [sighting]
                           :judgements [judgement]
                           :verdicts [verdict]}}]
    (is (= [{:type "sighting",
            :observables
            [{:type "domain", :value "example.com"}
             {:type "pki_serial", :value "12345"}],
            :relations
            [{:source {:type "pki_serial", :value "12345"},
              :related {:type "pki_serial", :value "12345"}}]}
           {:type "judgement",
            :observable {:type "pki_serial", :value "12345"}}
           {:type "verdict",
            :observable {:type "pki_serial", :value "12345"}}
           {:type "casebook",
            :observables
            [{:type "domain", :value "example.com"}
             {:type "pki_serial", :value "12345"}],
            :bundle
            {:type "bundle",
             :sightings
             [{:type "sighting",
               :observables
               [{:type "domain", :value "example.com"}
                {:type "pki_serial", :value "12345"}],
               :relations
               [{:source {:type "pki_serial", :value "12345"},
                 :related {:type "pki_serial", :value "12345"}}]}],
             :judgements
             [{:type "judgement",
               :observable {:type "pki_serial", :value "12345"}}],
             :verdicts
             [{:type "verdict",
               :observable {:type "pki_serial", :value "12345"}}]}}]
           (transduce (sut/rename-observable-type "pki-serial" "pki_serial")
                      conj
                      [sighting
                       judgement
                       verdict
                       casebook])))))

