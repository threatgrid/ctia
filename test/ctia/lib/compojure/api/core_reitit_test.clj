(ns ctia.lib.compojure.api.core-reitit-test
  (:require [ctia.lib.compojure.api.core-reitit :as sut]
            [ctia.http.middleware.auth :as mid]
            [reitit.ring :as ring]
            [clojure.test :refer [deftest is]]
            [ring.swagger.json-schema :refer [describe]]
            [schema.core :as s]))

(defmacro with-deterministic-gensym [& body]
  `(with-bindings {#'sut/*gensym* (let [a# (atom -1)]
                                    (fn [s#]
                                      {:pre [(string? s#)]
                                       :post [(symbol? ~'%)]}
                                      (symbol (str s# "__" (swap! a# inc')))))}
     (do ~@body)))

(defn dexpand-1 [form]
  (with-deterministic-gensym
    (macroexpand-1 form)))

(deftest routes-test
  (is (= [["/blah" identity] ["/foo" identity]]
         (sut/routes ["/blah" identity] ["/foo" identity]))))

(deftest middleware-test
  (is (= ["" {:middleware [[:some 1] [:middleware 2]]}
          [["/blah" identity] ["/foo" identity]]]
         (sut/middleware [[:some 1] [:middleware 2]]
           ["/blah" identity]
           ["/foo" identity]))))

(deftest context-test
  (is (= ["/my-route" [identity]]
         (sut/context
           "/my-route" []
           identity)))
  (is (= ["/my-route"
          {:swagger {:tags #{:foo :bar}}}
          [identity]]
         (sut/context
           "/my-route" []
           :tags #{:foo :bar}
           identity)))
  (is (= ["/my-route"
          {:swagger {:tags 'tags-are-compile-time-literals}}
          [identity]]
         (let [tags-are-compile-time-literals #{:foo :bar}]
           (sut/context
             "/my-route" []
             :tags tags-are-compile-time-literals
             identity))))
  (is (= ["/my-route"
          {:middleware [[mid/wrap-capabilities :create-incident]]}
          [identity]]
         (sut/context
           "/my-route" []
           :capabilities :create-incident
           identity)))
  (is (= ["/my-route"
          {:middleware [[mid/wrap-capabilities :create-incident]]}
          [identity]]
         (let [capabilities-are-expressions :create-incident]
           (sut/context
             "/my-route" []
             :capabilities capabilities-are-expressions
             identity))))
  (is (= ["/my-route"
          {:swagger {:description "a description"}}
          [identity]]
         (sut/context
           "/my-route" []
           :description "a description"
           identity)))
  (is (= ["/my-route"
          {:swagger {:description "a description"}}
          [identity]]
         (let [descriptions-are-expressions "a description"]
           (sut/context
             "/my-route" []
             :description descriptions-are-expressions
             identity))))
  (is (= ["/my-route"
          {:swagger {:summary "a summary"}}
          [identity]]
         (sut/context
           "/my-route" []
           :summary "a summary"
           identity)))
  (is (= ["/my-route"
          {:swagger {:summary "a summary"}}
          [identity]]
         (let [summarys-are-expressions "a summary"]
           (sut/context
             "/my-route" []
             :summary summarys-are-expressions
             identity))))
  (is (= ["/my-route"
          {:responses {200 {:schema {:a (s/enum "schema")}}}}
          [identity]]
         (sut/context
           "/my-route" []
           :responses {200 {:schema {:a (s/enum "schema")}}}
           identity)))
  (is (= ["/my-route"
          {:responses {200 {:schema {:a (s/enum "schema")}}}}
          [identity]]
         (let [responses-are-expressions {200 {:schema {:a (s/enum "schema")}}}]
           (sut/context
             "/my-route" []
             :responses responses-are-expressions
             identity)))))

(deftest get-test
  (is (= '["/my-route" {:get (clojure.core/fn [req__0] (clojure.core/let [] (do {:status 200})))}]
         (dexpand-1
           '(sut/GET "/my-route" []
                     {:status 200}))))
  (is (= {:status 200
          :body "here"}
         (let [app (ring/ring-handler
                     (ring/router
                       (sut/GET "/my-route" []
                                {:status 200
                                 :body "here"})))]
           (app {:request-method :get
                 :uri "/my-route"})))))
