(ns ctia.task.settings-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ctia.task.settings :as sut]
            [ctia.stores.es.init :as init]
            [clj-momo.test-helpers.core :as mth]
            [ductile
             [index :as es-index]
             [conn :as es-conn]]
            [ctia.test-helpers
             [core :as h :refer [fixture-ctia]]
             [es :as es-helpers :refer [fixture-properties:es-store fixture-delete-store-indexes]]]))

(defn fixture-update-stores [t]
  (h/with-config-transformer*
    #(-> %
         (assoc-in [:ctia :store :es :relationship :replicas]
                   12)
         (assoc-in [:ctia :store :es :malware :refresh_interval]
                   "12s")
         (assoc-in [:ctia :store :es :indicator :refresh_interval]
                   "12s"))
    t))

(use-fixtures :each 
              fixture-properties:es-store
              fixture-update-stores
              fixture-ctia
              fixture-delete-store-indexes)
(use-fixtures :once mth/fixture-schema-validation)

(defn get-setting
  "helper for retrieving a setting inside an index/template ES res"
  [es-res setting]
  (-> (first es-res)
      val
      (get-in [:settings :index setting])))

(deftest update-stores!-test
  (let [app (h/get-current-app)
        {:keys [get-in-config]} (h/get-service-map app :ConfigService)

        initial-indicator-props (init/get-store-properties :indicator get-in-config)
        _ (sut/update-stores! [:relationship :malware] (es-helpers/app->ESConnServices app))
        es-props (get-in-config [:ctia :store :es])
        conn (es-conn/connect (:default es-props))
        relationship-indexname (get-in es-props [:relationship :indexname])
        relationship-index (es-index/get conn (str relationship-indexname "*"))
        relationship-template (es-index/get-template conn relationship-indexname)

        malware-indexname (get-in es-props [:malware :indexname])
        malware-index (es-index/get conn (str malware-indexname "*"))
        malware-template (es-index/get-template conn malware-indexname)

        indicator-indexname (get-in es-props [:indicator :indexname])
        indicator-index (es-index/get conn (str indicator-indexname "*"))
        indicator-template (es-index/get-template conn indicator-indexname)]
    (testing "stores that are passed to update-stores! should have their settings updated"
      (is (= "12"
             (get-setting relationship-index :number_of_replicas)
             (get-setting relationship-template :number_of_replicas)))
      (is (= "12s"
             (get-setting malware-index :refresh_interval)
             (get-setting malware-template :refresh_interval))))

    (testing "stores that are not passed to update-stores! should not have their settings updated"
      (is (= "12s"
             (:refresh_interval initial-indicator-props)
             (get-setting indicator-index :refresh_interval)
             (get-setting indicator-template :refresh_interval)))
      (is (= "1"
             (str (:replicas initial-indicator-props))
             (get-setting indicator-index :number_of_replicas)
             (get-setting indicator-template :number_of_replicas))))))
