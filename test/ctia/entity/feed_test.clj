(ns ctia.entity.feed-test
  (:require
   [clojure.string :as string]
   [clj-momo.test-helpers.core :as mth]
   [clj-http.client :as client]
   [ctim.schemas.common :as c]
   [clojure.test :refer [deftest testing is join-fixtures use-fixtures]]
   [ctia.entity.feed
    :refer [sort-restricted-feed-fields]]
   [ctia.test-helpers
    [access-control :refer [access-control-test]]
    [auth :refer [all-capabilities]]
    [core :as helpers :refer [post]]
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
   :source "Feed Indicator Example",
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
     :source "Feed Indicator Example",
     :external_ids
     ["ctia-feed-indicator-test"],
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
     :confidence "High"}],
   :judgements
   [{:valid_time
     {:start_time "2019-03-01T19:22:45.531Z",
      :end_time "2019-03-31T19:22:45.531Z"},
     :schema_version "1.0.11",
     :observable
     {:type "ip", :value "187.75.16.77"},
     :type "judgement",
     :source "Feed Indicator Example",
     :external_ids
     ["ctia-feed-indicator-test"],
     :disposition 2,
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :disposition_name "Malicious",
     :priority 95,
     :id
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d400",
     :severity "High",
     :tlp "amber",
     :timestamp "2019-03-01T19:22:45.531Z",
     :confidence "High"}
    {:valid_time
     {:start_time "2019-03-01T19:22:45.531Z",
      :end_time "2019-03-31T19:22:45.531Z"},
     :schema_version "1.0.11",
     :observable
     {:type "ip", :value "187.75.16.76"},
     :type "judgement",
     :source "Feed Indicator Example",
     :external_ids
     ["ctia-feed-indicator-test"],
     :disposition 2,
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :disposition_name "Malicious",
     :priority 95,
     :id
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d499",
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
     :source "Feed Indicator Example",
     :external_ids
     ["ctia-feed-indicator-test"],
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
     :source "Feed Indicator Example",
     :external_ids
     ["ctia-feed-indicator-test"],
     :disposition 3,
     :source_uri
     "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
     :disposition_name "Suspicious",
     :priority 95,
     :id
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d500",
     :severity "High",
     :tlp "amber",
     :timestamp "2019-03-01T19:22:45.531Z",
     :confidence "High"}],
   :relationships
   [{:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
     :type "relationship",
     :external_ids
     ["ctia-feed-indicator-test"],
     :short_description
     "Judgement is part of indicator - 'Custom Malicious IP Watchlist'",
     :title "judgement/indicator relationship",
     :external_references [],
     :source_ref
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d500",
     :id
     "transient:esa-relationship-3c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a5e",
     :tlp "amber",
     :timestamp "2019-05-08T18:03:32.785Z",
     :relationship_type "element-of"}
    {:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
     :type "relationship",
     :external_ids
     ["ctia-feed-indicator-test"],
     :short_description
     "Judgement is part of indicator - 'Custom Malicious IP Watchlist'",
     :title "judgement/indicator relationship",
     :external_references [],
     :source_ref
     "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d400",
     :id
     "transient:esa-relationship-3c056c6ef8ace5057980b57f3eb07b916c84d94f7d1a340f41aba7630c459a5d",
     :tlp "amber",
     :timestamp "2019-05-08T18:03:32.785Z",
     :relationship_type "element-of"}
    {:schema_version "1.0.11",
     :target_ref
     "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
     :type "relationship",
     :external_ids
     ["ctia-feed-indicator-test"],
     :short_description
     "Judgement is part of indicator - 'Custom Suspicious IP Watchlist'",
     :title "judgement/indicator relationship",
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

(defn feed-view-tests [feed-id feed]
  (testing "GET /ctia/feed/:id/view?s=:secret"
    (let [feed-view-url-txt (:feed_view_url feed)
          feed-view-url-txt-wrong-secret (->> (drop-last feed-view-url-txt)
                                              (string/join ""))
          response-txt (client/get feed-view-url-txt {})
          response-txt-wrong-secret
          (client/get feed-view-url-txt-wrong-secret
                      {:throw-exceptions false
                       :headers {"Authorization" "45c1f5e3f05d0"}})
          response-body-txt (:body response-txt)
          response-body-txt-wrong-secret (:body response-txt-wrong-secret)]

      (is (= 200 (:status response-txt)))
      (is (= "187.75.16.75\n187.75.16.76\n187.75.16.77"
             response-body-txt))

      (is (= 401 (:status response-txt-wrong-secret)))
      (is (= "wrong secret"
             response-body-txt-wrong-secret))

      (testing "feed output judgements"
        (let [feed-update (assoc feed :output :judgements)
              updated-feed-response
              (helpers/put (str "ctia/feed/" (:short-id feed-id))
                           :body feed-update
                           :headers {"Authorization" "45c1f5e3f05d0"})
              updated-feed (:parsed-body updated-feed-response)]
          (is (= 200 (:status updated-feed-response)))
          (is (= (dissoc feed-update :feed_view_url)
                 (dissoc updated-feed
                         :feed_view_url)))

          (let [feed-view-url (:feed_view_url updated-feed)
                response (client/get feed-view-url
                                     {:as :json})
                response-body (:body response)]

            (is (= 200 (:status response)))
            (is (= (set (map :observable
                             (:judgements blocklist-bundle)))
                   (set (map :observable
                             (:judgements response-body)))))
            ;;teardown
            (is (= 200
                   (:status
                    (helpers/put (str "ctia/feed/" (:short-id feed-id))
                                 :body feed
                                 :headers {"Authorization" "45c1f5e3f05d0"}))))))))))

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

(deftest test-feed-pagination-field-selection
  (test-for-each-store
   (fn []
     (helpers/set-capabilities! "foouser" ["foogroup"] "user" all-capabilities)
     (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [entities (repeat 345 (assoc new-feed-maximal
                                       :title "foo"))
           ids (->> (doall (map #(post "/ctia/feed"
                                       :body (dissoc % :id)
                                       :headers {"Authorization"
                                                 "45c1f5e3f05d0"})
                                entities))
                    (map :parsed-body)
                    (map :id))]
       (field-selection-tests
        ["ctia/feed/search?query=*"
         (doc-id->rel-url (first ids))]
        {"Authorization" "45c1f5e3f05d0"}
        sort-restricted-feed-fields)
       (pagination-test
        "ctia/feed/search?query=*"
        {"Authorization" "45c1f5e3f05d0"}
        sort-restricted-feed-fields)))))

(deftest test-feed-routes-access-control
  (test-for-each-store
   (fn []
     (access-control-test "feed"
                          new-feed-minimal
                          true
                          true))))
