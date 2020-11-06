(ns ctia.graphql-service-schemas
  (:require [schema.core :as s])
  (:import [graphql GraphQL]))

(s/defschema Context
  {:graphql GraphQL})
