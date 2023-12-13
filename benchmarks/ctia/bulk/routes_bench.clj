(ns ctia.bulk.routes-bench
  "Benchmark could be launched by `lein bench bulk`"
  (:require [ctia.test-helpers.benchmark :refer [cleanup-ctia! setup-ctia-es-store!]]
            [ctia.test-helpers.core :as helpers :refer [POST]]
            [perforate.core :refer [defgoal defcase]]
            [schema.core :as s]))

(def empty-bulk {})

(defgoal post-bulk "Post bulk"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [{:keys [app]}] (cleanup-ctia! app)))

(defn play [app b]
  (let [{:keys [status] :as resp}
        (POST app
              "ctia/bulk"
              :body b
              :headers {"Authorization" "45c1f5e3f05d0"})]
    (assert (= 201 status) (pr-str resp))))

(defcase post-bulk :empty-bulk-es-store
  [{:keys [app]}] (play app empty-bulk))

;; lein bench bulk 
;;
;; Goal:  Post bulk
;; -----
;; Case:  :empty-bulk-es-store
;; Evaluation count : 87240 in 60 samples of 1454 calls.
;;              Execution time mean : 2.126299 ms
;;     Execution time std-deviation : 4.006752 ms
;;    Execution time lower quantile : 637.405691 µs ( 2.5%)
;;    Execution time upper quantile : 14.418208 ms (97.5%)
;; 
;; Found 7 outliers in 60 samples (11.6667 %)
;;         low-severe       1 (1.6667 %)
;;         low-mild         6 (10.0000 %)
;;  Variance from outliers : 98.3262 % Variance is severely inflated by outliers
