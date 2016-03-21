(ns ctia.stores.es.campaign
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.schemas.campaign :refer [Campaign
                                  NewCampaign
                                  realize-campaign]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "campaign")

(defn- make-id [schema j]
  (str "campagin" "-" (UUID/randomUUID)))

(defn handle-create-campaign [state login new-campaign]
  (let [id (make-id Campaign new-campaign)
        realized (realize-campaign new-campaign id login)]
    (create-doc (:conn state)
                (:index state)
                mapping
                realized)))

(defn handle-update-campaign [state login id new-campaign]
  (update-doc (:conn state)
              (:index state)
              mapping
              id
              new-campaign))

(defn handle-read-campaign [state id]
  (get-doc (:conn state)
           (:index state)
           mapping
           id))

(defn handle-delete-campaign [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-campaigns [state filter-map]
  (search-docs (:conn state)
               (:index state)
               mapping
               filter-map))
