(ns ctia.test-helpers.auth)

(def all-capabilities
  #{:create-actor :read-actor :delete-actor
    :create-campaign :read-campaign :delete-campaign
    :create-exploit-target :read-exploit-target :delete-exploit-target
    :create-coa :read-coa :delete-coa
    :create-incident :read-incident :delete-incident
    :create-judgement :read-judgement :delete-judgement
    :create-judgement-indicator
    :create-feedback :read-feedback :list-feedback :delete-feedback
    :create-sighting :read-sighting :delete-sighting
    :create-indicator :read-indicator
    :list-indicators-by-title
    :create-ttp :read-ttp :delete-ttp
    :list-judgements-by-observable
    :list-judgements-by-indicator
    :list-indicators-by-observable
    :list-sightings-by-observable
    :get-verdict})
