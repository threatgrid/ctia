(ns ctia.http.generative.specs
  (:refer-clojure :exclude [get])
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as tcg]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers.core
             :refer [common= post get encode normalize]]
            [ctim.generators.schemas :as gen]
            [ctim.generators.schemas.sighting-generators :as gs]))

(defn assert-successfully-created
  ([status]
   (assert (= 201 status)
           (format "status %s was not 201" status)))
  ([status body]
   (assert (empty? (:errors body))
           (format (str "Errors in the body: " (:errors body))))
   (assert (= 201 status)
           (format "Status %s was not 201" status))))

(defn assert-successful
  ([status]
   (assert (= 200 status)
           (format "Status %s was not 200" status)))
  ([status body]
   (assert (empty? (:errors body))
           (format (str "Errors in the body: " (:errors body))))
   (assert (= 200 status)
           (format "Status %s was not 200" status))))

(defn get-http-params []
  (get-in @properties [:ctia :http :show]))

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

         (assert-successfully-created post-status# post-entity#)
         (assert-successful get-status# get-entity#)
         (if-not (empty? (keys new-entity#))
           (common= new-entity#
                    (normalize post-entity#)
                    (normalize get-entity#))
           (common= (normalize post-entity#)
                    (normalize get-entity#)))))))

(def-property spec-actor-routes 'actor)
(def-property spec-campaign-routes 'campaign)
(def-property spec-coa-routes 'coa)
(def-property spec-exploit-target-routes 'exploit-target)

(def spec-indicator-routes
  (for-all [[new-indicator new-sightings]
            (gen/gen-new-indicator-with-new-sightings get-http-params)]
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

      (assert-successfully-created post-status)
      (doseq [{status :status} stored-sighting-responses]
        (assert-successfully-created status))
      (assert-successful get-indicator-status)
      (assert-successful search-result-status)

      (and
       ;; Sometimes the indicator ID is reused multiple times during a
       ;; test run (with no store cleanup in between).  Use subset? to
       ;; make sure that extra results don't fail the test.  (See
       ;; issue #328).
       (set/subset? stored-sighting-ids
                    search-result-ids)
       (common= new-indicator
                (normalize post-indicator)
                (normalize get-indicator))))))

(def-property spec-feedback-routes 'feedback)
(def-property spec-incident-routes 'incident)
(def-property spec-judgement-routes 'judgement)
(def-property sepc-sighting-routes 'sighting)
(def-property spec-ttp-routes 'ttp)
