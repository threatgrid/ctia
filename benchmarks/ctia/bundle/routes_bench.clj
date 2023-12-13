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
;; Evaluation count : 110460 in 60 samples of 1841 calls.
;;              Execution time mean : 1.854401 ms
;;     Execution time std-deviation : 2.564676 ms
;;    Execution time lower quantile : 631.847954 µs ( 2.5%)
;;    Execution time upper quantile : 8.998960 ms (97.5%)
;; 
;; Found 7 outliers in 60 samples (11.6667 %)
;;         low-severe       7 (11.6667 %)
;;  Variance from outliers : 98.3202 % Variance is severely inflated by outliers


(defn play [app fixture]
  (POST app
        "ctia/bundle/import"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defcase import-bundle :empty-bundle-import-es-store
  [{:keys [app]}] (play app empty-bundle))
