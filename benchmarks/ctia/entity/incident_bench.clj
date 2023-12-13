(ns ctia.entity.incident-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-es-store!]]
             [core :as helpers :refer [PATCH POST]]]
            [ctim.examples.incidents
             :refer [new-incident-minimal
                     new-incident-maximal]]
            [cemerick.uri :as uri]
            [perforate.core :refer :all]))

(def small-incident new-incident-minimal)

(defgoal patch-incident "Patch Incident"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [{:keys [app]}] (cleanup-ctia! app)))

(def headers {"Authorization" "45c1f5e3f05d0"})

(defn play [app endpoint patch]
  (let [{:keys [status]} (PATCH app
                                endpoint
                                :body patch
                                :headers headers)]
    (assert (= 200 status) status)))

(defcase patch-incident :empty-incident-patch-es-store
  [{:keys [app]}]
  (let [{{:keys [id]} :parsed-body :keys [status]}
        (POST app
              "ctia/incident"
              :body small-incident
              :query-params {:wait_for true}
              :headers {"Authorization" "45c1f5e3f05d0"})]
    (assert (= 201 status))
    (assert id)
    (play app
          (str "/ctia/incident/" (uri/uri-encode id))
          {})))

;; lein bench incident
;;
;; patch-incident
;; Goal:  Patch Incident
;; -----
;; Case:  :empty-incident-patch-es-store
;; Evaluation count : 120 in 60 samples of 2 calls.
;;              Execution time mean : 939.382994 ms
;;     Execution time std-deviation : 22.135350 ms
;;    Execution time lower quantile : 890.402209 ms ( 2.5%)
;;    Execution time upper quantile : 975.471646 ms (97.5%)
;; 
;; Found 4 outliers in 60 samples (6.6667 %)
;;         low-severe       3 (5.0000 %)
;;         low-mild         1 (1.6667 %)
;;  Variance from outliers : 11.0283 % Variance is moderately inflated by outliers

