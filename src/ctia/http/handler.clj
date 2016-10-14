(ns ctia.http.handler
  (:require [compojure.api.sweet :refer [context defapi]]
            [compojure.api.core :refer [middleware]]
            [ctia.http.middleware.auth :as auth]
            [ctia.http.exceptions :as ex]
            [ctia.http.middleware.metrics :as metrics]
            [ctia.http.routes
             [actor :refer [actor-routes]]
             [bulk :refer [bulk-routes]]
             [campaign :refer [campaign-routes]]
             [coa :refer [coa-routes]]
             [data-table :refer [data-table-routes]]
             [documentation :refer [documentation-routes]]
             [event :refer [event-routes]]
             [exploit-target :refer [exploit-target-routes]]
             [feedback :refer [feedback-routes]]
             [incident :refer [incident-routes]]
             [indicator :refer [indicator-routes]]
             [judgement :refer [judgement-routes]]
             [metrics :refer [metrics-routes]]
             [observable :refer [observable-routes]]
             [properties :refer [properties-routes]]
             [sighting :refer [sighting-routes]]
             [ttp :refer [ttp-routes]]
             [verdict :refer [verdict-routes]]
             [bundle :refer [bundle-routes]]
             [version :refer [version-routes]]]
            [ring.middleware
             [format :refer [wrap-restful-format]]
             [params :as params]]
            [ctia.http.middleware.cache-control :refer [wrap-cache-control-headers]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]))

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

(defapi api-handler
  {:exceptions
   {:handlers
    {:compojure.api.exception/request-parsing ex/request-parsing-handler
     :compojure.api.exception/request-validation ex/request-validation-handler
     :compojure.api.exception/response-validation ex/response-validation-handler
     :compojure.api.exception/default ex/default-error-handler}}
   :swagger {:ui "/"
             :spec "/swagger.json"
             :data {:info {:title "CTIA"
                           :license {:name "All Rights Reserved",
                                     :url ""}
                           :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                     :url "http://github.com/threatgrid/ctia"
                                     :email "cisco-intel-api-support@cisco.com"}
                           :description api-description}

                    :tags [{:name "Actor" :description "Actor operations"}
                           {:name "Campaign" :description "Campaign operations"}
                           {:name "COA" :description "COA operations"}
                           {:name "DataTable" :description "DataTable operations"}
                           {:name "Events" :description "Events operations"}
                           {:name "ExploitTarget" :description "ExploitTarget operations"}
                           {:name "Feedback" :description "Feedback operations"}
                           {:name "Incident" :description "Incident operations"}
                           {:name "Indicator", :description "Indicator operations"}
                           {:name "Judgement", :description "Judgement operations"}
                           {:name "Properties", :description "Properties operations"}
                           {:name "Sighting", :description "Sighting operations"}
                           {:name "TTP", :description "TTP operations"}
                           {:name "Verdict", :description "Verdict operations"}
                           {:name "Bundle", :description "Bundle operations"}
                           {:name "Version", :description "Version operations"}]}}}

  (middleware [auth/wrap-authentication
               wrap-not-modified
               wrap-cache-control-headers
               ;; always last
               metrics/wrap-metrics]

              documentation-routes
              (context "/ctia" []
                       actor-routes
                       bulk-routes
                       campaign-routes
                       coa-routes
                       data-table-routes
                       event-routes
                       exploit-target-routes
                       feedback-routes
                       incident-routes
                       indicator-routes
                       judgement-routes
                       metrics-routes
                       observable-routes
                       properties-routes
                       sighting-routes
                       ttp-routes
                       verdict-routes
                       bundle-routes
                       version-routes)))
