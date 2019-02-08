(ns ctia.http.handler
  (:require [clj-momo.ring.middleware.metrics :as metrics]
            [ctia.entity.entities :refer [entities]]
            [ctia.entity.casebook :refer [casebook-operation-routes]]
            [ctia.entity.incident :refer [incident-additional-routes]]
            [ctia.entity.feedback :refer [feedback-by-entity-route]]
            [ctia.entity.relationship :refer [incident-casebook-link-route]]
            [compojure.api
             [core :refer [middleware]]
             [routes :as api-routes]
             [sweet :refer  [routes api context undocumented]]]
            [compojure.route :as rt]
            [ctia.bundle.routes :refer [bundle-routes]]
            [ctia.bulk.routes :refer [bulk-routes]]
            [ctia.documentation.routes :refer [documentation-routes]]
            [ctia.graphql.routes :refer [graphql-ui-routes
                                         graphql-routes]]
            [ctia.http.exceptions :as ex]
            [ctia.http.middleware
             [auth :refer :all]
             [cache-control :refer [wrap-cache-control]]
             [unknown :as unk]
             [version :refer [wrap-version]]]
            [ctia.metrics.routes :refer [metrics-routes]]
            [ctia.observable.routes :refer [observable-routes]]
            [ctia.properties :refer [properties
                                     get-http-swagger]]
            [ctia.properties.routes :refer [properties-routes]]
            [ctia.version.routes :refer [version-routes]]
            [ctia.status.routes :refer [status-routes]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.util.http-response :refer [ok]]))

(def api-description
  "A Threat Intelligence API service

  This API provides a mechanism for making Judgements on the Disposition
  of Observables, which are then distilled into a final Verdict.  A
  Disposition is a statement regarding the malicious, or otherwise,
  nature of an Observable.

  The Judgements can be grouped into Indicators, which can be associated
  with Campaigns, Actors and TTPs.  Feedback can be given on specific
  Judgements, indicating agreement or disagreement, or clarification.

  When an Observable with a malicious Verdict is seen, it can be recorded as
  a Sighting, and the Relations that Observable had with other Observables can
  be recorded as well.

  We support a pre-defined set of Observable Types.  Each Observable Type has a
  specific form of natural identifier, its ID, which is almost always
  the default way it it represented when observed in the wild.

  * Ipv4/IPv6  -- 192.168.1.1
  * Domain/Hostname -- foo.com, www.bar.com
  * SHA256  -- the sha256 checksum of a file, or other data blob
  * MD5 -- the md5 checksum of a file, or other data blob
  * SHA1 -- the sha1 checksum of a file, or other data blob
  * URL -- A minimal form of the URL

  The Verdict is derived from all of the Judgements on that Observable which
  have not yet expired.  The highest priority Judgement becomes the
  active verdict.  If there is more than one Judgement with that
  priority, than Clean disposition has priority over all others, then
  Malicious disposition, and so on down to Unknown.

  <a href='/doc/README.md'>CTIA Documentation</a>")


(defmacro entity-routes
  [entities]
  `(do
     (compojure.api.sweet/routes
      ~@(for [entity (remove :no-api?
                             (vals (eval entities)))]
          `(context
            ~(:route-context entity) []
            :tags ~(:tags entity)
            (:routes (~(:entity entity) entities)))))))

(defn api-handler []
  (let [swagger-config (get-http-swagger)
        oauth2? (-> swagger-config :oauth2 :enabled)
        scope-map
        (into {} (-> swagger-config
                     :oauth2
                     :scopes
                     (clojure.string/split #",")
                     (->> (map #(clojure.string/split % #"\|")))))
        _ (clojure.pprint/pprint scope-map)
        scopes (keys scope-map)]
    (api {:exceptions
          {:handlers
           {:compojure.api.exception/request-parsing ex/request-parsing-handler
            :compojure.api.exception/request-validation ex/request-validation-handler
            :compojure.api.exception/response-validation ex/response-validation-handler
            :clj-momo.lib.es.conn/es-query-parsing-error ex/es-query-parsing-error-handler
            :access-control-error ex/access-control-error-handler
            :invalid-tlp-error ex/invalid-tlp-error-handler
            :spec-validation-error ex/spec-validation-error-handler
            :compojure.api.exception/default ex/default-error-handler}}
          :swagger
          (cond-> {:ui "/"
                   :spec "/swagger.json"
                   :data {:info {:title "CTIA"
                                 :license {:name "All Rights Reserved",
                                           :url ""}
                                 :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                           :url "http://github.com/threatgrid/ctia"
                                           :email "cisco-intel-api-support@cisco.com"}
                                 :description api-description}
                          :tags [{:name "Actor" :description "Actor operations"}
                                 {:name "Attack Pattern" :description "Attack Pattern operations"}
                                 {:name "Bundle" :description "Bundle operations (Beta)"}
                                 {:name "Campaign" :description "Campaign operations"}
                                 {:name "COA" :description "COA operations"}
                                 {:name "DataTable" :description "DataTable operations"}
                                 {:name "Event" :description "Events operations"}
                                 {:name "Feedback" :description "Feedback operations"}
                                 {:name "GraphQL" :description "GraphQL operations"}
                                 {:name "Incident" :description "Incident operations"}
                                 {:name "Indicator", :description "Indicator operations"}
                                 {:name "Judgement", :description "Judgement operations"}
                                 {:name "Malware", :description "Malware operations"}
                                 {:name "Relationship", :description "Relationship operations"}
                                 {:name "Properties", :description "Properties operations"}
                                 {:name "Casebook", :description "Casebook operations"}
                                 {:name "Sighting", :description "Sighting operations"}
                                 {:name "Bulk", :description "Bulk operations"}
                                 {:name "Metrics", :description "Performance Statistics"}
                                 {:name "Tool", :description "Tool operations"}
                                 {:name "Verdict", :description "Verdict operations"}
                                 {:name "Status", :description "Status Information"}
                                 {:name "Version", :description "Version Information"}]}}

            oauth2? (assoc-in [:options :ui]
                              {:oauth2 {:clientId (-> swagger-config :oauth2 :client-id)
                                        :appName (-> swagger-config :oauth2 :app-name)
                                        :realm (-> swagger-config :oauth2 :realm)}})
            oauth2? (assoc-in [:data :security]
                              [{(-> swagger-config :oauth2 :key) scopes}])
            oauth2? (assoc-in [:data :securityDefinitions]
                              {(-> swagger-config :oauth2 :key)
                               {:type "oauth2"
                                :scopes scope-map
                                :authorizationUrl (-> swagger-config :oauth2 :authorization-url)
                                :tokenUrl (-> swagger-config :oauth2 :token-url)
                                :flow (-> swagger-config :oauth2 :flow)}}))}

         (middleware [wrap-not-modified
                      wrap-cache-control
                      wrap-version
                      ;; always last
                      (metrics/wrap-metrics "ctia" api-routes/get-routes)]
                     documentation-routes
                     (graphql-ui-routes)
                     (context
                      "/ctia" []
                      ;; The order is important here for version-routes
                      ;; must be before the middleware fn
                      version-routes
                      (middleware [wrap-authenticated]
                                  (entity-routes entities)
                                  status-routes
                                  (context
                                   "/bulk" []
                                   :tags ["Bulk"]
                                   bulk-routes)
                                  (context
                                   "/incident" []
                                   :tags ["Incident"]
                                   incident-casebook-link-route)
                                  bundle-routes
                                  observable-routes
                                  metrics-routes
                                  properties-routes
                                  graphql-routes)))
         (undocumented
          (rt/not-found (ok (unk/err-html)))))))
