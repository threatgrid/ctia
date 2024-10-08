(ns ctia.task.rollover-test
  (:require [ductile.index :as es-index]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [ctia.stores.es.init :as init]
            [ctia.task.rollover :as sut]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.es :as es-helpers]
            [ctia.test-helpers.fixtures :as fixt]
            [puppetlabs.trapperkeeper.app :as app]))

(deftest rollover-aliased-test
  (es-helpers/for-each-es-version
   "rollover should properly trigger _rollover"
   [7]
   #(es-helpers/clean-es-state! % "ctia_*")
   (helpers/with-properties
     (into ["ctia.store.es.default.port" es-port
            "ctia.store.es.default.version" version
            "ctia.auth.type" "allow-all"]
           es-helpers/basic-auth-properties)
     (helpers/fixture-ctia-with-app
       (fn [app]
         (let [{{:keys [get-in-config]} :ConfigService :as services} (es-helpers/app->ESConnServices app)
               _ (assert (and (= es-port (get-in-config [:ctia :store :es :default :port]))
                              (= version (get-in-config [:ctia :store :es :default :version])))
                         (format "CTIA is not properly configured for testing ES version %s."
                                 version))
               props-aliased {:entity :sighting
                              :indexname (es-helpers/get-indexname app :sighting)
                              :host "localhost"
                              :port es-port
                              :aliased true
                              :rollover {:max_docs 3}
                              :refresh "true"
                              :version version
                              :auth es-helpers/basic-auth}
               state-aliased (init/init-es-conn! props-aliased services)
               rollover-aliased (sut/rollover-store state-aliased)
               count-indices (fn []
                               (->> (str (:index state-aliased) "*")
                                    (es-index/get conn)
                                    count))
               post-bulk-fn (fn []
                              (let [examples (fixt/bundle 100 false)]
                                (helpers/POST-bulk app examples true)
                                (es-index/refresh! conn)))]
           (post-bulk-fn)
           (assert (= 1 (count-indices)))
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
    ;; this test does not depend on ES version
    (helpers/with-properties*
      (into ["ctia.auth.type" "allow-all"
             "ctia.store.es.default.port" 9207
             "ctia.store.es.default.version" 7]
            es-helpers/basic-auth-properties)
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

(deftest mk-app!-test
  (es-helpers/for-each-es-version
   "rollover task shall properly init"
   [7]
    #(es-helpers/clean-es-state! % "ctia_*")
    (is (map? (app/service-graph (sut/mk-app!))))))
