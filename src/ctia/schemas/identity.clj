(ns ctia.schemas.identity
  (:require [ctim.schemas.common :as c]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def Role s/Str)
(def Login s/Str)
(def Group s/Str)

(s/defschema Identity
  {:role Role
   :groups [Group]
   :capabilities #{s/Keyword}
   :login s/Str})
