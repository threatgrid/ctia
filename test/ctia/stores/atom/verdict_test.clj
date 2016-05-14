(ns ctia.stores.atom.verdict-test
  (:require [ctia.stores.atom.store :as as]
            [ctia.store :as store :refer [verdict-store]]
            [clojure.test :refer :all]
            [schema.test :as st]
            [schema-generators.generators :as g]
            [ctia.test-helpers.core :as test-helpers]
            [ctia.schemas.verdict :refer [Verdict StoredVerdict realize-verdict]]))

(use-fixtures :once st/validate-schemas)
(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-properties:atom-store
                                    test-helpers/fixture-ctia-fast]))

(deftest store-test
  (let [verdicts (g/sample 5 StoredVerdict)]
    (doseq [v verdicts]
      (println "Realizing " v)
      (let [verdict (select-keys v [:type :disposition :judgement_id :disposition_name])
            realized-verdict (realize-verdict verdict (:owner v))]
        (store/create-verdict @verdict-store realized-verdict)))))
