(ns ctia.stores.atom.verdict-test
  (:require [ctia.stores.atom.store :as as]
            [ctia.store :as store :refer [verdict-store judgement-store]]
            [clojure.test :refer :all]
            [schema.test :as st]
            [schema-generators.generators :as g]
            [ctia.test-helpers.core :as test-helpers :refer [post]]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.schemas.verdict :refer [Verdict StoredVerdict realize-verdict]]
            [ctia.schemas.judgement :refer [realize-judgement]])
  (:import [java.util Date]))

(use-fixtures :once (join-fixtures [st/validate-schemas
                                    test-helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each (join-fixtures [whoami-helpers/fixture-reset-state
                                    test-helpers/fixture-properties:atom-store
                                    test-helpers/fixture-ctia-fast]))

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
        (store/create-verdict @verdict-store realized-verdict)))))

(deftest read-test
  (let [verdicts (g/sample 5 StoredVerdict)]
    (doseq [v verdicts]
      (let [verdict (select-keys v [:type :disposition :judgement_id :disposition_name])
            realized-verdict (realize-verdict verdict (:owner v))
            {id :id} (store/create-verdict @verdict-store realized-verdict)
            {created :created :as read-verdict} (store/read-verdict @verdict-store id)]
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
        _ (println "Creating judgement")
        response (post "ctia/judgement"
                       :body judgement
                       :headers {"api_key" "45c1f5e3f05d0"})]
    (println "Response: " response)
    (println "VERDICTS: " @verdict-store)))
