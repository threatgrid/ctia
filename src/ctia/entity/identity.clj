(ns ctia.entity.identity
  (:require [schema.core :as s]
            [schema-tools.core :as st]
            [clj-momo.lib.es
             [document :refer [create-doc get-doc delete-doc]]]
            [ctia.store :refer [IIdentityStore]]
            [ctia.stores.es
             [crud :as crud]
             [mapping :as em]
             [schemas :refer [ESConnState]]]))

(def Role s/Str)
(def Login s/Str)
(def Group s/Str)

(s/defschema Identity
  {:role Role
   :groups [Group]
   :capabilities #{s/Keyword}
   :login s/Str})

(def ^{:private true} mapping "identity")

(s/defschema PartialIdentity
  (st/optional-keys Identity))

(defn capabilities->capabilities-set
  "transform a vec of capabilities from es
   to a set of keywords"
  [caps]
  (set (map keyword caps)))

(defn capabilities-set->capabilities
  "transform a set of capabilities
   into a vec of strings"
  [caps]
  (vec (map name caps)))

(s/defn handle-create :- Identity
  [{:keys [conn props]} :- ESConnState
   new-identity :- Identity]
  (let [id (:login new-identity)
        realized (assoc new-identity :id id)
        transformed (update-in realized [:capabilities]
                               capabilities-set->capabilities)
        res (create-doc conn
                        (:write-index props)
                        mapping
                        transformed
                        (:refresh props "false"))]
    (-> res
        (update-in [:capabilities] capabilities->capabilities-set)
        (dissoc :id))))

(s/defn handle-read :- (s/maybe Identity)
  [state :- ESConnState
   login :- s/Str]
  (some-> (crud/get-doc-with-index state login {})
          :_source
          (update-in [:capabilities] capabilities->capabilities-set)
          (dissoc :id)))

(s/defn handle-delete
  [state :- ESConnState
   login :- s/Str]
  (when-let [{index :_index}
             (crud/get-doc-with-index state login {})]
    (delete-doc (:conn state)
                index
                mapping
                login
                true)))

(def identity-mapping
  {"identity"
   {:dynamic false
    :properties
    {:id em/all_token
     :role em/token
     :capabilities em/token
     :login em/token
     :groups em/token}}})

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (handle-read state login))
  (create-identity [_ new-identity]
    (handle-create state new-identity))
  (delete-identity [_ org-id role]
    (handle-delete state org-id)))

(def identity-entity
  {:new-spec map?
   :schema Identity
   :stored-schema Identity
   :partial-schema PartialIdentity
   :partial-stored-schema PartialIdentity
   :partial-list-schema [PartialIdentity]
   :new-schema Identity
   :no-api? true
   :no-bulk? true
   :entity :identity
   :plural :identities
   :es-store ->IdentityStore
   :es-mapping identity-mapping})
