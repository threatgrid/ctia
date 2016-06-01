(ns ctia.lib.keyword-test
  (:require [ctia.lib.keyword :refer [singular]]
            [clojure.test :refer [deftest is]]))

(deftest testing-singular
  (is (= :actor (singular :actors)))
  (is (= :campaign (singular :campaigns)))
  (is (= :coa (singular :coas)))
  (is (= :exploit-target (singular :exploit-targets)))
  (is (= :feedback (singular :feedbacks)))
  (is (= :incident (singular :incidents)))
  (is (= :indicator (singular :indicators)))
  (is (= :judgement (singular :judgements)))
  (is (= :sighting (singular :sightings)))
  (is (= :ttp (singular :ttps))))
