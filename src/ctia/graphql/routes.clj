(ns ctia.graphql.routes
  (:require [clojure.tools.logging :as log]
            [compojure.api.core :as c :refer [POST routes]]
            [ctia.graphql.schemas :as gql]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ring-graphql-ui.core :refer [graphiql
                                          voyager]]
            [ring.util.http-response :refer [bad-request
                                             internal-server-error
                                             ok]]
            [schema.core :as s]))

(s/defn graphql-ui-routes [{{:keys [get-in-config]} :ConfigService
                            :as _services_} :- APIHandlerServices]
  (let [jwt-storage-key
        (get-in-config [:ctia :http :jwt :local-storage-key])]
    (c/undocumented
     ;; --- GraphiQL https://github.com/shahankit/custom-graphiql/
     (graphiql {:path "/graphiql"
                :endpoint "/ctia/graphql"
                :jwtLocalStorageKey jwt-storage-key})
     ;; --- GraphQL Voyager https://github.com/APIs-guru/graphql-voyager
     (voyager {:path "/voyager"
               :endpoint "/ctia/graphql"
               :jwtLocalStorageKey jwt-storage-key}))))

(s/defn graphql-routes [_services_ :- APIHandlerServices]
 (routes
  (POST "/graphql" []
        :tags ["GraphQL"]
        :return gql/RelayGraphQLResponse
        :body [body gql/RelayGraphQLQuery {:description "a Relay compatible GraphQL body"}]
        :summary "EXPERIMENTAL: Executes a Relay compatible GraphQL query"
        :capabilities
        #{:list-campaigns
          :read-actor
          :read-asset
          :read-asset-mapping
          :read-asset-properties
          :read-malware
          :read-attack-pattern
          :read-judgement
          :read-sighting
          :list-sightings
          :read-identity-assertion
          :list-identity-assertions
          :list-relationships
          :read-coa
          :read-indicator
          :list-judgements
          :list-tools
          :list-indicators
          :read-feedback
          :list-verdicts
          :list-feedbacks
          :list-malwares
          :list-data-tables
          :list-incidents
          :read-campaign
          :list-attack-patterns
          :read-relationship
          :list-actors
          :list-assets
          :list-asset-mappings
          :list-asset-properties
          :read-investigation
          :read-incident
          :list-coas
          :read-tool
          :list-investigations
          :read-data-table
          :list-weaknesses
          :read-weakness
          :list-vulnerabilities
          :read-vulnerability}
        :identity-map identity-map
        (let [request-context {:ident identity-map}
              {:keys [query operationName variables]} body]
          (log/debug "Graphql call body: "
                     (pr-str body)
                     " variables: " (pr-str variables))
          (let [{:keys [errors data] :as result}
                (gql/execute query operationName variables request-context)
                str-errors (map str errors)]
            (log/debug "Graphql result:" result)
            (cond
              (seq str-errors) (do (log/error "Graphql errors: " (pr-str str-errors))
                                   (bad-request (cond-> {:errors str-errors}
                                                  (some? data) (assoc :data data))))
              (some? data) (ok {:data data})
              :else (internal-server-error
                     {:error "No data or errors were returned by the GraphQL query"})))))))
