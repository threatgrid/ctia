(ns ctia.entity.feed-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clj-http.client :as client]
            [ctim.schemas.common :as c]
            [clojure.test :refer [deftest testing is join-fixtures use-fixtures]]
            [ctia.entity.feed
             :refer [sort-restricted-feed-fields]]
            [ctia.test-helpers
             [access-control :refer [access-control-test]]
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-entity-bulk]]
             [crud :refer [entity-crud-test]]
             [fake-whoami-service :as whoami-helpers]
             [field-selection :refer [field-selection-tests]]
             [http :refer [doc-id->rel-url]]
             [pagination :refer [pagination-test]]
             [store :refer [test-for-each-store]]]))

(def new-feed-maximal
  {:revision 0
   :schema_version c/ctim-schema-version
   :type "feed"
   :tlp "green"
   :timestamp #inst "2016-05-11T00:40:48.212-00:00"
   :external_references [{:source_name "source"
                          :external_id "T1067"
                          :url "https://ex.tld/wiki/T1067"
                          :hashes ["#section1"]
                          :description "Description text"}]
   :external_ids ["https://ex.tld/ctia/feed/feed-345"]
   :indicator_id "https://ex.tld/ctia/indicator/indicator-345"
   :language "en"
   :title "title"
   :feed_type "indicator"
   :output :observables
   :lifetime {:start_time #inst "2016-01-01T01:01:01.000Z"
              :end_time #inst "2042-01-01T01:01:01.000Z"}})

(def new-feed-minimal
  {:title "title"
   :feed_type "indicator"
   :output :judgements})

(def blocklist-bundle
  {:type "bundle",
   :source "Feed Indicator with COA Example",
   :source_uri
   "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
   :indicators
   [{:description
     "A lookup table for IPs (IPv4 and IPv6) that are considered suspicious by security analysts",
     :tags ["Suspicious IPs"],
     :valid_time
     {:start_time "2019-05-03T21:48:25.801Z",
      :end_time "2020-06-03T21:48:25.801Z"},
     :producer "Talos",
     :schema_version "1.0.11",
     :type "indicator",
     :source "Feed Indicator with COA Example",
     :external_ids
     ["esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e"],
     :short_description
     "Custom Suspicious IP Watchlist",
     :title "Custom Suspicious IP Watchlist",
     :indicator_type ["IP Watchlist"],
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :id
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
     :severity "High",
     :tlp "amber",
     :confidence "High"}
    {:description
     "A lookup table for IPs (IPv4 and IPv6) that are considered malicious by security analysts",
     :tags ["Malicious IPs"],
     :valid_time
     {:start_time "2019-05-03T21:48:25.801Z",
      :end_time "2020-06-03T21:48:25.801Z"},
     :producer "Talos",
     :schema_version "1.0.11",
     :type "indicator",
     :source "Feed Indicator with COA Example",
     :external_ids
     ["esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501339f"],
     :short_description
     "Custom Malicious IP Watchlist",
     :title "Custom Malicious IP Watchlist",
     :indicator_type ["IP Watchlist"],
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :id
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501339f",
     :severity "High",
     :tlp "amber",
     :confidence "High"}],
   :judgements
   [{:valid_time
     {:start_time "2019-03-01T19:22:45.531Z",
      :end_time "2019-03-31T19:22:45.531Z"},
     :schema_version "1.0.11",
     :observable
     {:type "ip", :value "187.75.16.75"},
     :type "judgement",
     :source "Feed Indicator with COA Example",
     :external_ids
     ["esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d498"],
     :disposition 2,
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :disposition_name "Malicious",
     :priority 95,
     :id
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d498",
     :severity "High",
     :tlp "amber",
     :timestamp "2019-03-01T19:22:45.531Z",
     :confidence "High"}
    {:valid_time
     {:start_time "2019-03-01T19:22:45.531Z",
      :end_time "2019-03-31T19:22:45.531Z"},
     :schema_version "1.0.11",
     :observable
     {:type "ip", :value "187.75.16.75"},
     :type "judgement",
     :source "Feed Indicator with COA Example",
     :external_ids
     ["esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d499"],
     :disposition 3,
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :disposition_name "Suspicious",
     :priority 95,
     :id
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d499",
     :severity "High",
     :tlp "amber",
     :timestamp "2019-03-01T19:22:45.531Z",
     :confidence "High"}],
   :coas
   [{:description
     "Course of action to block IPs in a blacklist",
     :valid_time
     {:start_time "2019-03-01T19:22:45.531Z",
      :end_time "2019-03-31T19:22:45.531Z"},
     :stage "Response",
     :schema_version "1.0.11",
     :type "coa",
     :source "Feed Indicator with COA Example",
     :external_ids
     ["esa-coa-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d500"],
     :title "Block IPs in Blacklist",
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :coa_type "Internal Blocking",
     :id
     "transient:esa-coa-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d500",
     :tlp "amber",
     :timestamp "2019-03-01T19:22:45.531Z",
     :open_c2_coa
     {:id "foo",
      :type "structured_coa",
      :action {:type "deny"},
      :modifiers {:method ["blacklist"]}},
     :structured_coa_type "openc2"}
    {:description
     "Course of action to watch for IPs in a watchlist",
     :valid_time
     {:start_time "2019-03-01T19:22:45.531Z",
      :end_time "2019-03-31T19:22:45.531Z"},
     :stage "Response",
     :schema_version "1.0.11",
     :type "coa",
     :source "Feed Indicator with COA Example",
     :external_ids
     ["esa-coa-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d501"],
     :title "Observe IPs in watchlist",
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :coa_type "Monitoring",
     :id
     "transient:esa-coa-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d501",
     :tlp "amber",
     :timestamp "2019-03-01T19:22:45.531Z",
     :open_c2_coa
     {:id "foo",
      :type "structured_coa",
      :action {:type "alert"},
      :modifiers {:method ["blacklist"]}},
     :structured_coa_type "openc2"}],
   :relationships
   [{:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501339f",
     :type "relationship",
     :external_ids
     ["esa-relationship-1c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a5c"],
     :short_description
     "COA - 'Block IPs in Blacklist' mitigates indicator - 'Custom Malicious IP Watchlist'",
     :title "coa/indicator relationship",
     :external_references [],
     :source_ref
     "transient:esa-coa-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d500",
     :id
     "transient:esa-relationship-1c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a5c",
     :tlp "amber",
     :timestamp "2019-05-08T18:03:32.785Z",
     :relationship_type "mitigates"}
    {:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
     :type "relationship",
     :external_ids
     ["esa-relationship-1c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a6d"],
     :short_description
     "COA - 'Observe IPs in watchlist' applies to indicator - 'Custom Suspicious IP Watchlist'",
     :title "coa/indicator relationship",
     :external_references [],
     :source_ref
     "transient:esa-coa-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d501",
     :id
     "transient:esa-relationship-2c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a6d",
     :tlp "amber",
     :timestamp "2019-05-08T18:03:32.785Z",
     :relationship_type "mitigates"}
    {:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501339f",
     :type "relationship",
     :external_ids
     ["esa-relationship-1c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a5c"],
     :short_description
     "Judgement is part of indicator - 'Custom Malicious IP Watchlist'",
     :title "coa/indicator relationship",
     :external_references [],
     :source_ref
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d498",
     :id
     "transient:esa-relationship-3c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a5c",
     :tlp "amber",
     :timestamp "2019-05-08T18:03:32.785Z",
     :relationship_type "element-of"}
    {:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
     :type "relationship",
     :external_ids
     ["esa-relationship-1c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a6d"],
     :short_description
     "Judgement is part of indicator - 'Custom Suspicious IP Watchlist'",
     :title "coa/indicator relationship",
     :external_references [],
     :source_ref
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d499",
     :id
     "transient:esa-relationship-4c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a6d",
     :tlp "amber",
     :timestamp "2019-05-08T18:03:32.785Z",
     :relationship_type "element-of"}]})

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  whoami-helpers/fixture-server]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(defn feed-view-tests [_ feed]
  (testing "GET /ctia/feed/:id/view?s=:secret"
    (let [feed-view-url (:feed_view_url feed)
          response (client/get feed-view-url
                               {:as :json
                                :headers {"Authorization" "45c1f5e3f05d0"}})
          response-body (:body response)]

      (is (= 200 (:status response)))
      (is (= response-body
             {:observables [{:value "187.75.16.75", :type "ip"}]} )))))

(deftest test-feed-routes
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")

     (let [response (helpers/post "ctia/bundle/import"
                                  :body blocklist-bundle
                                  :headers {"Authorization" "45c1f5e3f05d0"})
           bundle-import-result (:parsed-body response)
           indicator-id (some->> (:results bundle-import-result)
                                 (filter #(= (:type %) :indicator))
                                 first
                                 :id)]

       (is (not (nil? indicator-id))
           "we successfully have an indicator id to test the view")

       (entity-crud-test
        {:entity "feed"
         :example (assoc new-feed-maximal
                         :indicator_id
                         indicator-id)
         :search-field :title
         :update-field :title
         :invalid-test-field :title
         :headers {:Authorization "45c1f5e3f05d0"}
         :additional-tests feed-view-tests})))))

#_(deftest test-feed-pagination-field-selection
    (test-for-each-store
     (fn []
       (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
       (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                           "foouser"
                                           "foogroup"
                                           "user")
       (testing "With Blocklist fixtures imported"
         (let [ids (post-entity-bulk
                    (assoc new-feed-maximal :title "foo")
                    :feeds
                    345
                    {"Authorization" "45c1f5e3f05d0"})]
           (field-selection-tests
            ["ctia/feed/search?query=*"
             (doc-id->rel-url (first ids))]
            {"Authorization" "45c1f5e3f05d0"}
            sort-restricted-feed-fields))

         (pagination-test
          "ctia/feed/search?query=*"
          {"Authorization" "45c1f5e3f05d0"}
          sort-restricted-feed-fields)))))

#_(deftest test-feed-routes-access-control
    (test-for-each-store
     (fn []
       (access-control-test "feed"
                            new-feed-minimal
                            true
                            true))))


