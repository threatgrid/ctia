(ns ctia.stores.atom.event
  (:require [ctia.stores.atom.common :as mc]
            [ctim.events.schemas :refer [Event]]))

(def handle-create (mc/create-handler-from-realized Event))
(def handle-list (mc/list-handler Event))
