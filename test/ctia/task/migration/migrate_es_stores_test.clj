(ns ctia.task.migration.migrate-es-stores-test
  (:require [clojure
             [test :refer [deftest is join-fixtures testing use-fixtures]]
             [walk :refer [keywordize-keys]]]

            [schema.core :as s]
            [clj-http.client :as client]
            [clj-momo.test-helpers.core :as mth]
            [clj-momo.lib.es
             [conn :refer [connect]]
             [document :as es-doc]
             [index :as es-index]]

            [ctia.properties :as props]
            [ctia.task.migration
             [fixtures :refer [examples example-types fixtures-nb]]
             [migrate-es-stores :as sut]
             [store :refer [setup! prefixed-index]]]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post post-bulk with-atom-logger]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.stores.es.store :refer [store->map]]
            [ctia.store :refer [stores]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  whoami-helpers/fixture-server
                  es-helpers/fixture-delete-store-indexes]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

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

(defn index-exists?
  [store prefix]
  (let [{:keys [conn indexname]} (store->map store {})]
    (es-index/index-exists? conn
                            (prefixed-index indexname prefix))))

(setup!)
(def es-props (get-in @props/properties [:ctia :store :es]))
(def es-conn (connect (:default es-props)))
(def migration-index (get-in es-props [:migration :indexname]))

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
    (testing "simulate migrate es indexes shall not create any document"
      (sut/migrate-store-indexes "test-1"
                                 "0.0.0"
                                 [:0.4.16]
                                 (keys @stores)
                                 10
                                 false
                                 false)

      (doseq [store (vals @stores)]
        (is (not (index-exists? store "0.0.0"))))
      (is (nil? (seq (es-doc/get-doc es-conn
                                     (get-in es-props [:migration :indexname])
                                     "migration"
                                     "test-1"
                                     {})))))
    (testing "migrate es indexes"
      (let [logger (atom [])]
        (with-atom-logger logger
          (sut/migrate-store-indexes "test-2"
                                     "0.0.0"
                                     [:__test]
                                     (keys @stores)
                                     10
                                     true
                                     false))
        (testing "shall generate a proper migration state"
          (let [migration-state (es-doc/get-doc es-conn
                                                migration-index
                                                "migration"
                                                "test-2"
                                                {})]
            (is (= (set (keys @stores))
                   (set (keys (:stores migration-state)))))
            (doseq [[entity-type migrated-store] (:stores migration-state)]
              (let [{:keys [source target started completed]} migrated-store
                     source-size
                    (cond
                      (= :identity entity-type) 1
                      (= :event entity-type) (* fixtures-nb (count examples))
                      (contains? example-types (keyword entity-type)) fixtures-nb
                      :else 0)]
                (is (= source-size (:total source)))
                (is (not (nil? started)))
                (is (not (nil? completed)))
                (is (= (:total source)
                       (:migrated target)))
                (is (int? (:total source)))
                (is (= (:index target)
                       (prefixed-index (:index source) "0.0.0")))))))
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
                            hits))))))))
    ;;TODO test restart
    ;;insert/modify/delete documents
    ;; run migrate with restart true
    ;; check that modifications are taken into account in targets
    ;; (sut/migrate-store-indexes "test-2"
    ;;                            "0.0.0"
    ;;                            [:__test]
    ;;                            (keys @stores)
    ;;                            10
    ;;                            true
    ;;                            true))



    ;; TODO delete inserted examples / events, or remove indexes
    (es-index/delete! es-conn "v0.0.0*")
    (es-doc/delete-doc es-conn migration-index "migration" "test-2" "true")))

