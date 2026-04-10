(ns ctia.test-helpers.otel
  "Test helpers for OpenTelemetry span capture."
  (:import
   [io.opentelemetry.api.common AttributeKey Attributes]
   [io.opentelemetry.api.trace Span SpanContext StatusCode]))

(defn mock-span
  "Creates a Span that captures setAttribute calls into the given atom."
  [captured-atom]
  (reify Span
    (^Span setAttribute [this ^AttributeKey key value]
      (swap! captured-atom assoc (.getKey key) value)
      this)
    (^Span addEvent [this ^String _name] this)
    (^Span addEvent [this ^String _name ^Attributes _attrs] this)
    (^Span setStatus [this ^StatusCode _code] this)
    (^Span setStatus [this ^StatusCode _code ^String _desc] this)
    (^Span recordException [this ^Throwable _ex] this)
    (^Span recordException [this ^Throwable _ex ^Attributes _attrs] this)
    (^Span updateName [this ^String _name] this)
    (end [_this])
    (^SpanContext getSpanContext [_this] (SpanContext/getInvalid))
    (^boolean isRecording [_this] true)))

(defmacro with-mock-span
  "Binds `captured-sym` to an atom that collects setAttribute calls from a
  mock span installed as the current span for the duration of `body`."
  [captured-sym & body]
  `(let [~captured-sym (atom {})
         span# (mock-span ~captured-sym)
         scope# (.makeCurrent span#)]
     (try
       ~@body
       (finally
         (.close scope#)))))
