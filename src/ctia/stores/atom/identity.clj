(ns ctia.stores.atom.identity
  (:require [ctia.schemas.identity :as identity]
            [ctia.stores.atom.common :as mc]
            [schema.core :as s]))

(s/defn handle-create-identity :- identity/Identity
  [state :- (s/atom {identity/Login identity/Identity})
   new-identity :- identity/Identity]
  (let [id (:login new-identity)]
    (get
     (swap! state assoc id new-identity)
     id)))

(s/defn handle-read-identity :- (s/maybe identity/Identity)
  [state :- (s/atom {identity/Login identity/Identity})
   login :- identity/Login]
  (get @state login))

(s/defn handle-delete-identity :- s/Bool
  [state :- (s/atom {identity/Login identity/Identity})
   login :- identity/Login]
  (if (contains? @state login)
    (do (swap! state dissoc login)
        true)
    false))
