(ns ctia.stores.es.campaign
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [schema.coerce :as c]
   [ring.swagger.coerce :as sc]
   [ctia.schemas.campaign :refer [Campaign
                                  NewCampaign
                                  StoredCampaign
                                  realize-campaign]]
   [ctia.stores.es.document :refer [create-doc
                                    update-doc
                                    get-doc
                                    delete-doc
                                    search-docs]]))

(def ^{:private true} mapping "campaign")

(def coerce-stored-campaign
  (c/coercer! (s/maybe StoredCampaign)
              sc/json-schema-coercion-matcher))

(defn handle-create-campaign [state login realized-new-campaign]
  (-> (create-doc (:conn state)
                  (:index state)
                  mapping
                  realized-new-campaign)
      coerce-stored-campaign))

(defn handle-update-campaign [state id login new-campaign]
  (-> (update-doc (:conn state)
                  (:index state)
                  mapping
                  id
                  new-campaign)
      coerce-stored-campaign))

(defn handle-read-campaign [state id]
  (-> (get-doc (:conn state)
               (:index state)
               mapping
               id)
      coerce-stored-campaign))

(defn handle-delete-campaign [state id]
  (delete-doc (:conn state)
              (:index state)
              mapping
              id))

(defn handle-list-campaigns [state filter-map]
  (->> (search-docs (:conn state)
                    (:index state)
                    mapping
                    filter-map)
       (map coerce-stored-campaign)))
