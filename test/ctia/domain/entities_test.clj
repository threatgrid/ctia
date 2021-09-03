(ns ctia.domain.entities-test
  (:require [ctia.domain.entities :as sut]
            [ctim.examples.sightings :refer [sighting-minimal sighting-maximal]]
            [ctia.entity.sighting.schemas :as ss]
            [ctia.test-helpers.collections :refer [is-submap?]]
            [clojure.test :refer [deftest is testing]]))

(deftest simple-default-realize
  (let [services {:services
                  {:ConfigService
                   {:get-in-config
                    {:ctia
                     {:access-control {:default-tlp "green"}}}}}}
        new-sighting-minimal (assoc (dissoc sighting-minimal :id)
                                    :source "create")
        realized-create (sut/default-realize "sighting"
                                             ss/NewSighting
                                             new-sighting-minimal
                                             "sighting-id-1"
                                             {:login "g2"
                                              :groups ["iroh-services"]
                                              :client-id "ireaux"}
                                             nil
                                             services)]
    (testing "realized fn without previous object shall initiate all stored field"
      (is-submap? new-sighting-minimal realized-create)
      (is-submap? {:client_id "ireaux",
                   :schema_version "1.1.3",
                   :type "sighting",
                   :id "sighting-id-1",
                   :tlp "green",
                   :groups ["iroh-services"],
                   :owner "g2"}
                  realized-create)
      (is (some? (:created realized-create)))
      (is (= (:modified realized-create)
             (:created realized-create)
             (:timestamp realized-create))))

    (testing "realized fn with a previous object shall preserve previous stored field and update modified"
      (let [new-sighting-maximal (assoc (dissoc sighting-maximal :id)
                                        :source "update")
            realized-update (sut/default-realize "sighting"
                                                 ss/NewSighting
                                                 new-sighting-maximal
                                                 "sighting-id-2"
                                                 {:login "update-login"
                                                  :groups ["update-group"]
                                                  :client-id "iroh"}
                                                 realized-create
                                                 services)]
        (is-submap? new-sighting-maximal realized-update)
        (is-submap? {:client_id "ireaux",
                     :schema_version "1.1.3",
                     :type "sighting",
                     :id "sighting-id-2",
                     :tlp "amber",
                     :groups ["iroh-services"],
                     :owner "g2"}
                    realized-update)
        (is (some? (:created realized-update)))
        (is (some? (:modified realized-update)))
        (is (not= (:modified realized-update)
                  (:created realized-update)))
        (is (= (:created realized-update)
               (:created realized-create)
               (:timestamp realized-create)))))))
