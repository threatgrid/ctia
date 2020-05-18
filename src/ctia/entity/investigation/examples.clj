(ns ctia.entity.investigation.examples
  (:require [ctim.schemas.common :as c]))

(def investigation-maximal
  {:id "http://ex.tld/ctia/investigation/investigation-2805d697-66b3-4e14-9b32-179e7a72eab6"
   :type "investigation"
   :schema_version c/ctim-schema-version
   :revision 1
   :external_ids [(str "http://ex.tld/ctia/investigation/investigation-"
                       "f867a601-ac76-4491-a0d6-51968c9f9021")
                  (str "http://ex.tld/ctia/investigation/investigation-"
                       "53468a66-5d95-49b5-88ec-454bdf894db9")]
   :external_references [{:source_name "source"
                          :external_id "T1067"
                          :url "https://ex.tld/wiki/T1067"
                          :hashes ["#section1"]
                          :description "Description text"}]
   :timestamp #inst "2017-10-23T19:25:27.278-00:00"
   :language "language"
   :object_ids ["https://intel.test.iroh.site:443/ctia/indicator/indicator-97c97157-6592-43e3-a54e-f126b4950787"]
   :investigated_observables ["sha256:585c2a90e4928f67af7be2d0bdc282b7eb6c90113ae588461a441d31b5268e88"]
   :targets [{:type "endpoint"
              :observables [{:value "Demo_iOS_3", :type "hostname"}]
              :observed_time {:start_time "2019-04-01T20:45:11.000Z"}
              :os "iOS 10.3 (1)"}]
   :title "investigation-title"
   :description "description"
   :short_description "short desc"
   :source "a source"
   :source_uri "http://example.com/somewhere-else"
   :tlp "green"})

(def investigation-minimal
  {:id "http://ex.tld/ctia/investigation/investigation-2805d697-66b3-4e14-9b32-179e7a72eab6"
   :source "a source"
   :object_ids []
   :investigated_observables []
   :targets []
   :schema_version c/ctim-schema-version
   :type "investigation"})

(def new-investigation-maximal
  investigation-maximal)

(def new-investigation-minimal
  {:source "a source"})
