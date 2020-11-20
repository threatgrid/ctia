(ns ctia.task.rollover-test
  (:require [ductile.index :as es-index]
            [clojure.string :as string]
            [clojure.test :refer [deftest testing is]]
            [ctia.stores.es.init :as init]
            [ctia.task.rollover :as sut]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixt]))

(deftest rollover-aliased-test
  (es-helpers/for-each-es-version
   "rollover should properly trigger _rollover"
   [5 7]
   #(es-index/delete! % "ctia_*")
   (helpers/with-properties
     ["ctia.store.es.default.port" es-port
      "ctia.store.es.default.version" version
      "ctia.auth.type" "allow-all"]
     (helpers/fixture-ctia-with-app
       (fn [app]
         (let [{{:keys [get-in-config]} :ConfigService :as services} (es-helpers/app->ESConnServices app)
               _ (assert (and (= es-port (get-in-config [:ctia :store :es :default :port]))
                              (= version (get-in-config [:ctia :store :es :default :version])))
                         (format "CTIA is not properly configured for testing ES version %s."
                                 version))
               get-indexname (fn [entity]
                               {:post [(string? %)]}
                               (get-in-config [:ctia :store :es entity :indexname]))
               props-not-aliased {:entity :malware
                                  :indexname (get-indexname :malware)
                                  :host "localhost"
                                  :port es-port
                                  :version version}
               state-not-aliased (init/init-es-conn! props-not-aliased services)
               rollover-not-aliased (sut/rollover-store state-not-aliased)
               props-aliased {:entity :sighting
                              :indexname (get-indexname :sighting)
                              :host "localhost"
                              :port es-port
                              :aliased true
                              :rollover {:max_docs 3}
                              :refresh "true"
                              :version version}
               state-aliased (init/init-es-conn! props-aliased services)
               rollover-aliased (sut/rollover-store state-aliased)
               count-indices (fn []
                               (->> (str (:index state-aliased) "*")
                                    (es-index/get (:conn state-aliased))
                                    count))
               post-bulk-fn (fn []
                              (let [examples (fixt/bundle 100 false)
                                    {:keys [error message]} (helpers/POST-bulk app examples true)]
                                (es-index/refresh! (:conn state-aliased))))]
           (is (nil? rollover-not-aliased))
           (is (seq rollover-aliased))
           (is (false? (:rolled_over rollover-aliased)))
           (is (= 1 (count-indices)))
           (post-bulk-fn)
           (is (nil? (sut/rollover-store state-not-aliased)))
           (is (= 1 (count-indices)))
           (is (true? (:rolled_over (sut/rollover-store state-aliased))))
           (is (= 2 (count-indices)))
           (post-bulk-fn)
           (is (true? (:rolled_over (sut/rollover-store state-aliased))))
           (is (= 3 (count-indices)))))))))

(deftest rollover-stores-error-test
  (with-redefs [es-index/rollover! (fn [_ alias _]
                                     (if (string/starts-with? alias "ok_index")
                                       {:rolled_over (rand-nth [true false])}
                                       (throw (ex-info "that's baaaaaaaddd"
                                                       {:code :unhappy}))))]
    ;; this test does not depends on ES version
    (helpers/with-properties*
      ["ctia.auth.type" "allow-all"
       "ctia.store.es.default.port" 9207
       "ctia.store.es.default.version" 7]
      #(helpers/fixture-ctia-with-app
        (fn [app]
          (let [services (es-helpers/app->ESConnServices app)
                ok-state (init/init-store-conn {:entity "sighting"
                                                :indexname "ok_index"
                                                :rollover {:max_docs 3}
                                                :aliased true}
                                               services)
                ko-state (init/init-store-conn {:entity "sighting"
                                                :indexname "bbaaaaadddd_index"
                                                :rollover {:max_docs 2}
                                                :aliased true}
                                               services)
                stores {:ok-type-1 [{:state ok-state}]
                        :ok-type-2 [{:state ok-state}]
                        :ok-type-3 [{:state ok-state}]
                        :ko-type-1 [{:state ko-state}]
                        :ko-type-2 [{:state ko-state}]}
                {:keys [nb-errors
                        ok-type-1
                        ok-type-2
                        ok-type-3
                        ko-type-1
                        ko-type-2]} (sut/rollover-stores stores)]
            (is (= 2 nb-errors))
            (is (every? nil? [ko-type-1 ko-type-2]))
            (is (every? (fn [k] (some? (:rolled_over k)))
                        [ok-type-1 ok-type-2 ok-type-3]))))))))
