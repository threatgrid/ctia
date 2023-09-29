(ns ctia.bundle.core-test
  (:require [clojure.test :as t :refer [deftest use-fixtures are is testing]]
            [clojure.tools.logging.test :refer [logged? with-log]]
            [ctia.bundle.core :as sut]
            [ctia.domain.entities :refer [with-long-id]]
            [ctia.flows.crud :refer [make-id]]
            [ctia.test-helpers.core :as h]
            [ctia.test-helpers.http :refer [app->HTTPShowServices]]
            [ctia.test-helpers.es :as es-helpers]))

(deftest local-entity?-test
  (es-helpers/fixture-properties:es-store
    (fn []
      (h/fixture-ctia-with-app
        (fn [app]
          (are [x y] (= x (sut/local-entity? y (app->HTTPShowServices app)))
               false nil
               false ""
               false "http://unknown.site/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"
               true "indicator-56067199-47c0-4294-8957-13d6b265bdc4"
               true "http://localhost:57254/ctia/indicator/indicator-56067199-47c0-4294-8957-13d6b265bdc4"))))))

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
    (is (= "(source_ref:*malware*)"
           (:query (sut/relationships-filters "id" {:source_type [:malware]}))))
    (is (= "(target_ref:*sighting*)"
           (:query (sut/relationships-filters "id" {:target_type [:sighting]}))))
    (is (= "(target_ref:*sighting*) AND (source_ref:*malware*)"
           (:query (sut/relationships-filters "id" {:source_type [:malware]
                                                    :target_type [:sighting]}))))
    (is (= "(source_ref:*malware* OR source_ref:*vulnerability*)"
           (:query (sut/relationships-filters "id" {:source_type [:malware :vulnerability]}))))

    (is (= "(target_ref:*sighting* OR target_ref:*incident*)"
           (:query (sut/relationships-filters "id" {:target_type [:sighting :incident]})))))

  (testing "relationships-filters should return proper fields and combine filters"
    (is (= {:one-of {:source_ref "id"}
            :query "(source_ref:*malware*)"}
           (sut/relationships-filters "id" {:source_type [:malware]
                                            :related_to [:source_ref]})))))

(deftest with-existing-entity-test
  (es-helpers/fixture-properties:es-store
    (fn []
      (h/fixture-ctia-with-app
        (fn [app]
          (testing "with-existing-entity"
            (let [http-show-services (app->HTTPShowServices app)

                  indicator-id-1 (make-id "indicator")
                  indicator-id-2 (make-id "indicator")
                  indicator-id-3 (make-id "indicator")
                  indicator-original-id-1 (str "transient:" indicator-id-1)
                  new-indicator {:id indicator-original-id-1
                                 :external_ids ["swe-alarm-indicator-1"]}
                  find-by-ext-ids (fn [existing-ids]
                                    (fn [external_id]
                                      (map (fn [old-id]
                                             {:external_id external_id
                                              :entity {:id old-id}})
                                           existing-ids)))
                  test-fn (fn [{new-indicator* :new-indicator :keys [msg expected existing-ids id->old-entity log?]
                                :or {log? false
                                     existing-ids []
                                     id->old-entity {}
                                     new-indicator* new-indicator}}]
                            (with-log
                              (testing msg
                                (is (= expected
                                       (sut/with-existing-entity
                                         new-indicator*
                                         (find-by-ext-ids existing-ids)
                                         id->old-entity
                                         http-show-services)))
                                (is (= log?
                                       (logged? 'ctia.bundle.core
                                                :warn
                                                #"More than one entity is linked to the external ids"))))))]
              (test-fn {:msg "no existing external id"
                        :expected {:id indicator-original-id-1
                                   :external_ids ["swe-alarm-indicator-1"]}
                        :existing-ids []
                        :log? false})
              (let [{:keys [id] :as new-indicator} (with-long-id (assoc new-indicator :id indicator-id-1)
                                                     http-show-services)]
                (doseq [existing-ids [[]
                                      [indicator-id-2]
                                      [indicator-id-2
                                       indicator-id-1]]]
                  (testing (str "existing-ids:" (pr-str existing-ids))
                    (test-fn {:msg "existing long id"
                              :new-indicator {:new-entity new-indicator}
                              :id->old-entity {id {:id id}}
                              :expected {:result "exists"
                                         :id id
                                         :new-entity new-indicator}
                              :existing-ids existing-ids})
                    (test-fn {:msg "non-existing long id"
                              :new-indicator {:new-entity new-indicator}
                              :id->old-entity {}
                              :expected {:result "error"
                                         :error {:type :unresolvable-id
                                                 :reason (str "Long id must already correspond to an entity: "
                                                              id)}
                                         :new-entity new-indicator}
                              :existing-ids existing-ids}))))
              (test-fn {:msg "1 existing external id"
                        :expected (with-long-id {:result "exists"
                                                 :external_ids ["swe-alarm-indicator-1"]
                                                 :id indicator-id-1
                                                 :new-entity (with-long-id {:id indicator-id-1}
                                                               http-show-services)}
                                    http-show-services)
                        :existing-ids [indicator-id-1]
                        :log? false})
              (test-fn {:msg "more than 1 existing external id"
                        :expected (with-long-id {:result "exists"
                                                 :external_ids ["swe-alarm-indicator-1"]
                                                 :id indicator-id-2
                                                 :new-entity (with-long-id {:id indicator-id-2}
                                                               http-show-services)}
                                    http-show-services)
                        :existing-ids [indicator-id-2
                                       indicator-id-1]
                        :log? true}))))))))

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

(deftest merge-asset_properties-properties-test
  (let [[old1 old2 old3 new1 new2] (map (comp str gensym)
                                        '[old1 old2 old3 new1 new2])]
    (is (= [{:name "bar" :value new2}
            {:name "baz" :value old3}
            {:name "foo" :value new1}]
           (sut/merge-asset_properties-properties
             (shuffle [{:name "foo" :value new1}
                       {:name "bar" :value new2}])
             (shuffle [{:name "foo" :value old1}
                       {:name "bar" :value old2}
                       {:name "baz" :value old3}]))))
    (testing "right-most wins in both new and old"
      (is (= [{:name "bar" :value new2}
              {:name "baz" :value old1}
              {:name "foo" :value new2}]
             (sut/merge-asset_properties-properties
               [{:name "foo" :value new1}
                {:name "foo" :value new1}
                {:name "foo" :value new1}
                {:name "foo" :value new2}
                {:name "bar" :value new2}]
               [{:name "foo" :value old1}
                {:name "bar" :value old2}
                {:name "baz" :value old3}
                {:name "baz" :value old3}
                {:name "baz" :value old3}
                {:name "baz" :value old1}]))))))
