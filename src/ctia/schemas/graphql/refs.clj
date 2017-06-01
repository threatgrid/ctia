(ns ctia.schemas.graphql.refs
  (:require [ctia.schemas.graphql.helpers :as g]))

(def judgement-type-name "Judgement")
(def JudgementRef
  (g/new-ref judgement-type-name))
