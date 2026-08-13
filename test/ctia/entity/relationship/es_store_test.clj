(ns ctia.entity.relationship.es-store-test
  (:require [clojure.test :refer [deftest is testing are]]
            [ctia.entity.relationship.es-store :as sut]
            [schema.test :refer [validate-schemas]]
            [clojure.test :refer [use-fixtures]]))

(use-fixtures :once validate-schemas)

(def base-relationship
  {:id "http://localhost:3000/ctia/relationship/relationship-00000000-0000-0000-0000-000000000001"
   :type "relationship"
   :schema_version "1.1.0"
   :relationship_type "related-to"
   :source_ref "http://localhost:3000/ctia/malware/malware-00000000-0000-0000-0000-000000000002"
   :target_ref "http://localhost:3000/ctia/sighting/sighting-00000000-0000-0000-0000-000000000003"
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
      "http://localhost:3000/ctia/indicator/indicator-00000000-0000-0000-0000-000000000010"
      "http://localhost:3000/ctia/incident/incident-00000000-0000-0000-0000-000000000011"

      "judgement" "verdict"
      "http://localhost:3000/ctia/judgement/judgement-00000000-0000-0000-0000-000000000012"
      "http://localhost:3000/ctia/verdict/verdict-00000000-0000-0000-0000-000000000013"

      "attack-pattern" "vulnerability"
      "http://localhost:3000/ctia/attack-pattern/attack-pattern-00000000-0000-0000-0000-000000000014"
      "http://localhost:3000/ctia/vulnerability/vulnerability-00000000-0000-0000-0000-000000000015"

      "casebook" "investigation"
      "http://localhost:3000/ctia/casebook/casebook-00000000-0000-0000-0000-000000000016"
      "http://localhost:3000/ctia/investigation/investigation-00000000-0000-0000-0000-000000000017"))

  (testing "handles unparseable refs gracefully (no nil values added)"
    (let [rel (assoc base-relationship
                     :source_ref "not-a-valid-ctia-url"
                     :target_ref "also-invalid")
          result (sut/stored-relationship->es-stored-relationship rel)]
      ;; Should not contain :source_type or :target_type keys at all
      (is (not (contains? result :source_type)))
      (is (not (contains? result :target_type))))))

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
    (let [partial-rel {:id "http://localhost:3000/ctia/relationship/relationship-00000000-0000-0000-0000-000000000001"
                       :source_ref "http://localhost:3000/ctia/malware/malware-00000000-0000-0000-0000-000000000002"
                       :source_type "malware"
                       :target_type "sighting"}
          result (sut/es-partial-stored-relationship->partial-stored-relationship partial-rel)]
      (is (nil? (:source_type result)))
      (is (nil? (:target_type result)))
      (is (= (:source_ref partial-rel) (:source_ref result)))))

  (testing "handles partial without type fields"
    (let [partial-rel {:id "http://localhost:3000/ctia/relationship/relationship-00000000-0000-0000-0000-000000000001"
                       :source_ref "http://localhost:3000/ctia/malware/malware-00000000-0000-0000-0000-000000000002"}
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
