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
        realized-create (sut/default-realize {:type-name "sighting"
                                              :Model ss/NewSighting
                                              :new-object new-sighting-minimal
                                              :id "sighting-id-1"
                                              :ident-map {:login "g2"
                                                          :groups ["iroh-services"]
                                                          :client-id "ireaux"}
                                              :prev-object nil
                                              :services services})]
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
    (Thread/sleep 1) ;; allow time to pass so :modified != :created below
    (testing "realized fn with a previous object shall preserve previous stored field and update modified"
      (let [new-sighting-maximal (assoc (dissoc sighting-maximal :id)
                                        :source "update")
            realized-update (sut/default-realize {:type-name "sighting"
                                                  :Model ss/NewSighting
                                                  :new-object new-sighting-maximal
                                                  :id "sighting-id-2"
                                                  :ident-map {:login "update-login"
                                                              :groups ["update-group"]
                                                              :client-id "iroh"}
                                                  :prev-object realized-create
                                                  :services services})]
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

(deftest un-store-test
  (is (= (sut/un-store
           {:foo 1
            :created 2
            :modified 3
            :client_id 4})
         {:foo 1}))
  (doseq [config [nil {} {:keep-client_id false}]]
    (testing (pr-str config)
      (is (= (sut/un-store
               {:foo 1
                :created 2
                :modified 3
                :client_id 4}
               config)
             {:foo 1}))))
  (is (= (sut/un-store
           {:foo 1
            :created 2
            :modified 3
            :client_id 4}
           {:keep-client_id true})
         {:foo 1
          :client_id 4})))
