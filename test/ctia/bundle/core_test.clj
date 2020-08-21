(ns ctia.bundle.core-test
  (:require [ctia.bundle.core :as sut]
            [clojure.tools.logging.test :refer [logged? with-log]]
            [ctia.domain.entities :as ent :refer [with-long-id]]
            [ctia.flows.crud :refer [make-id]]
            [clojure.test :as t :refer [deftest use-fixtures join-fixtures are is testing]]
            [ctia.test-helpers.core :as h]))

(use-fixtures :once
  (join-fixtures [h/fixture-properties:clean
                  h/fixture-ctia-fast]))

(deftest local-entity?-test
  (are [x y] (= x (sut/local-entity? y (h/current-get-in-config-fn)))
    false nil
    false "http://unknown.site/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"
    true "indicator-56067199-47c0-4294-8957-13d6b265bdc4"
    true "http://localhost:57254/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"))

(deftest clean-bundle-test
  (is (= {:b '(1 2 3) :d '(1 3)}
         (sut/clean-bundle {:a '(nil) :b '(1 2 3) :c '() :d '(1 nil 3)}))))

(deftest relationships-filters
  (testing "relationships-filters should properly add related_to filters to handle edge direction"
    (is (= {:source_ref "id"
            :target_ref "id"}
           (:one-of (sut/relationships-filters "id" {})))
        "default related-_to param is #{:source_ref :target_ref}")
    (is (= {:source_ref "id"}
           (:one-of (sut/relationships-filters "id" {:related_to [:source_ref]}))))
    (is (= {:target_ref "id"}
           (:one-of (sut/relationships-filters "id" {:related_to [:target_ref]}))))
    (is (= {:source_ref "id"
            :target_ref "id"}
           (:one-of (sut/relationships-filters "id" {:related_to [:source_ref :target_ref]})))))

  (testing "relationships-filters should properly add query filters"
    (is (= "source_ref:*malware*"
           (:query (sut/relationships-filters "id" {:source_type :malware}))))
    (is (= "target_ref:*sighting*"
           (:query (sut/relationships-filters "id" {:target_type :sighting}))))
    (is (= "target_ref:*sighting* AND source_ref:*malware*"
           (:query (sut/relationships-filters "id" {:source_type :malware
                                                    :target_type :sighting})))))

  (testing "relationships-filters should return proper fields and combine filters"
    (is (= {:one-of {:source_ref "id"}
            :query "source_ref:*malware*"}
           (sut/relationships-filters "id" {:source_type :malware
                                            :related_to [:source_ref]})))))

(deftest with-existing-entity-test
  (testing "with-existing-entity"
    (let [app (h/get-current-app)
          get-in-config (h/current-get-in-config-fn app)
          indicator-id-1 (make-id "indicator")
          indicator-id-2 (make-id "indicator")
          indicator-id-3 (make-id "indicator")
          new-indicator {:id indicator-id-3
                         :external_ids ["swe-alarm-indicator-1"]}
          find-by-ext-ids (fn [existing-ids]
                            (constantly
                             (map (fn [old-id]
                                    {:entity {:id old-id}})
                                  existing-ids)))
          test-fn (fn [{:keys [msg expected existing-ids log?]}]
                    (with-log
                      (testing msg
                        (is (= expected
                               (sut/with-existing-entity
                                 new-indicator
                                 (find-by-ext-ids existing-ids))))
                        (is (= log?
                               (logged? 'ctia.bundle.core
                                        :warn
                                        #"More than one entity is linked to the external ids"))))))]
      (test-fn {:msg "no existing external id"
                :expected {:id indicator-id-3
                           :external_ids ["swe-alarm-indicator-1"]}
                :existing-ids []
                :log? false})
      (test-fn {:msg "1 existing external id"
                :expected (with-long-id {:result "exists"
                                         :external_ids ["swe-alarm-indicator-1"]
                                         :id indicator-id-1}
                                        get-in-config)
                :existing-ids [indicator-id-1]
                :log? false})
      (test-fn {:msg "more than 1 existing external id"
                :expected (with-long-id {:result "exists"
                                         :external_ids ["swe-alarm-indicator-1"]
                                         :id indicator-id-2}
                                        get-in-config)
                :existing-ids [indicator-id-2
                               indicator-id-1]
                :log? true}))))

(deftest filter-external-ids-test
  (let [external-ids ["ctia-indicator-1" "cisco-indicator-1" "indicator-1"]]
    (are [expected prefixes log?]
        (testing prefixes
          (with-log
            (is (= expected
                   (sut/filter-external-ids external-ids prefixes)))
            (is (= log?
                   (logged? 'ctia.bundle.core
                            :warn
                            #"More than 1 valid external ID has been found"))))
          true)
      external-ids [] true
      external-ids nil true
      [] ["not-matched"] false
      ["ctia-indicator-1"] ["ctia"] false
      ["ctia-indicator-1" "cisco-indicator-1"] ["ctia" "cisco"] true)))

(deftest entity->import-data-test
  (let [sighting-id (make-id "sighting")
        external_ids ["ireaux-sighting-2"
                      "ireaux-sighting-1"
                      "ctia-sighting-1"]
        test-fn (fn [{:keys [entity prefixes entity-type] :as _params}
                     {:keys [log? output] :as _expected}]
                  (with-log
                    (let [res (sut/entity->import-data entity
                                                       entity-type
                                                       prefixes)]
                      (is (= log?
                             (logged? 'ctia.bundle.core
                                      :warn
                                      #"No valid external ID has been provided"))::test)
                      (is (= output res)))))]
    (are [msg params expected]
        (testing msg
          (test-fn params expected)
          true)

      "no external prefixes and no external_id"
      {:entity {:id sighting-id}
       :entity-type :sighting
       :prefixes ""}
      {:log? true
       :output {:new-entity {:id sighting-id}
                :type :sighting}}

      "no prefixes, no external_id and transient id"
      {:entity {:id "transient:sighting-1"}
       :entity-type :sighting
       :prefixes ""}
      {:log? true
       :output {:new-entity {:id "transient:sighting-1"}
                :type :sighting
                :original_id "transient:sighting-1"}}

      "prefixes but no external_id"
      {:entity {:id sighting-id}
       :entity-type :sighting
       :prefixes "ireaux-"}
      {:log? true
       :output {:new-entity {:id sighting-id}
                :type :sighting}}

      "all external_ids match one prefix"
      {:entity {:id sighting-id
                :external_ids external_ids}
       :entity-type :sighting
       :prefixes "ireaux-,ctia-"}
      {:log? false
       :output {:new-entity {:id sighting-id
                             :external_ids external_ids}
                :type :sighting
                :external_ids external_ids}}

      "external_ids, only some match external prefixes"
      {:entity {:id sighting-id
                :external_ids external_ids}
       :entity-type :sighting
       :prefixes "ireaux-"}
      {:log? false
       :output {:new-entity {:id sighting-id
                             :external_ids external_ids}
                :type :sighting
                :external_ids ["ireaux-sighting-2"
                               "ireaux-sighting-1"]}}

      "external_ids that match none of provided external_ids"
      {:entity {:id sighting-id
                :external_ids external_ids}
       :entity-type :sighting
       :prefixes "unmatched-"}
      {:log? true
       :output {:new-entity {:id sighting-id
                             :external_ids external_ids}
                :type :sighting}}

      "external_ids with no external prefixes should be preserved"
      {:entity {:id sighting-id
                :external_ids external_ids}
       :entity-type :sighting
       :prefixes ""}
      {:log? false
       :output {:new-entity {:id sighting-id
                             :external_ids external_ids}
                :type :sighting
                :external_ids external_ids}})))
