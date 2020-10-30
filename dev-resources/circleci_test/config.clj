(require '[ctia.dev.test-reporter])

{:test-results-dir "target/test-results"
 :reporters [circleci.test.report/clojure-test-reporter
             ctia.dev.test-reporter/time-reporter]
 :selectors {:es-store :es-store
             :disabled :disabled
             :default (complement
                        (some-fn :disabled
                                 :sleepy
                                 :generative))
             :integration (some-fn
                            :es-store
                            :integration
                            :es-aliased-index)
             :no-gen (complement 
                       (some-fn :disabled
                                :generative))
             :all (complement :disabled)}}
