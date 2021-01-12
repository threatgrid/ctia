(ns ctia.schemas.services
  "Schemas for functions in our Trapperkeeper service
  graph that are provided by external libraries"
  (:require [schema.core :as s]))

(s/defschema ConfigServiceFns
  "The built-in Trapperkeeper configuration service.

  https://github.com/puppetlabs/trapperkeeper/blob/3.1.0/documentation/Built-in-Configuration-Service.md#trapperkeepers-built-in-configuration-service"
  {:get-config (s/=> s/Any)
   :get-in-config (s/=>* s/Any
                         [[s/Any]]
                         [[s/Any] s/Any])})
