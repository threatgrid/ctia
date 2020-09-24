(ns ctia.lib.keyword-test
  (:require [ctia.lib.keyword :refer [singular]]
            [clojure.test :refer [deftest is are]]))

(deftest testing-singular
  (are [singular* plural] (= singular* (singular plural))
    :actor              :actors
    :campaign           :campaigns
    :coa                :coas
    :feedback           :feedbacks
    :incident           :incidents
    :indicator          :indicators
    :judgement          :judgements
    :sighting           :sightings
    :identity_assertion :identity_assertions
    :target_record      :target_records
    :ttp                :ttps))
