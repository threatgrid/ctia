(ns ctia.stores.atom.verdict-test
  (:require [ctia.stores.atom.store :as as]
            [ctia.store :as store :refer [verdict-store]]
            [clojure.test :refer :all]
            [schema.test :as st]
            [schema-generators.generators :as g]
            [ctia.test-helpers.core :as test-helpers]
            [ctia.schemas.verdict :refer [Verdict StoredVerdict realize-verdict]])
  (:import [java.util Date]))

(use-fixtures :once st/validate-schemas)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
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
