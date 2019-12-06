(ns ctia.domain.access-control
  (:require [ctia.properties :refer [get-access-control]]
            [ctim.schemas.common :as csc]
            [schema.core :as s]))

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

(def tlps ["white" "green" "amber" "red"])

(defn properties-default-tlp []
  (or (:default-tlp (get-access-control))
      csc/default-tlp))

(defn allowed-tlps []
  (let [min-tlp (:min-tlp (get-access-control) "white")]
    (nthrest tlps
             (.indexOf tlps min-tlp))))

(defn max-record-visibility-everyone? []
  (= "everyone"
     (or (:max-record-visibility (get-access-control))
         "everyone")))

(defn allowed-tlp? [tlp]
  (some #{tlp} (allowed-tlps)))

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
    (seq (clojure.set/intersection
          (set (:groups ident))
          (set (:authorized_groups doc)))))))

(s/defn authorized-user? :- s/Bool
  [doc ident]
  (boolean
   (and
    (not (nil? (:login ident)))
    (seq (:authorized_users doc))
    (seq (clojure.set/intersection
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

(s/defn allow-read? :- s/Bool
  [doc ident]
  (boolean
   (or (and (max-record-visibility-everyone?)
            (some #{(:tlp doc)} public-tlps))
       (allow-write? doc ident))))
