(ns url-shortener.handler
  (:require
   [url-shortener.shortener :refer [create-short-url handle-redirect]]
   [url-shortener.report :refer [handle-report handle-create-report]]
   [reitit.ring :as ring]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [muuntaja.middleware :refer [wrap-params wrap-format]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [clojure.tools.logging :as log]))

(defn ring-handler [{geoip :geoip redis :redis}]
  (ring/router [["/shorten" {:post create-short-url}]
                ["/report"        {:post handle-create-report}]
                ["/report/:token" {:get handle-report}]]))

(defn default-handler [{geoip :geoip redis :redis}]
  (ring/routes
   (ring/create-resource-handler {:path "/" :root ""})
   (ring/ring-handler (ring/router ["/:path" {:get (partial handle-redirect geoip)
                                              :head (partial handle-redirect geoip)}]))
   (ring/create-default-handler)))

(def middleware [[wrap-defaults api-defaults]
                 wrap-format
                 wrap-params
                 wrap-forwarded-remote-addr])
