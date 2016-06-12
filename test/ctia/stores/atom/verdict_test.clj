(ns ctia.stores.atom.verdict-test
  (:require [ctia.stores.atom.store :as as]
            [ctia.store :as store]
            [ctia.domain.entities :as entities :refer [realize-verdict realize-judgement]]
            [clojure.test :refer :all]
            [clojure.edn :as edn]
            [schema.test :as st]
            [schema-generators.generators :as g]
            [ctia.test-helpers.core :as test-helpers :refer [post]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctim.schemas.verdict :refer [Verdict StoredVerdict]])
  (:import [java.util Date]))

(use-fixtures :once (join-fixtures [st/validate-schemas
                                    test-helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each (join-fixtures [whoami-helpers/fixture-reset-state
                                    test-helpers/fixture-properties:atom-store
                                    test-helpers/fixture-ctia]))

(defn- test-equiv
  [a b]
  (let [a' (dissoc a :created :id)
        b' (dissoc b :created :id :owner)]
    (is (= a' b'))))

(def one-second 1000)

(defn time-since
  [^Date d]
  (- (.getTime (Date.)) (.getTime d)))

(deftest store-test
  (let [verdicts (g/sample 5 StoredVerdict)]
    (doseq [v verdicts]
      (let [verdict (select-keys v [:type :disposition :judgement_id :disposition_name])
            realized-verdict (realize-verdict verdict (:owner v))]
        (store/write-store :verdict store/create-verdict realized-verdict)))))

(deftest read-test
  (let [verdicts (g/sample 5 StoredVerdict)]
    (doseq [v verdicts]
      (let [verdict (select-keys v [:type :disposition :judgement_id :disposition_name])
            realized-verdict (realize-verdict verdict (:owner v))
            {id :id} (store/write-store :verdict store/create-verdict realized-verdict)
            {created :created :as read-verdict} (store/read-store :verdict store/read-verdict id)]
        (test-equiv verdict read-verdict)
        (is (> one-second (time-since created)))))))



(deftest calculate-test
  (test-helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")
  (let [observable {:value "http://observation.org/"
                    :type "url"}
        judgement {:observable observable
                   :disposition 3
                   :source "test"
                   :priority 50
                   :confidence "Low"
                   :severity 100
                   :valid_time {:start_time "1971-01-01T00:00:00.000-00:00"}
                   :tlp "yellow"}
        {body :body :as response} (post "ctia/judgement"
                                        :body judgement
                                        :headers {"api_key" "45c1f5e3f05d0"})
        {j-id :id :as new-judgement} (edn/read-string body)
        verdict-id (str "verdict-" (subs j-id 10))
        verdict (store/read-store :verdict store/read-verdict verdict-id)
        verdict' (dissoc verdict :created)]
    (is (= {:type "verdict"
            :disposition 3
            :judgement_id j-id
            :disposition_name "Suspicious"
            :id verdict-id
            :owner "foouser"}
           verdict'))))
