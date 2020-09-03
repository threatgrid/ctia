(ns ctia.test-helpers.migration
  (:require [ctia.task.migration.store :refer [MigrationStoreServices]]
            [ctia.test-helpers.core :as helpers]
            [ctia.store-service :as store-svc]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s]))

(s/defn app->MigrationStoreServices
  :- MigrationStoreServices
  [app]
  (let [store-svc (app/get-service app :StoreService)
        deref-stores (partial store-svc/deref-stores store-svc)
        get-in-config (helpers/current-get-in-config-fn app)]
    {:ConfigService {:get-in-config get-in-config}
     :StoreService {:deref-stores deref-stores}}))
