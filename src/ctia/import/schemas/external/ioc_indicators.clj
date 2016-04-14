(ns ctia.import.schemas.external.ioc-indicators
  (:require [schema.core :as s]))

(s/defschema IoCIndicator
  {(s/required-key "confidence") s/Int
   (s/required-key "author") s/Str
   (s/required-key "tags") [s/Str]
   (s/required-key "name") s/Str
   (s/required-key "created-at") (s/maybe s/Str)
   (s/required-key "variables") [s/Str]
   (s/required-key "title") s/Str
   (s/required-key "last-modified") (s/maybe s/Str)
   (s/required-key "category") [s/Str]
   (s/required-key "severity") s/Int
   (s/required-key "description") (s/maybe s/Str)})
