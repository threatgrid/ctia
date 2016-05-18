(ns ctia.http.generative.specs
  (:refer-clojure :exclude [get])
  (:require [clojure.string :as str]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as tcg]
            [ctia.test-helpers.core
             :refer [common= post get encode normalize]]
            [ctia.test-helpers.generators.schemas :as gen]
            [ctia.test-helpers.generators.schemas.sighting-generators :as gs]))

(defn assert-successful
  [status]
  (assert (= 200 status)
          (format "Status %s was not 200" status)))

(defmacro def-property [name model-type]
  `(def ~name
     (for-all [new-entity# (gen/gen-entity (keyword (str "new-" ~model-type)))]
       (let [{post-status# :status
              {id# :id, :as post-entity#} :parsed-body}
             (post (str "ctia/" ~model-type)
                   :body new-entity#)

             {get-status# :status
              get-entity# :parsed-body}
             (get (str "ctia/" ~model-type "/" (encode id#)))]

         (assert-successful post-status#)
         (assert-successful get-status#)

         (common= new-entity#
                  (normalize post-entity#)
                  (normalize get-entity#))))))

(def-property spec-actor-routes 'actor)
(def-property spec-campaign-routes 'campaign)
(def-property spec-coa-routes 'coa)
(def-property spec-exploit-target-routes 'exploit-target)

(def-property spec-indicator-routes 'indicator)
#_(def spec-indicator-routes
    (for-all [[new-indicator new-sightings] gen/gen-new-indicator-with-new-sightings]
             (let [{post-status :status
                    {id :id, :as post-indicator} :parsed-body}
                   (post "ctia/indicator"
                         :body new-indicator)

                   {get-indicator-status :status
                    get-indicator :parsed-body}
                   (get (str "ctia/indicator/" (encode id)))

                   stored-sighting-responses
                   (map #(post "ctia/sighting"
                               :body %)
                        new-sightings)

                   stored-sighting-ids
                   (->> stored-sighting-responses
                        (map (comp :id :parsed-body))
                        set)

                   {search-result-status :status
                    search-results :parsed-body
                    :as search-response}
                   (get (str "ctia/indicator/" (encode id) "/sightings"))

                   search-result-ids
                   (->> search-results
                        (map :id)
                        set)]

               (assert-successful post-status)
               (doseq [{status :status} stored-sighting-responses]
                 (assert-successful status))
               (assert-successful get-indicator-status)
               (assert-successful search-result-status)

               (and
                (= stored-sighting-ids
                   search-result-ids)
                (common= new-indicator
                         (normalize post-indicator)
                         (normalize get-indicator))))))

(def-property spec-feedback-routes 'feedback)
(def-property spec-incident-routes 'incident)
(def-property spec-judgement-routes 'judgement)
(def-property sepc-sighting-routes 'sighting)
(def-property spec-ttp-routes 'ttp)
