(ns ctia.task.migrate-es-stores-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.task.migrate-es-stores :as sut]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctim.examples
             [actors :refer [actor-maximal]]
             [campaigns :refer [campaign-maximal]]
             [coas :refer [coa-maximal]]
             [exploit-targets :refer [exploit-target-maximal]]
             [incidents :refer [incident-maximal]]
             [indicators :refer [indicator-maximal]]
             [judgements :refer [judgement-maximal]]
             [relationships :refer [relationship-maximal]]
             [sightings :refer [sighting-maximal]]
             [ttps :refer [ttp-maximal]]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  whoami-helpers/fixture-server
                  es-helpers/fixture-delete-store-indexes]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(def fixtures-nb 200)

(defn post-bulk [examples]
  (let [{status :status
         bulk-res :parsed-body}
        (post "ctia/bulk"
              :body examples
              :headers {"Authorization" "45c1f5e3f05d0"})]
    (is (= 201 status))))

(defn randomize [doc]
  (assoc doc
         :id (str "transient:"
                  (str (java.util.UUID/randomUUID)))))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(def examples
  {:actors (n-doc actor-maximal fixtures-nb)
   :campaigns (n-doc campaign-maximal fixtures-nb)
   :coas (n-doc coa-maximal fixtures-nb)
   :exploit-targets (n-doc exploit-target-maximal fixtures-nb)
   :incidents (n-doc incident-maximal fixtures-nb)
   :indicators (n-doc indicator-maximal fixtures-nb)
   :judgements (n-doc judgement-maximal fixtures-nb)
   :relationships (n-doc relationship-maximal fixtures-nb)
   :sightings (n-doc sighting-maximal fixtures-nb)
   :ttps (n-doc ttp-maximal fixtures-nb)})

(deftest test-migrate-store-indexes
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
      (sut/migrate-store-indexes "foo"
                                 "add-groups,fix-end-time"
                                 100
                                 false))
    (testing "migrate es indexes"
      (sut/migrate-store-indexes "foo"
                                 "add-groups,fix-end-time"
                                 100
                                 true))))
(deftest add-groups-test
  (is (= (transduce sut/add-groups conj [{}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups []}])
         [{:groups ["tenzin"]}]))
  (is (= (transduce sut/add-groups conj [{:groups ["foo"]}])
         [{:groups ["foo"]}])))

(deftest fix-end-time-test
  (is (= (transduce sut/fix-end-time conj [{}]) [{}]))
  (is (= (transduce sut/fix-end-time conj [{:valid_time
                                            {:start_time "foo"}}])
         [{:valid_time
           {:start_time "foo"}}]))
  (is (= (transduce sut/fix-end-time conj
                    [{:valid_time
                      {:end_time "2535"}}])
         [{:valid_time
           {:end_time "2525"}}])))
