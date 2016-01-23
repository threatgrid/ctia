(ns cia.sightings
  (:require [schema.core :as s]
            [cia.models :refer [Observable ObservableType Time URI Confidence]]
            [ring.swagger.schema :refer [coerce!]]))

(s/defschema Sighting
  "See http://stixproject.github.io/data-model/1.2/indicator/SightingType/"
  {:id s/Str
   :origin s/Str
   :timestamp Time
   :description  s/Str
   :observables [Observable]
   :indicators [s/Num]
   })
