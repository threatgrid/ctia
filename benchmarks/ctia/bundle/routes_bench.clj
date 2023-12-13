(ns ctia.bundle.routes-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-es-store!]]
             [core :as helpers :refer [POST]]]
            [ctim.examples.bundles
             :refer [new-bundle-minimal]]
            [perforate.core :refer :all]))

(def empty-bundle new-bundle-minimal)

(defgoal import-bundle "Import Bundle"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [{:keys [app]}] (cleanup-ctia! app)))

;; lein bench bundle
;;
;; Goal:  Import Bundle
;; -----
;; Case:  :empty-bundle-import-es-store
;; Evaluation count : 1200 in 60 samples of 20 calls.
;;              Execution time mean : 55.115812 ms
;;     Execution time std-deviation : 4.980215 ms
;;    Execution time lower quantile : 52.074792 ms ( 2.5%)
;;    Execution time upper quantile : 64.366600 ms (97.5%)
;; 
;; Found 2 outliers in 60 samples (3.3333 %)
;;         low-severe       2 (3.3333 %)
;;  Variance from outliers : 65.2491 % Variance is severely inflated by outliers


(defn play [app fixture]
  (let [{:keys [status] :as resp} (POST app
                                        "ctia/bundle/import"
                                        :body fixture
                                        :headers {"Authorization" "45c1f5e3f05d0"})]
    (assert (= 201 status) (pr-str resp))(assert )))

(defcase import-bundle :empty-bundle-import-es-store
  [{:keys [app]}] (play app empty-bundle))
