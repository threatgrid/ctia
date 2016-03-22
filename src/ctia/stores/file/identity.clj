(ns ctia.stores.file.identity
  (:require [ctia.schemas.identity :as identity]
            [ctia.store :refer [IIdentityStore]]
            [ctia.stores.file.common :as mc]
            [schema.core :as s]
            [alandipert.enduro :as e]))

(s/defn handle-create-identity :- identity/Identity
  [state :- (s/atom {identity/Login identity/Identity})
   new-identity :- identity/Identity]
  (let [id (:login new-identity)]
    (get
     (e/swap! state assoc id new-identity)
     id)))

(s/defn handle-read-identity :- (s/maybe identity/Identity)
  [state :- (s/atom {identity/Login identity/Identity})
   login :- identity/Login]
  (get @state login))

(s/defn handle-delete-identity :- s/Bool
  [state :- (s/atom {identity/Login identity/Identity})
   login :- identity/Login]
  (if (contains? @state login)
    (do (e/swap! state dissoc login)
        true)
    false))

(defrecord IdentityStore [state]
  IIdentityStore
  (read-identity [_ login]
    (handle-read-identity state login))
  (create-identity [_ new-identity]
    (handle-create-identity state new-identity))
  (delete-identity [_ org-id role]
    (handle-delete-identity state org-id role)))
