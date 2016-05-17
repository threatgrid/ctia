(ns ctia.stores.es.identity
  (:import java.util.UUID)
  (:require
   [schema.core :as s]
   [ctia.schemas.identity :refer [Identity]]
   [ctia.lib.es.document :refer [create-doc
                                 get-doc
                                 delete-doc]]))

(def ^{:private true} mapping "identity")

(defn capabilities->capabilities-set [caps]
  "transform a vec of capabilities from es
   to a set of keywords"
  (set (map #(-> % keyword) caps)))

(defn capabilities-set->capabilities [caps]
  "transform a set of capabilities
   into a vec of strings"
  (vec (map name caps)))

(defn handle-create-identity [state new-identity]
  (let [id (:login new-identity)
        realized (assoc new-identity :id id)
        transformed (update-in realized [:capabilities]
                               capabilities-set->capabilities)

        res (create-doc (:conn state)
                        (:index state)
                        mapping
                        transformed)]
    (-> res
        (update-in [:capabilities] capabilities->capabilities-set)
        (dissoc :id))))

(s/defn handle-read-identity :- (s/maybe Identity)
  [state :- s/Any login :- s/Str]
  (some-> (get-doc (:conn state)
                   (:index state)
                   mapping
                   login)
          (update-in [:capabilities] capabilities->capabilities-set)
          (dissoc :id)))

(defn handle-delete-identity [state login]
  (delete-doc (:conn state)
              (:index state)
              mapping
              login))
