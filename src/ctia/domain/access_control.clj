(ns ctia.domain.access-control
  (:require [clojure.set :as set]
            [clojure.string :as str]
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
  (let [min-tlp (:min-tlp (get-access-control get-in-config) "white")]
    (nthrest tlps
             (.indexOf tlps min-tlp))))

(defn max-record-visibility-everyone? [get-in-config]
  (= "everyone"
     (or (:max-record-visibility (get-access-control get-in-config))
         "everyone")))

(defn allowed-tlp? [tlp get-in-config]
  (some #{tlp} (allowed-tlps get-in-config)))

(s/defn allowed-group? :- s/Bool
  [doc ident]
  (boolean
   (and (seq (:groups ident))
        (seq (:groups doc))
        (boolean (some (set (:groups ident))
                       (set (:groups doc)))))))

(s/defn owner? :- s/Bool
  [doc ident]
  (boolean
   (and (= (:owner doc)
           (:login ident))
        (allowed-group? doc ident))))

(s/defn authorized-group? :- s/Bool
  [doc ident]
  (boolean
   (and
    (seq (:groups ident))
    (seq (:authorized_groups doc))
    (seq (set/intersection
          (set (:groups ident))
          (set (:authorized_groups doc)))))))

(s/defn authorized-user? :- s/Bool
  [doc ident]
  (boolean
   (and
    (not (nil? (:login ident)))
    (seq (:authorized_users doc))
    (seq (set/intersection
          #{(:login ident)}
          (set (:authorized_users doc)))))))

(s/defn allow-write? :- s/Bool
  [current-doc ident]
  (boolean
   (cond
     ;; Document Owner
     (owner? current-doc ident) true

     ;;or if user is listed in authorized_users field
     (authorized-user? current-doc ident) true

     ;;or if one of the users groups are in the authorized_groups field
     (authorized-group? current-doc ident) true

     ;; CTIM models with TLP green that is owned by org BAR
     (and (some #{(:tlp current-doc)} public-tlps)
          (allowed-group? current-doc
                          ident)) true

     ;; CTIM models with TLP amber that is owned by org BAR
     (and (= (:tlp current-doc) "amber")
          (allowed-group? current-doc
                          ident)) true)))

(defn restricted-read? [ident]
  (not (:authorized-anonymous ident)))

(s/defn allow-read? :- s/Bool
  [doc ident get-in-config]
  (boolean
   (or (not (restricted-read? ident))
       (and (max-record-visibility-everyone? get-in-config)
            (some #{(:tlp doc)} public-tlps))
       (allow-write? doc ident))))

(s/defn validate-authorized-groups
  "Validates that all values in the entity's authorized_groups belong to
   the authenticated user's groups. Returns nil if valid, or a set of
   invalid group IDs if the entity contains foreign groups."
  [entity ident]
  (let [entity-authorized-groups (set (map str/lower-case (:authorized_groups entity)))
        identity-groups (set (map str/lower-case (:groups ident)))
        foreign (set/difference entity-authorized-groups identity-groups)]
    (when (seq foreign)
      foreign)))

(s/defn validate-authorized-users
  "Validates that the only allowed value in the entity's authorized_users
   is the caller's own login. Returns nil if valid, or a set of invalid
   user logins if the entity contains foreign users."
  [entity ident]
  (let [entity-authorized-users (set (map str/lower-case (:authorized_users entity)))
        allowed #{(some-> (:login ident) str/lower-case)}
        foreign (set/difference entity-authorized-users allowed)]
    (when (seq foreign)
      foreign)))
