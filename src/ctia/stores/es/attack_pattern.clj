(ns ctia.stores.es.attack-pattern
  (:require [ctia.stores.es.crud :as crud]
            [ctia.schemas.core :refer [StoredAttackPattern]]))

(def handle-create (crud/handle-create :attack-pattern StoredAttackPattern))
(def handle-read (crud/handle-read :attack-pattern StoredAttackPattern))
(def handle-update (crud/handle-update :attack-pattern StoredAttackPattern))
(def handle-delete (crud/handle-delete :attack-pattern StoredAttackPattern))
(def handle-list (crud/handle-find :attack-pattern StoredAttackPattern))
(def handle-query-string-search (crud/handle-query-string-search :attack-pattern StoredAttackPattern))
