(ns cia.stores.es.campaign
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [cia.stores.es.index :refer [es-conn index-name]]
   [cia.schemas.campaign :refer [Campaign
                                 NewCampaign
                                 realize-campaign]]
   [cia.stores.es.document :refer [create-doc
                                   update-doc
                                   get-doc
                                   delete-doc
                                   search-docs]]))

(def ^{:private true} mapping "campaign")

(defn- make-id [schema j]
  (str "campagin" "-" (UUID/randomUUID)))

(defn handle-create-campaign [state new-campaign]
  (let [id (make-id Campaign new-campaign)
        realized (realize-campaign new-campaign id)]
    (create-doc es-conn index-name mapping realized)))

(defn handle-update-campaign [state id new-campaign]
  (update-doc
   es-conn
   index-name
   mapping
   id
   new-campaign))

(defn handle-read-campaign [state id]
  (get-doc es-conn index-name mapping id))

(defn handle-delete-campaign [state id]
  (delete-doc es-conn index-name mapping id))

(defn handle-list-campaigns [state filter-map]
  (search-docs es-conn index-name mapping filter-map))
