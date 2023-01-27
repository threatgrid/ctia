(ns ctia.entity.feed-test
  (:require
   [clj-http.client :as client]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [ctia.entity.feed :as sut]
   [ctia.test-helpers.access-control :refer [access-control-test]]
   [ctia.test-helpers.auth :refer [all-capabilities]]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.crud :refer [entity-crud-test]]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [test-for-each-store-with-app]]
   [ctim.schemas.common :as c]
   [schema.test :refer [validate-schemas]]))

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

(def indicator
  {:description
   "A lookup table for IPs (IPv4 and IPv6) that are considered suspicious by security analysts",
   :tags ["Suspicious IPs"],
   :valid_time
   {:start_time "2019-05-03T21:48:25.801Z",
    :end_time "2052-06-03T21:48:25.801Z"},
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
   :confidence "High"})

(def base-judgement
  {:schema_version "1.0.11",
   :type "judgement",
   :source "Feed Indicator Example",
   :external_ids
   ["ctia-feed-indicator-test"],
   :disposition 2,
   :source_uri
   "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
   :disposition_name "Malicious",
   :priority 95,
   :severity "High",
   :tlp "amber",
   :timestamp "2019-03-01T19:22:45.531Z",
   :confidence "High"})

(def judgements
  (map #(let [transient-id (format "transient:esa-judgement-%03d" %)
              ip (format "187.75.42.%d" %)]
          (into base-judgement {:id transient-id
                                :valid_time {:start_time "2019-03-01T19:22:45.531Z",
                                             :end_time "2052-03-31T19:22:45.531Z"}
                                :observable {:type "ip" :value ip}}))
       (range 100)))

(def expired-judgements
  (map #(let [transient-id (format "transient:esa-judgement-%03d" %)
              ip (format "187.75.16.%d" %)]
          (into base-judgement {:id transient-id
                                :valid_time {:start_time "2019-03-01T19:22:45.531Z",
                                             :end_time "2019-03-31T19:22:45.531Z"}
                                :observable {:type "ip" :value ip}}))
       (range 100 200)))

(def relationships
  (map #(let [suffix (string/replace % #"transient:esa-judgement-" "")
              transient-id (str "transient:esa-relationship-" suffix)]
          {:id transient-id
           :source_ref %
           :schema_version "1.0.11",
           :target_ref
           "transient:esa-indicator-ec95b042572a11894fffe553555c44f5c88c9199aad23a925bb959daa501338e",
           :type "relationship",
           :external_ids
           ["ctia-feed-indicator-test"],
           :short_description
           "Judgement is part of indicator - 'Custom Malicious IP Watchlist'",
           :title "judgement/indicator relationship",
           :external_references [],
           :tlp "amber",
           :timestamp "2019-05-08T18:03:32.785Z",
           :relationship_type "element-of"})
       (map :id judgements)))

(def blocklist-bundle
  {:type "bundle",
   :source "Feed Indicator Example",
   :source_uri
   "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
   :indicators [indicator],
   :judgements (let [duplicated-observable-value (-> judgements
                                                     first
                                                     (assoc :id "transient:esa-judgement-4340e8cc49ff428e21ad1467de4b40246eb0e3b8da96caa2f71f9fe54123d500"))]
                 (-> (conj judgements duplicated-observable-value)
                     (concat expired-judgements)))
   :relationships relationships})

(use-fixtures :once
  (join-fixtures [validate-schemas
                  whoami-helpers/fixture-server]))

(defn feed-view-tests [app feed-id feed]
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
          response-body-txt-wrong-secret (:body response-txt-wrong-secret)
          expected-body (->> (map #(-> % :observable :value) judgements)
                             sort
                             (string/join "\n"))]
      (assert (not (string/blank? expected-body)))
      (is (= 200 (:status response-txt)))
      (is (= expected-body
             response-body-txt))

      (is (= 401 (:status response-txt-wrong-secret)))
      (is (= "wrong secret"
             response-body-txt-wrong-secret))

      (testing "bad request"
        (let [url-with-invalid-query-params
              (string/replace feed-view-url-txt #"s=" "invalid=")
              {:keys [body headers status]}
              (client/get url-with-invalid-query-params {:throw-exceptions false})]
          (is (= 400 status))
          (is (string/starts-with? (get headers "Content-Type") "text/plain"))
          (is (= "{:errors {:s missing-required-key, :invalid disallowed-key}}" body))))

      (testing "feed output judgements"
        (let [feed-update (assoc feed :output :judgements)
              updated-feed-response
              (helpers/PUT app
                  (str "ctia/feed/" (:short-id feed-id))
                  :body feed-update
                  :headers {"Authorization" "45c1f5e3f05d0"})
              updated-feed (:parsed-body updated-feed-response)]
          (is (= 200 (:status updated-feed-response)))
          (is (= (dissoc feed-update :feed_view_url)
                 (dissoc updated-feed
                         :feed_view_url)))

          (let [feed-view-url (:feed_view_url updated-feed)
                valid-response  (client/get feed-view-url
                                            {:as :json})
                valid-response-body (:body valid-response)]

            (is (= 200 (:status valid-response)))
            (is (= (count judgements)
                   (count (:judgements valid-response-body))))
            (is (= (set (map :observable
                             judgements))
                   (set (map :observable
                             (:judgements valid-response-body)))))

            ;;teardown
            (is (= 200 (:status
                        (helpers/PUT app
                            (str "ctia/feed/" (:short-id feed-id))
                            :body feed
                            :headers {"Authorization" "45c1f5e3f05d0"})))))))))

  (testing "pagination"
    (let [feed-view-url (:feed_view_url feed)
          counter (atom 0)
          expected-response (into #{} (map #(-> % :observable :value)) judgements)
          response (loop [acc #{} limit 20 search-after []]
                     (let [{:keys [headers body]}
                           (client/get feed-view-url {:query-params {:limit limit
                                                                     :search_after search-after}})]
                       (swap! counter inc)
                       (if (contains? headers "X-Search_after")
                         (recur (into acc (string/split-lines body))
                                (edn/read-string (get headers "X-Limit"))
                                (edn/read-string (get headers "X-Search_after")))
                         acc)))]
      (is (= response expected-response))
      (is (= (inc (/ (count expected-response) 20)) @counter))))

  (testing "when no pagination params - return entire collection"
    (let [feed-view-url (:feed_view_url feed)
          expected-response (into #{} (map #(-> % :observable :value)) judgements)
          response (let [{:keys [body]} (client/get feed-view-url)]
                     (into #{} (string/split-lines body)))]
      (is (= 100 (count response)))
      (is (= response expected-response)))))

(deftest test-feed-routes
  (test-for-each-store-with-app
   (fn [app]
     (helpers/set-capabilities! app
                                "foouser"
                                ["foogroup"]
                                "user"
                                all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         "45c1f5e3f05d0"
                                         "foouser"
                                         "foogroup"
                                         "user")
     (let [response (helpers/POST app
                        "ctia/bundle/import"
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
        (into sut/feed-entity
              {:app app
               :example (assoc new-feed-maximal
                               :indicator_id
                               indicator-id)
               :search-field :title
               :update-field :title
               :invalid-test-field :title
               :delete-search-tests? false
               :headers {:Authorization "45c1f5e3f05d0"}
               :additional-tests feed-view-tests}))))))

(deftest test-feed-routes-access-control
  (access-control-test "feed"
                       new-feed-minimal
                       true
                       true
                       test-for-each-store-with-app))
