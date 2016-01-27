(ns cia.sightings
  (:require [schema.core :as s]
            [cia.models :refer [Observable ObservableType Time URI Confidence ID Reference]]
            [ring.swagger.schema :refer [coerce!]]))

(s/defschema Sighting
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingType/"
  {:id s/Str
   :source s/Str
   :source_uri URI
   :timestamp Time
   :description  s/Str
   :observables [Observable]
   :relations [Reference]
   :indicators [Reference]
   })


(s/defschema Incidents
  "See http://stixproject.github.io/data-model/1.2/incident/IncidentType/"
  {:id ID})
