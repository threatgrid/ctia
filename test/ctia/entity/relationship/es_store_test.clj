(ns ctia.entity.relationship.es-store-test
  (:require [clojure.test :refer [deftest is testing are]]
            [ctia.entity.relationship.es-store :as sut]
            [schema.test :refer [validate-schemas]]
            [clojure.test :refer [use-fixtures]]))

(use-fixtures :once validate-schemas)

(def base-relationship
  {:id "http://example.com/ctia/relationship/relationship-123"
   :type "relationship"
   :schema_version "1.1.0"
   :relationship_type "related-to"
   :source_ref "http://example.com/ctia/malware/malware-456"
   :target_ref "http://example.com/ctia/sighting/sighting-789"
   :tlp "amber"
   :owner "test-user"
   :groups ["test-group"]
   :created #inst "2024-01-01T00:00:00.000Z"
   :modified #inst "2024-01-01T00:00:00.000Z"})

(deftest stored-relationship->es-stored-relationship-test
  (testing "extracts source_type and target_type from refs"
    (let [result (sut/stored-relationship->es-stored-relationship base-relationship)]
      (is (= "malware" (:source_type result)))
      (is (= "sighting" (:target_type result)))
      ;; Original fields preserved
      (is (= (:source_ref base-relationship) (:source_ref result)))
      (is (= (:target_ref base-relationship) (:target_ref result)))))

  (testing "handles various entity types"
    (are [source-type target-type source-ref target-ref]
        (let [rel (assoc base-relationship
                         :source_ref source-ref
                         :target_ref target-ref)
              result (sut/stored-relationship->es-stored-relationship rel)]
          (and (= source-type (:source_type result))
               (= target-type (:target_type result))))

      "indicator" "incident"
      "http://example.com/ctia/indicator/indicator-123"
      "http://example.com/ctia/incident/incident-456"

      "judgement" "verdict"
      "http://example.com/ctia/judgement/judgement-123"
      "http://example.com/ctia/verdict/verdict-456"

      "attack-pattern" "vulnerability"
      "http://example.com/ctia/attack-pattern/attack-pattern-123"
      "http://example.com/ctia/vulnerability/vulnerability-456"

      "casebook" "investigation"
      "http://example.com/ctia/casebook/casebook-123"
      "http://example.com/ctia/investigation/investigation-456")))

(deftest es-stored-relationship->stored-relationship-test
  (testing "removes source_type and target_type"
    (let [es-relationship (assoc base-relationship
                                 :source_type "malware"
                                 :target_type "sighting")
          result (sut/es-stored-relationship->stored-relationship es-relationship)]
      (is (nil? (:source_type result)))
      (is (nil? (:target_type result)))
      ;; Original fields preserved
      (is (= (:source_ref base-relationship) (:source_ref result)))
      (is (= (:target_ref base-relationship) (:target_ref result)))
      (is (= (:relationship_type base-relationship) (:relationship_type result))))))

(deftest es-partial-stored-relationship->partial-stored-relationship-test
  (testing "removes source_type and target_type from partial"
    (let [partial-rel {:id "http://example.com/ctia/relationship/relationship-123"
                       :source_ref "http://example.com/ctia/malware/malware-456"
                       :source_type "malware"
                       :target_type "sighting"}
          result (sut/es-partial-stored-relationship->partial-stored-relationship partial-rel)]
      (is (nil? (:source_type result)))
      (is (nil? (:target_type result)))
      (is (= (:source_ref partial-rel) (:source_ref result)))))

  (testing "handles partial without type fields"
    (let [partial-rel {:id "http://example.com/ctia/relationship/relationship-123"
                       :source_ref "http://example.com/ctia/malware/malware-456"}
          result (sut/es-partial-stored-relationship->partial-stored-relationship partial-rel)]
      (is (nil? (:source_type result)))
      (is (nil? (:target_type result))))))

(deftest store-opts-test
  (testing "stored->es-stored extracts from :doc wrapper"
    (let [doc-wrapper {:doc base-relationship}
          transform-fn (:stored->es-stored sut/store-opts)
          result (transform-fn doc-wrapper)]
      (is (= "malware" (:source_type result)))
      (is (= "sighting" (:target_type result)))))

  (testing "es-stored->stored extracts from :doc wrapper and removes types"
    (let [es-rel (assoc base-relationship
                        :source_type "malware"
                        :target_type "sighting")
          doc-wrapper {:doc es-rel}
          transform-fn (:es-stored->stored sut/store-opts)
          result (transform-fn doc-wrapper)]
      (is (nil? (:source_type result)))
      (is (nil? (:target_type result)))))

  (testing "es-partial-stored->partial-stored extracts from :doc wrapper"
    (let [partial-rel {:source_type "malware" :target_type "sighting"}
          doc-wrapper {:doc partial-rel}
          transform-fn (:es-partial-stored->partial-stored sut/store-opts)
          result (transform-fn doc-wrapper)]
      (is (nil? (:source_type result)))
      (is (nil? (:target_type result))))))
