(ns ctia.task.migration.store-test
  (:require [clojure.test :refer [deftest is testing]]

            [clj-momo.lib.es
             [conn :refer [connect]]
             [document :as es-doc]
             [index :as es-index]]
            [ctim.examples
             [malwares :refer [malware-minimal]]
             [relationships :refer [relationship-minimal]]
             [tools :refer [tool-minimal]]]

            [ctia.task.migration
             [fixtures :refer [examples fixtures-nb]]
             [store :as sut]]
            [ctia.properties :as props]
            ))

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
      (let [{:keys [source target started completed]} (get stores entity-type)]
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
               (get-in target [:store :indexname])))))))


;; TODO test others ns functions
;; note that this ns is already partially tested in migrate-es-stores-test
;; however it should more deeply test different functions
