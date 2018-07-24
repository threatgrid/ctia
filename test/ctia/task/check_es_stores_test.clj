(ns ctia.task.check-es-stores-test
  (:require [clj-http.client :as client]
            [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.properties :as props]
            [ctia.task.check-es-stores :as sut]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [post-bulk with-atom-logger]]
             [es :as es-helpers]
             [fake-whoami-service :as whoami-helpers]]
            [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-minimal]]
             [attack-patterns :refer [attack-pattern-minimal]]
             [campaigns :refer [campaign-minimal]]
             [casebooks :refer [casebook-minimal]]
             [coas :refer [coa-minimal]]
             [incidents :refer [incident-minimal]]
             [indicators :refer [indicator-minimal]]
             [investigations :refer [investigation-minimal]]
             [judgements :refer [judgement-minimal]]
             [malwares :refer [malware-minimal]]
             [relationships :refer [relationship-minimal]]
             [sightings :refer [sighting-minimal]]
             [tools :refer [tool-minimal]]
             [vulnerabilities :refer [vulnerability-minimal]]
             [weaknesses :refer [weakness-minimal]]]))

(use-fixtures :once
  (join-fixtures [mth/fixture-schema-validation
                  helpers/fixture-properties:clean
                  es-helpers/fixture-properties:es-store
                  helpers/fixture-ctia
                  whoami-helpers/fixture-server
                  es-helpers/fixture-delete-store-indexes]))

(use-fixtures :each
  whoami-helpers/fixture-reset-state)

(def fixtures-nb 100)

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(defn refresh-all-indices [host port]
  (client/post (format "http://%s:%s/_refresh" host port)))

(def examples
  {:actors (n-doc actor-minimal fixtures-nb)
   :attack_patterns (n-doc attack-pattern-minimal fixtures-nb)
   :campaigns (n-doc campaign-minimal fixtures-nb)
   :coas (n-doc coa-minimal fixtures-nb)
   :incidents (n-doc incident-minimal fixtures-nb)
   :indicators (n-doc indicator-minimal fixtures-nb)
   :investigations (n-doc investigation-minimal fixtures-nb)
   :judgements (n-doc judgement-minimal fixtures-nb)
   :malwares (n-doc malware-minimal fixtures-nb)
   :relationships (n-doc relationship-minimal fixtures-nb)
   :casebooks (n-doc casebook-minimal fixtures-nb)
   :sightings (n-doc sighting-minimal fixtures-nb)
   :tools (n-doc tool-minimal fixtures-nb)
   :vulnerabilities (n-doc vulnerability-minimal fixtures-nb)
   :weaknesses (n-doc weakness-minimal fixtures-nb)})

(deftest test-check-store-indexes
  (helpers/set-capabilities! "foouser"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0"
                                      "foouser"
                                      "foogroup"
                                      "user")
  (let [store-config (get-in @props/properties [:ctia :store :es :default])]
    (post-bulk examples)
    (refresh-all-indices (:host store-config)
                         (:port store-config))
    (testing "check ES Stores test setup"
      (testing "check ES indexes"
        (let [logger (atom [])]
          (with-atom-logger logger
            (doall (sut/check-store-indexes 100)))
          (testing "shall produce valid logs"
            (let [messages (set @logger)]
              (is (contains? messages "set batch size: 100"))
              (is (clojure.set/subset?
                   ["campaign - finished checking 100 documents"
                    "indicator - finished checking 100 documents"
                    "event - finished checking 1500 documents"
                    "actor - finished checking 100 documents"
                    "relationship - finished checking 100 documents"
                    "incident - finished checking 100 documents"
                    "investigation - finished checking 100 documents"
                    "coa - finished checking 100 documents"
                    "judgement - finished checking 100 documents"
                    "data-table - finished checking 0 documents"
                    "feedback - finished checking 0 documents"
                    "casebook - finished checking 100 documents"
                    "sighting - finished checking 100 documents"
                    "attack-pattern - finished checking 100 documents"
                    "malware - finished checking 100 documents"
                    "tool - finished checking 100 documents"
                    "vulnerability - finished checking 100 documents"
                    "weakness - finished checking 100 documents"]
                   messages)))))))))
