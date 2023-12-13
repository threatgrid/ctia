(ns ctia.bundle.routes-bench
  (:require [ctia.test-helpers
             [benchmark :refer [cleanup-ctia!
                                setup-ctia-es-store!]]
             [core :as helpers :refer [POST]]]
            [ctim.examples.bundles
             :refer [new-bundle-minimal]]
            [perforate.core :refer :all]))

(def empty-bundle new-bundle-minimal)

(defgoal import-bundle "Import Bundle"
  :setup (fn [] [(setup-ctia-es-store!)])
  :cleanup (fn [{:keys [app]}] (cleanup-ctia! app)))

(defn play [app fixture]
  (POST app
        "ctia/bundle/import"
        :body fixture
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defcase import-bundle :empty-bundle-import-es-store
  [{:keys [app]}] (play app empty-bundle))
