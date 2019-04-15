(ns ctia.task.migration.store-test
  (:require [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]

            [clj-momo.test-helpers.core :as mth]
            [clj-momo.lib.es
             [conn :refer [connect]]
             [document :as es-doc]
             [index :as es-index]]
            [ctim.examples
             [malwares :refer [malware-minimal]]
             [relationships :refer [relationship-minimal]]
             [tools :refer [tool-minimal]]]

            [ctia.test-helpers
             [core :as helpers :refer [post-bulk]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.task.migration
             [fixtures :refer [examples fixtures-nb]]
             [store :as sut]]
            [ctia.properties :as props]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  whoami-helpers/fixture-server
                  es-helpers/fixture-delete-store-indexes]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(deftest prefixed-index-test
  (is (= "v0.4.2_ctia_actor"
         (sut/prefixed-index "ctia_actor" "0.4.2")))
  (is (= "v0.4.2_ctia_actor"
         (sut/prefixed-index "v0.4.1_ctia_actor" "0.4.2"))))

(deftest target-index-settings-test
  (is (= {:index {:number_of_replicas 0
                  :refresh_interval -1
                  :mapping {}}}
         (sut/target-index-settings {:mapping {}})))
  (is (= {:index {:number_of_replicas 0
                  :refresh_interval -1}}
         (sut/target-index-settings {}))))

(sut/setup!)
(def es-props (get-in @props/properties [:ctia :store :es]))
(def es-conn (connect (:default es-props)))
(def migration-index (get-in es-props [:migration :indexname]))

(deftest init-migration-test
  (testing "init-migration should properly create new migration state from selected types"
    (post-bulk examples)
    (Thread/sleep 1000) ; ensure index refresh
    (let [prefix "0.0.0"
          entity-types [:tool :malware :relationship]
          migration-id-1 "migration-1"
          {:keys [id created stores]} (sut/init-migration migration-id-1
                                                          prefix
                                                          entity-types
                                                          false)]
      (is (= id migration-id-1))
      (is (= (set (keys stores))
             (set entity-types)))
      (doseq [entity-type entity-types]
        ;(println entity-type)
        (let [{:keys [source target started completed]} (get stores entity-type)]
          ;(println source)
          (is (nil? started))
          (is (nil? completed))
          (is (= 0 (:migrated target)))
          (is (= fixtures-nb (:total source)))
          (is (nil? (:started source)))
          (is (nil? (:completed target)))
          (is (= entity-type
                 (keyword (get-in source [:store :type]))))
          (is (= entity-type
                 (keyword (get-in target [:store :type]))))
          (is (= (:index source)
                 (get-in source [:store :indexname])))
          (is (= (:index target)
                 (get-in target [:store :indexname]))))))
    (es-index/delete! es-conn "ctia_*")))


;; TODO test others ns functions
;; note that this ns is already partially tested in migrate-es-stores-test
;; however it should more deeply test different functions
