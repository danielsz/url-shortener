(ns url-shortener.handler
  (:require
   [url-shortener.shortener :refer [shorten handle-redirect handle-redirect-with-legacy]]
   [url-shortener.report :refer [handle-report handle-create-report]]
   [url-shortener.admin.handler :refer [handle-admin handle-admin-stream]]
   [reitit.ring :as ring]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [muuntaja.middleware :refer [wrap-params wrap-format]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [clojure.tools.logging :as log]))

(defn ring-handler [{geoip :geoip redis :redis}]
  (ring/router [["/" {:post shorten}]
                ["/shorten" {:post shorten}]
                ["/report"        {:post handle-create-report}]
                ["/report/:token" {:get handle-report}]
                ["/admin"        {:get handle-admin}]
                ["/admin/stream" {:get handle-admin-stream}]]))

(defn default-handler [component]
  (ring/routes
   (ring/create-resource-handler {:path "/" :root "public"})
   (ring/ring-handler (ring/router ["/:path" {:get (partial handle-redirect-with-legacy component)
                                              :head (partial handle-redirect-with-legacy component)}]))
   (ring/create-default-handler)))

(def middleware [[wrap-defaults api-defaults]
                 wrap-format
                 wrap-params
                 wrap-forwarded-remote-addr])
