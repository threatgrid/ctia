(ns ctia.entity.feed-pagination-test
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

(use-fixtures :once
  (join-fixtures [validate-schemas
                  whoami-helpers/fixture-server]))

(def indicator-id
  "transient:esa-indicator-ec95b")

(def indicator
  {:description "" ,
   :tags ["Suspicious IPs"],
   :valid_time {:start_time "2019-05-03T21:48:25.801Z",
                :end_time "2052-06-03T21:48:25.801Z"},
   :producer "Talos",
   :schema_version "1.0.11",
   :type "indicator",
   :source "Feed Indicator Example",
   :external_ids ["ctia-feed-indicator-test"],
   :short_description "Custom Suspicious IP Watchlist",
   :title "Custom Suspicious IP Watchlist",
   :indicator_type ["IP Watchlist"],
   :source_uri "http://example.com/foo" ,
   :id indicator-id,
   :severity "High",
   :tlp "amber",
   :confidence "High"})

(def base-judgement
  {:schema_version "1.0.11",
   :type "judgement",
   :source "" ,
   :external_ids ["ctia-feed-indicator-test"],
   :disposition 2,
   :source_uri "http://example.com/foo" ,
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

(def relationships
  (map #(let [suffix (string/replace % #"transient:esa-judgement-" "")
              transient-id (str "transient:esa-relationship-" suffix)]
          {:id transient-id
           :source_ref %
           :schema_version "1.0.11",
           :target_ref indicator-id,
           :type "relationship",
           :external_ids ["ctia-feed-indicator-test"],
           :short_description "" ,
           :title "" ,
           :external_references [],
           :tlp "amber",
           :timestamp "2019-05-08T18:03:32.785Z",
           :relationship_type "element-of"})
       (map :id judgements)))

(def bundle
  {:type "bundle",
   :source "Feed Indicator Example",
   :source_uri "https://github.com/threatgrid/ctim/blob/master/src/doc/tutorials/modeling-threat-intel-ctim.md",
   :indicators [indicator],
   :judgements judgements
   :relationships relationships})

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

(deftest test-feed-pagination
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
     (with-redefs [ductile.pagination/max-result-window 40]
       (let [response (helpers/POST app
                          "ctia/bundle/import"
                          :body bundle
                          :headers {"Authorization" "45c1f5e3f05d0"})
             bundle-import-result (:parsed-body response)
             indicator-id (some->> (:results bundle-import-result)
                                   (filter #(= (:type %) :indicator))
                                   first
                                   :id)

             {{feed-view-url :feed_view_url} :parsed-body}
             (helpers/POST app "ctia/feed?wait_for=true"
               :body (assoc new-feed-maximal
                            :indicator_id indicator-id)
               :headers {:Authorization "45c1f5e3f05d0"})]

         (testing "when no pagination params - return entire collection"
           (let [expected-response (into #{} (map #(-> % :observable :value)) judgements)
                 response (let [{:keys [body]} (client/get feed-view-url)]
                            (into #{} (string/split-lines body)))]
             (is (= 100 (count response)))
             (is (= response expected-response)))))))))
