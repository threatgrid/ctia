(ns ctia.entity.campaign-bench
  (:require
   [ctim.examples.campaigns :refer [new-campaign-minimal
                                    new-campaign-maximal]]
   [ctia.test-helpers
    [benchmark :refer [cleanup-ctia!
                       setup-ctia-es-store!]]
    [core :as helpers :refer [POST]]]
   [perforate.core :refer :all]))

(def small-campaign
  new-campaign-minimal)

(def big-campaign
  (dissoc new-campaign-maximal :id))

(defgoal create-campaign "Create Campaign"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [{:keys [app]}] (cleanup-ctia! app)))

(defn play [app fixture]
  (POST app
        "ctia/campaign"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defcase create-campaign :big-campaign-es-store
  [{:keys [app]}] (play app big-campaign))

(defcase create-campaign :small-campaign-es-store
  [{:keys [app]}] (play app small-campaign))

;; lein bench campaign
;;
;; create-campaign
;; Goal:  Create Campaign
;; -----
;; Case:  :big-campaign-es-store
;; Evaluation count : 10380 in 60 samples of 173 calls.
;;              Execution time mean : 6.625116 ms
;;     Execution time std-deviation : 1.381886 ms
;;    Execution time lower quantile : 5.072085 ms ( 2.5%)
;;    Execution time upper quantile : 10.352341 ms (97.5%)
;; 
;; Found 2 outliers in 60 samples (3.3333 %)
;;         low-severe       2 (3.3333 %)
;;  Variance from outliers : 91.1637 % Variance is severely inflated by outliers
;; 
;; Case:  :small-campaign-es-store
;; Evaluation count : 14760 in 60 samples of 246 calls.
;;              Execution time mean : 4.696163 ms
;;     Execution time std-deviation : 396.121872 µs
;;    Execution time lower quantile : 4.052968 ms ( 2.5%)
;;    Execution time upper quantile : 5.515621 ms (97.5%)
;; 
;; Found 1 outliers in 60 samples (1.6667 %)
;;         low-severe       1 (1.6667 %)
;;  Variance from outliers : 61.8652 % Variance is severely inflated by outliers
