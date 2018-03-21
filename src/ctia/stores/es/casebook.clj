(ns ctia.stores.es.casebook
  (:require [ctia.schemas.core
             :refer [StoredCasebook PartialStoredCasebook]]
            [ctia.stores.es.crud :as crud]))

(def handle-create (crud/handle-create :casebook StoredCasebook))
(def handle-read (crud/handle-read :casebook PartialStoredCasebook))
(def handle-update (crud/handle-update :casebook StoredCasebook))
(def handle-delete (crud/handle-delete :casebook StoredCasebook))
(def handle-list (crud/handle-find :casebook PartialStoredCasebook))
(def handle-query-string-search (crud/handle-query-string-search :casebook PartialStoredCasebook))

(def ^{:private true} mapping "casebook")
