(ns ctia.flows.hook-protocol
  "Declare the protocol without any other function around to prevent reload error.
  See https://nelsonmorris.net/2015/05/18/reloaded-protocol-and-no-implementation-of-method.html")

(defprotocol Hook
  "A hook is mainly a function"
  ;; `init` Should be function which could have stateful effect.
  ;; The function will be called during initialization of the application.
  ;; Its return value will be discarded.
  ;;
  ;; Typing : `IO ()`, an IO effect that returns nothing.
  (init [this])
  ;; The Handle function will take the name of the type of the object
  ;; an object of this type
  ;; and optionally it could contains a previous object.
  ;; typically for the update hook the `prev-object`
  ;; will contains the previous value of the object
  ;;
  ;; Typing: handle :- X
  ;;     [type-name will be :X
  ;;      object :- X
  ;;      prev-object :- (s/maybe X)]
  ;; or using Haskell notation: `handle :- a -> Keyword -> b -> Maybe b -> IO b`
  (handle [this
           object
           prev-object])
  ;; `destroy` Should be function which could have stateful effect.
  ;; It will be called at the shutdown of the application.
  ;; This function will typically be used to free some resources for example.
  ;; Typing: `IO ()`, an IO effect that returns nothing
  (destroy [this]))
