(ns ctia.tasks.migrate-es-stores-bench
  (:require [ctia.task.migrate-es-stores :as sut]
            [ctia.test-helpers
             [core :refer [with-properties]]
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-es-store!
                                delete-store-indexes]]
             [core :as helpers :refer [post-bulk]]]
            [ctim.domain.id :refer [make-transient-id]]
            [ctim.examples
             [actors :refer [actor-maximal]]
             [attack-patterns :refer [attack-pattern-maximal]]
             [campaigns :refer [campaign-maximal]]
             [casebooks :refer [casebook-maximal]]
             [coas :refer [coa-maximal]]
             [incidents :refer [incident-maximal]]
             [indicators :refer [indicator-maximal]]
             [investigations :refer [investigation-maximal]]
             [judgements :refer [judgement-maximal]]
             [malwares :refer [malware-maximal]]
             [relationships :refer [relationship-maximal]]
             [sightings :refer [sighting-maximal]]
             [tools :refer [tool-maximal]]]
            [perforate.core :refer [defcase defgoal]]))

(defn randomize [doc]
  (assoc doc
         :id (make-transient-id "_")))

(defn n-doc [fixture nb]
  (map randomize (repeat nb fixture)))

(defn make-examples [fixtures-nb]
  {:actors (n-doc actor-maximal fixtures-nb)
   :attack_patterns (n-doc attack-pattern-maximal fixtures-nb)
   :campaigns (n-doc campaign-maximal fixtures-nb)
   :coas (n-doc coa-maximal fixtures-nb)
   :incidents (n-doc incident-maximal fixtures-nb)
   :indicators (n-doc indicator-maximal fixtures-nb)
   :investigations (n-doc investigation-maximal fixtures-nb)
   :judgements (n-doc judgement-maximal fixtures-nb)
   :malwares (n-doc malware-maximal fixtures-nb)
   :relationships (n-doc relationship-maximal fixtures-nb)
   :casebooks (n-doc casebook-maximal fixtures-nb)
   :sightings (n-doc sighting-maximal fixtures-nb)
   :tools (n-doc tool-maximal fixtures-nb)})

(defn post-fixtures []
  (dotimes [n 10]
    (println "posting bulk...")
    (post-bulk (make-examples 1000))))

(defgoal migrate-store-indexes "Run a migration"
  :setup (fn [& args]
           (println "launching CTIA...")
           (setup-ctia-es-store!)

           (println "clear ES stores")
           (delete-store-indexes)

           (println "posting fixtures...")
           (post-fixtures)
           (prn "fixtures loaded!")
           [true])
  :cleanup (fn [& args]
             (println "cleanup and exit CTIA...")
             (cleanup-ctia!)))

(defn run-migrate-es-stores []
  (with-redefs [sut/exit (fn [_] nil)]
    (sut/run-migration "0.0.0"
                       [:__test]
                       1000
                       true)))

(defcase migrate-store-indexes :base
  [_] (run-migrate-es-stores))

(defcase migrate-store-indexes :optimizations_disabled
  [_] (with-properties ["ctia.migration.es/indexname" "ctia_migration"]
        (run-migrate-es-stores)))



