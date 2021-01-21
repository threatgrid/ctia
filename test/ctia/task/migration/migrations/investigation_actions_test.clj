(ns ctia.task.migration.migrations.investigation-actions-test
  (:require [ctia.task.migration.migrations.investigation-actions :as sut]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is]]))

(deftest derive-action-data-test
  (is (= sut/empty-action-data
         (sut/derive-action-data {:type "judgement"
                                  :observable {:type "pki-serial" :value "12345"}})))

  (is (= sut/empty-action-data
         (sut/derive-action-data {:type "collect"
                                  :result []})))
  (is (= {:object_ids #{}
          :investigated_observables
          #{"sha256:585c2a90e4928f67af7be2d0bdc282b7eb6c90113ae588461a441d31b5268e88"
            "sha256:ac517bb701f20fe2f1826a0170c8ba7ba3f0632c77d89ed2dfe1883f588e9d1f"}
          :targets #{}}
         (sut/derive-action-data {:type "collect"
                                  :result [{:value "585c2a90e4928f67af7be2d0bdc282b7eb6c90113ae588461a441d31b5268e88"
                                            :type "sha256"}
                                           {:value "ac517bb701f20fe2f1826a0170c8ba7ba3f0632c77d89ed2dfe1883f588e9d1f"
                                            :type "sha256"}]})))

  (is (= {:object_ids #{"https://intel.test.iroh.site:443/ctia/indicator/indicator-97c97157-6592-43e3-a54e-f126b4950787"
                        "https://intel.test.iroh.site:443/ctia/sighting/sighting-b1a75bb0-d10d-47e6-b70d-915a24f66a0b"
                        "https://intel.test.iroh.site:443/ctia/relationship/relationship-c3c89cb9-b09a-4fe3-b92a-5bae7faca36a"}
          :investigated_observables []
          :targets #{}}
         (sut/derive-action-data
          {:type "investigate",
           :result {:data [{:module "AMP Global Intel",
                            :module-type "CTIAInvestigateModule",
                            :data {:indicators {:docs [{:id "https://intel.test.iroh.site:443/ctia/indicator/indicator-97c97157-6592-43e3-a54e-f126b4950787"}]}
                                   :relationships {:docs [{:id "https://intel.test.iroh.site:443/ctia/relationship/relationship-c3c89cb9-b09a-4fe3-b92a-5bae7faca36a"}]}
                                   :sightings {:docs [{:id "https://intel.test.iroh.site:443/ctia/sighting/sighting-b1a75bb0-d10d-47e6-b70d-915a24f66a0b"}]}}}]}})))


  (is (= {:object_ids #{"https://intel.test.iroh.site:443/ctia/indicator/indicator-97c97157-6592-43e3-a54e-f126b4950787"
                        "https://intel.test.iroh.site:443/ctia/sighting/sighting-b1a75bb0-d10d-47e6-b70d-915a24f66a0b"
                        "https://intel.test.iroh.site:443/ctia/relationship/relationship-c3c89cb9-b09a-4fe3-b92a-5bae7faca36a"},
          :investigated_observables [],
          :targets #{{:type "endpoint",
                      :observables [{:value "Demo_iOS_3", :type "hostname"}],
                      :observed_time {:start_time "2019-04-01T20:45:11.000Z"},
                      :os "iOS 10.3 (1)"}
                     {:type "endpoint",
                      :observables [{:value "Demo_iOS_1", :type "hostname"}],
                      :observed_time {:start_time "2019-03-27T15:30:24.000Z"},
                      :os "iOS 10.3 (1)"}}}
         (sut/derive-action-data
          {:type "investigate",
           :result {:data [{:module "AMP Global Intel",
                            :module-type "CTIAInvestigateModule",
                            :data {:indicators {:docs [{:id "https://intel.test.iroh.site:443/ctia/indicator/indicator-97c97157-6592-43e3-a54e-f126b4950787"
                                                        :targets [{:type "endpoint",
                                                                   :observables [{:value "Demo_iOS_3", :type "hostname"}]
                                                                   :observed_time {:start_time "2019-04-01T20:45:11.000Z"}
                                                                   :os "iOS 10.3 (1)"}]}]}
                                   :relationships {:docs [{:id "https://intel.test.iroh.site:443/ctia/relationship/relationship-c3c89cb9-b09a-4fe3-b92a-5bae7faca36a"}]}
                                   :sightings {:docs [{:id "https://intel.test.iroh.site:443/ctia/sighting/sighting-b1a75bb0-d10d-47e6-b70d-915a24f66a0b"
                                                       :targets [{:type "endpoint",
                                                                  :observables [{:value "Demo_iOS_1", :type "hostname"}]
                                                                  :observed_time {:start_time "2019-03-27T15:30:24.000Z"},
                                                                  :os "iOS 10.3 (1)"}]}]}}}]}}))))

(deftest migrate-action-data-test
   (let [actions [{:type "collect"
                   :result [{:value "585c2a90e4928f67af7be2d0bdc282b7eb6c90113ae588461a441d31b5268e88"
                             :type "sha256"}
                            {:value "ac517bb701f20fe2f1826a0170c8ba7ba3f0632c77d89ed2dfe1883f588e9d1f"
                             :type "sha256"}]}]
         actions-json (json/encode actions)
         entities [{:type "investigation"
                    :actions actions-json}]]
     (is (= [{:type "investigation",
              :actions actions-json
              :object_ids #{}
              :investigated_observables #{"sha256:585c2a90e4928f67af7be2d0bdc282b7eb6c90113ae588461a441d31b5268e88"
                                          "sha256:ac517bb701f20fe2f1826a0170c8ba7ba3f0632c77d89ed2dfe1883f588e9d1f"}
              :targets #{}}]
            (into [] sut/migrate-action-data entities)))))
