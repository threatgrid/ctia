(ns ctia.domain.access-control
  (:require [clojure.set :as set]
            [ctia.properties :refer [get-access-control]]
            [ctim.schemas.common :as csc]
            [schema.core :as s])
  (:import [java.util List]))

(def acl-fields
  "Those fields should always be retrieved from _source
   to check access control"
  [:id
   :owner
   :groups
   :tlp
   :authorized_users
   :authorized_groups])

(def public-tlps
  ["white" "green"])

(def ^List tlps ["white" "green" "amber" "red"])

(defn properties-default-tlp [get-in-config]
  (or (:default-tlp (get-access-control get-in-config))
      csc/default-tlp))

(defn allowed-tlps [get-in-config]
  (let [min-tlp (:min-tlp (get-access-control get-in-config) "white")
        idx (.indexOf tlps min-tlp)
        _ (assert (not= -1 idx) min-tlp)]
    (subvec tlps idx)))

(defn max-record-visibility-everyone? [get-in-config]
  (= "everyone"
     (or (:max-record-visibility (get-access-control get-in-config))
         "everyone")))

(defn allowed-tlp? [tlp get-in-config]
  (some #{tlp} (allowed-tlps get-in-config)))

(s/defn allowed-group? :- s/Bool
  [doc ident]
  (boolean
    (some-> (not-empty (set (:groups doc)))
            (some (:groups ident)))))

(s/defn owner? :- s/Bool
  [doc ident]
  (boolean
   (and (= (:owner doc)
           (:login ident))
        (allowed-group? doc ident))))

(s/defn authorized-group? :- s/Bool
  [doc ident]
  (boolean
    (some-> (not-empty (set (:authorized_groups doc)))
            (some (:groups ident)))))

(s/defn authorized-user? :- s/Bool
  [doc ident]
  (boolean
   (and
    (some? (:login ident))
    (some #{(:login ident)} (:authorized_users doc)))))

(s/defn allow-write? :- s/Bool
  [current-doc ident]
  (boolean
   (or
     ;; Document Owner
     (owner? current-doc ident)

     ;;or if user is listed in authorized_users field
     (authorized-user? current-doc ident)

     ;;or if one of the users groups are in the authorized_groups field
     (authorized-group? current-doc ident)

     ;; CTIM models with TLP green that is owned by org BAR
     (and (some #{(:tlp current-doc)} public-tlps)
          (allowed-group? current-doc
                          ident))

     ;; CTIM models with TLP amber that is owned by org BAR
     (and (= (:tlp current-doc) "amber")
          (allowed-group? current-doc
                          ident)))))

(defn restricted-read? [ident]
  (not (:authorized-anonymous ident)))

(s/defn allow-read? :- s/Bool
  [doc ident get-in-config]
  (boolean
   (or (not (restricted-read? ident))
       (and (max-record-visibility-everyone? get-in-config)
            (some #{(:tlp doc)} public-tlps))
       (allow-write? doc ident))))
