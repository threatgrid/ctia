(ns ctia.task.migrate-es-stores-test
  (:require [clj-http.client :as client]
            [clj-momo.test-helpers.core :as mth]
            [clojure
             [test :refer [deftest is join-fixtures testing use-fixtures]]
             [walk :refer [keywordize-keys]]]
            [ctia.properties :as props]
            [ctia.task.migrate-es-stores :as sut]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post with-atom-logger]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-minimal]]
             [campaigns :refer [campaign-minimal]]
             [coas :refer [coa-minimal]]
             [exploit-targets :refer [exploit-target-minimal]]
             [incidents :refer [incident-minimal]]
             [indicators :refer [indicator-minimal]]
             [judgements :refer [judgement-minimal]]
             [relationships :refer [relationship-minimal]]
             [sightings :refer [sighting-minimal]]
             [ttps :refer [ttp-minimal]]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  whoami-helpers/fixture-server
                  es-helpers/fixture-delete-store-indexes]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(def fixtures-nb 100)

(defn post-bulk [examples]
  (let [{status :status
         bulk-res :parsed-body}
        (post "ctia/bulk"
              :body examples
              :headers {"Authorization" "45c1f5e3f05d0"})]
    (is (= 201 status))))

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(defn refresh-index [host port index]
  (let [index-refresh-url
        (format "http://%s:%s/%s/_refresh" host port index)]
    (:status (client/post index-refresh-url))))

(def examples
  {:actors (n-doc actor-minimal fixtures-nb)
   :campaigns (n-doc campaign-minimal fixtures-nb)
   :coas (n-doc coa-minimal fixtures-nb)
   :exploit-targets (n-doc exploit-target-minimal fixtures-nb)
   :incidents (n-doc incident-minimal fixtures-nb)
   :indicators (n-doc indicator-minimal fixtures-nb)
   :judgements (n-doc judgement-minimal fixtures-nb)
   :relationships (n-doc relationship-minimal fixtures-nb)
   :sightings (n-doc sighting-minimal fixtures-nb)
   :ttps (n-doc ttp-minimal fixtures-nb)})

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

(deftest test-migrate-store-indexes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")

  (testing "migrate ES Stores test setup"
    (post-bulk examples)
    (testing "simulate migrate es indexes"
      (sut/migrate-store-indexes "foo"
                                 "add-groups,fix-end-time"
                                 10
                                 false))
    (testing "migrate es indexes"
      (let [logger (atom [])]
        (with-atom-logger logger
          (sut/migrate-store-indexes "foo"
                                     "__test"
                                     10
                                     true))

        (testing "shall produce valid logs"
          (let [messages (set @logger)]
            (is (contains? messages "set batch size: 10"))
            (is (clojure.set/subset?
                 ["campaign - finished migrating 100 documents"
                  "indicator - finished migrating 100 documents"
                  "exploit-target - finished migrating 100 documents"
                  "event - finished migrating 1000 documents"
                  "actor - finished migrating 100 documents"
                  "relationship - finished migrating 100 documents"
                  "incident - finished migrating 100 documents"
                  "coa - finished migrating 100 documents"
                  "identity - finished migrating 1 documents"
                  "judgement - finished migrating 100 documents"
                  "data-table - finished migrating 0 documents"
                  "feedback - finished migrating 0 documents"
                  "sighting - finished migrating 100 documents"
                  "ttp - finished migrating 100 documents"]
                 messages))))

        (testing "shall produce new indices
                  with enough documents and the right transforms"
          (let [{:keys [default
                        data-table
                        relationship
                        judgement
                        exploit-target
                        coa
                        ttp
                        incident
                        event
                        indicator
                        campaign
                        sighting
                        actor]
                 :as es-props}
                (get-in @props/properties [:ctia :store :es])
                expected-indices
                (->> {relationship fixtures-nb
                      judgement fixtures-nb
                      exploit-target fixtures-nb
                      coa fixtures-nb
                      ttp fixtures-nb
                      incident fixtures-nb
                      event (* (count (keys examples))
                               fixtures-nb)
                      indicator fixtures-nb
                      campaign fixtures-nb
                      sighting fixtures-nb
                      actor fixtures-nb}
                     (map (fn [[k v]]
                            {(str  "foo_" (:indexname k)) v}))
                     (into {})
                     keywordize-keys)
                refreshes
                (doseq [index (keys expected-indices)]
                  (refresh-index (:host default)
                                 (:port default)
                                 (name index)))
                cat-indices-url
                (format "http://%s:%s/_cat/indices?format=json&pretty=true"
                        (:host default)
                        (:port default))
                {:keys [body]
                 :as cat-response}
                (client/get cat-indices-url {:as :json})
                formatted-cat-indices
                (->> body
                     (map (fn [{:keys [index]
                               :as entry}]
                            {index (read-string
                                    (:docs.count entry))}))
                     (into {})
                     keywordize-keys)]

            (is (= expected-indices
                   (select-keys formatted-cat-indices
                                (keys expected-indices))))

            (doseq [[index _]
                    expected-indices]
              (let [search-url (format "http://%s:%s/%s/_search"
                                       (:host default)
                                       (:port default)
                                       (name index))
                    {:keys [body]
                     :as search-res}
                    (client/get search-url {:as :json})
                    hits (->> body
                              :hits
                              :hits
                              (map #(get-in % [:_source :groups])))]
                (is (every? #(= ["migration-test"] %)
                            hits))))))))))
