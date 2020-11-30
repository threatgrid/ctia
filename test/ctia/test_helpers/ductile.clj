(ns ctia.test-helpers.ductile
  (:require [puppetlabs.trapperkeeper.core :as tk]))

(defprotocol CTIADuctileTestHelperService)

(tk/defservice ctia-ductile-test-helper-service
  CTIADuctileTestHelperService
  []
  )
