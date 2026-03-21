(ns url-shortener.handler
  (:require
   [url-shortener.shortener :refer [shorten handle-redirect handle-redirect-with-legacy]]
   [url-shortener.report :refer [handle-report-page handle-create-report  handle-report-stream]]
   [url-shortener.reports.api :refer [handle-api-report]]
   [url-shortener.reports.link :refer [handle-link-detail]]
   [url-shortener.admin.handler :refer [handle-admin handle-admin-stream]]
   [url-shortener.admin.group-detail :refer [handle-group-detail handle-group-stream]]
   [reitit.ring :as ring]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [muuntaja.middleware :refer [wrap-params wrap-format]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [clojure.tools.logging :as log]))

(defn ring-handler [{geoip :geoip redis :redis pubsub :pubsub}]
  (ring/router [["/" {:post shorten}]
                ["/shorten" {:post shorten}]
                ["/report"        {:post handle-create-report}]
                ["/report/:token/stream"   {:get  (partial handle-report-stream pubsub)}]
                ["/report/:token"          {:get  handle-report-page}]
                ["/api/report/:token" {:get handle-api-report}]
                ["/admin"        {:get handle-admin}]
                ["/admin/stream" {:get (partial handle-admin-stream pubsub)}]
                ["/admin/group/:group-id"         {:get handle-group-detail}]
                ["/admin/group/:group-id/stream"  {:get (partial handle-group-stream pubsub)}]
                ["/admin/link/:path"             {:get  handle-link-detail}]]))

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
