(ns ctia.task.migration.migrate-es-stores-test
  (:require [clj-http.client :as client]
            [clj-momo.test-helpers.core :as mth]
            [clojure
             [test :refer [deftest is join-fixtures testing use-fixtures]]
             [walk :refer [keywordize-keys]]]
            [ctia.properties :as props]
            [ctia.task.migration.migrate-es-stores :as sut]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post post-bulk with-atom-logger]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.store :refer [stores]]
            [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-minimal]]
             [attack-patterns :refer [attack-pattern-minimal]]
             [campaigns :refer [campaign-minimal]]
             [coas :refer [coa-minimal]]
             [incidents :refer [incident-minimal]]
             [indicators :refer [indicator-minimal]]
             [investigations :refer [investigation-minimal]]
             [judgements :refer [judgement-minimal]]
             [malwares :refer [malware-minimal]]
             [relationships :refer [relationship-minimal]]
             [casebooks :refer [casebook-minimal]]
             [sightings :refer [sighting-minimal]]
             [tools :refer [tool-minimal]]
             [vulnerabilities :refer [vulnerability-minimal]]
             [weaknesses :refer [weakness-minimal]]]))

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

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(defn refresh-indices [host port]
  (client/post (format "http://%s:%s/_refresh" host port)))

(defn make-cat-indices-url [host port]
  (format "http://%s:%s/_cat/indices?format=json&pretty=true" host port))

(defn get-cat-indices [host port]
  (let [url (make-cat-indices-url host
                                  port)
        {:keys [body]
         :as cat-response} (client/get url {:as :json})]
    (->> body
         (map (fn [{:keys [index]
                    :as entry}]
                {index (read-string
                        (:docs.count entry))}))
         (into {})
         keywordize-keys)))

(def examples
  {:actors (n-doc actor-minimal fixtures-nb)
   :attack_patterns (n-doc attack-pattern-minimal fixtures-nb)
   :campaigns (n-doc campaign-minimal fixtures-nb)
   :coas (n-doc coa-minimal fixtures-nb)
   :incidents (n-doc incident-minimal fixtures-nb)
   :indicators (n-doc indicator-minimal fixtures-nb)
   :investigations (n-doc investigation-minimal fixtures-nb)
   :judgements (n-doc judgement-minimal fixtures-nb)
   :malwares (n-doc malware-minimal fixtures-nb)
   :relationships (n-doc relationship-minimal fixtures-nb)
   :casebooks (n-doc casebook-minimal fixtures-nb)
   :sightings (n-doc sighting-minimal fixtures-nb)
   :tools (n-doc tool-minimal fixtures-nb)
   :vulnerabilities (n-doc vulnerability-minimal fixtures-nb)
   :weaknesses (n-doc weakness-minimal fixtures-nb)})

(deftest test-migrate-store-indexes
  ;; TODO clean data
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
      (sut/migrate-store-indexes "test-1"
                                 "0.0.0"
                                 [:0.4.16]
                                 (keys @stores)
                                 10
                                 false
                                 nil))
    (testing "migrate es indexes"
      (let [logger (atom [])]
        (with-atom-logger logger
          (sut/migrate-store-indexes "test2"
                                     "0.0.0"
                                     [:__test]
                                     (keys @stores)
                                     10
                                     true
                                     nil))

        (testing "shall produce valid logs"
          (let [messages (set @logger)]
            (is (contains? messages "set batch size: 10"))
            (is (clojure.set/subset?
                 ["campaign - finished migrating 100 documents"
                  "indicator - finished migrating 100 documents"
                  "event - finished migrating 1500 documents"
                  "actor - finished migrating 100 documents"
                  "relationship - finished migrating 100 documents"
                  "incident - finished migrating 100 documents"
                  "investigation - finished migrating 100 documents"
                  "coa - finished migrating 100 documents"
                  "identity - finished migrating 1 documents"
                  "judgement - finished migrating 100 documents"
                  "data-table - finished migrating 0 documents"
                  "feedback - finished migrating 0 documents"
                  "casebook - finished migrating 100 documents"
                  "sighting - finished migrating 100 documents"
                  "attack-pattern - finished migrating 100 documents"
                  "malware - finished migrating 100 documents"
                  "tool - finished migrating 100 documents"
                  "vulnerability - finished migrating 100 documents"
                  "weakness - finished migrating 100 documents"]
                 messages))))

        (testing "shall produce new indices
                  with enough documents and the right transforms"
          (let [{:keys [default
                        data-table
                        relationship
                        judgement
                        investigation
                        coa
                        tool
                        attack-pattern
                        malware
                        incident
                        event
                        indicator
                        campaign
                        sighting
                        casebook
                        actor
                        vulnerability
                        weakness]
                 :as es-props}
                (get-in @props/properties [:ctia :store :es])
                expected-indices
                (->> {relationship fixtures-nb
                      judgement fixtures-nb
                      coa fixtures-nb
                      attack-pattern fixtures-nb
                      malware fixtures-nb
                      tool fixtures-nb
                      incident fixtures-nb
                      event (* (count (keys examples))
                               fixtures-nb)
                      indicator fixtures-nb
                      investigation fixtures-nb
                      campaign fixtures-nb
                      casebook fixtures-nb
                      sighting fixtures-nb
                      actor fixtures-nb
                      vulnerability fixtures-nb
                      weakness fixtures-nb}
                     (map (fn [[k v]]
                            {(str  "v0.0.0_" (:indexname k)) v}))
                     (into {})
                     keywordize-keys)
                refreshes (refresh-indices (:host default)
                                           (:port default))
                formatted-cat-indices (get-cat-indices (:host default)
                                                       (:port default))]
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
