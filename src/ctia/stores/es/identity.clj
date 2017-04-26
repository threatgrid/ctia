(ns ctia.stores.es.identity
  (:require [clj-momo.lib.es.document :refer [create-doc delete-doc get-doc]]
            [ctia.schemas.identity :refer [Identity]]
            [schema.core :as s]))

(def ^{:private true} mapping "identity")

(defn capabilities->capabilities-set [caps]
  "transform a vec of capabilities from es
   to a set of keywords"
  (set (map #(-> % keyword) caps)))

(defn capabilities-set->capabilities [caps]
  "transform a set of capabilities
   into a vec of strings"
  (vec (map name caps)))

(defn handle-create [state new-identity]
  (let [id (:login new-identity)
        realized (assoc new-identity :id id)
        transformed (update-in realized [:capabilities]
                               capabilities-set->capabilities)

        res (create-doc (:conn state)
                        (:index state)
                        mapping
                        transformed
                        (get-in state [:props :refresh] false))]
    (-> res
        (update-in [:capabilities] capabilities->capabilities-set)
        (dissoc :id))))

(s/defn handle-read :- (s/maybe Identity)
  [state :- s/Any login :- s/Str]
  (some-> (get-doc (:conn state)
                   (:index state)
                   mapping
                   login)
          (update-in [:capabilities] capabilities->capabilities-set)
          (dissoc :id)))

(defn handle-delete [state login]
  (delete-doc (:conn state)
              (:index state)
              mapping
              login))
