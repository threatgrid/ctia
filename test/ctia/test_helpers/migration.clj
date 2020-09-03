(ns ctia.test-helpers.migration
  (:require [ctia.task.migration.store :refer [MigrationStoreServices]]
            [ctia.test-helpers.core :as helpers]
            [ctia.store-service :as store-svc]
            [schema.core :as s]))

(s/defn app->MigrationStoreServices
  :- MigrationStoreServices
  [app]
  {:ConfigService (-> (helpers/get-service-map app :ConfigService)
                      (select-keys [:get-in-config]))
   :StoreService (-> (helpers/get-service-map app :StoreService)
                     (select-keys [:deref-stores]))})
