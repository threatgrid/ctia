(ns ctia.http.handler
  (:require [clj-momo.ring.middleware.metrics :as metrics]
            [ctia.entity.entities :refer [entities]]
            [ctia.entity.casebook :refer [casebook-operation-routes]]
            [ctia.entity.feedback :refer [feedback-by-entity-route]]
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
            [ctia.properties :refer [properties]]
            [ctia.properties.routes :refer [properties-routes]]
            [ctia.version.routes :refer [version-routes]]
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
  (api {:exceptions
        {:handlers
         {:compojure.api.exception/request-parsing ex/request-parsing-handler
          :compojure.api.exception/request-validation ex/request-validation-handler
          :compojure.api.exception/response-validation ex/response-validation-handler
          :clj-momo.lib.es.conn/es-query-parsing-error ex/es-query-parsing-error-handler
          :access-control-error ex/access-control-error-handler
          :compojure.api.exception/default ex/default-error-handler}}
        :swagger {:ui "/"
                  :spec "/swagger.json"
                  :options {:ui {:jwtLocalStorageKey
                                 (get-in @properties
                                         [:ctia :http :jwt :local-storage-key])}}
                  :data {:info {:title "CTIA"
                                :license {:name "All Rights Reserved",
                                          :url ""}
                                :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                          :url "http://github.com/threatgrid/ctia"
                                          :email "cisco-intel-api-support@cisco.com"}
                                :description api-description}

                         :tags [{:name "Actor" :description "Actor operations"}
                                {:name "Attack Pattern" :description "Attack Pattern operations"}
                                {:name "Bundle" :description "Bundle import (Beta)"}
                                {:name "Campaign" :description "Campaign operations"}
                                {:name "COA" :description "COA operations"}
                                {:name "DataTable" :description "DataTable operations"}
                                {:name "Events" :description "Events operations"}
                                {:name "ExploitTarget" :description "ExploitTarget operations"}
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
                                {:name "Version", :description "Version Information"}]}}}

       (middleware [wrap-not-modified
                    wrap-cache-control
                    wrap-version
                    ;; always last
                    (metrics/wrap-metrics "ctia" api-routes/get-routes)]
                   documentation-routes
                   (graphql-ui-routes)
                   (context
                    "/ctia" []
                    (entity-routes entities)
                    (context "/casebook" []
                             :tags ["Casebook"]
                             casebook-operation-routes)
                    (context "/feedback" []
                             :tags ["Feedback"]
                             feedback-by-entity-route)
                    (context
                     "/bulk" []
                     :tags ["Bulk"]
                     bulk-routes)
                    bundle-routes
                    observable-routes
                    metrics-routes
                    properties-routes
                    graphql-routes
                    version-routes))
       (undocumented
        (rt/not-found (ok (unk/err-html))))))
