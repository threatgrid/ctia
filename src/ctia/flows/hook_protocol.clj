(ns ctia.flows.hook-protocol
  "Declare the protocol without any other function around to prevent reload error.
  See https://nelsonmorris.net/2015/05/18/reloaded-protocol-and-no-implementation-of-method.html")

(defprotocol Hook
  "A hook is mainly a function"
  (init [this])
  (handle [this
           type-name
           stored-object
           prev-object])
  (destroy [this]))
