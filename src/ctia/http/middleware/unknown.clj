(ns ctia.http.middleware.unknown
  (:require [ring.util.response :refer [not-found]]))

(defn err-html
  "Construct an HTML body for a 404 response."
  ([] (err-html nil))
  ([uri]
   (str "<html> <head> "
        "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>"
        " <title>Error 404 </title> </head> "
       "<body> <h2>HTTP ERROR: 404</h2> <p>"
       (when uri
         (str "Problem accessing " uri ". "))
       "Reason: <pre>    Not Found</pre></p> </body> </html>")))

(defn wrap-unknowns
  "When a request results in a nil response, then this indicates that no route handled it.
  Unlike compojure.route/not-found, this can include request details.
  Currently unused, but necessary if previous 404 behavior is desired."
  [handler]
  (fn [request]
    (or (handler request)
        (not-found (err-html (:uri request))))))
