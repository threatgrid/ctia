(ns ctia.http.handler
  (:require [compojure.api.sweet :refer [defapi context]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.params :as params]
            [ctia.http.middleware.auth :as auth]
            [ctia.http.routes.documentation :refer [documentation-routes]]
            [ctia.http.routes.version :refer [version-routes]]
            [ctia.http.routes.actor :refer [actor-routes]]
            [ctia.http.routes.campaign :refer [campaign-routes]]
            [ctia.http.routes.exploit-target :refer [exploit-target-routes]]
            [ctia.http.routes.coa :refer [coa-routes]]
            [ctia.http.routes.incident :refer [incident-routes]]
            [ctia.http.routes.judgement :refer [judgement-routes]]
            [ctia.http.routes.indicator :refer [indicator-routes]]
            [ctia.http.routes.ttp :refer [ttp-routes]]
            [ctia.http.routes.sighting :refer [sighting-routes]]
            [ctia.http.routes.event :refer [event-routes]]
            [ctia.http.routes.observable :refer [observable-routes]]))

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

  <a href='/doc/data_structures.md'>Data structures documentation</a>")

(defapi api-handler
  {:swagger {:ui "/"
             :spec "/swagger.json"
             :data {:info {:title "Cisco Threat Intel API "
                           :license {:name "All Rights Reserved",
                                     :url ""}
                           :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                     :url "http://intel.threatgrid.cisco.com/support"
                                     :email "cisco-intel-api-support@cisco.com"}
                           :description api-description}
                    :tags [{:name "threat", :description "Threat Intelligence"}]}}}

  documentation-routes

  (context "/ctia" []
    version-routes
    actor-routes
    campaign-routes
    exploit-target-routes
    coa-routes
    incident-routes
    judgement-routes
    indicator-routes
    ttp-routes
    sighting-routes
    event-routes
    observable-routes))

(def app
  (-> api-handler
      auth/wrap-authentication
      params/wrap-params
      wrap-restful-format))
