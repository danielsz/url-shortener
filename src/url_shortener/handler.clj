(ns url-shortener.handler
  (:require
   [url-shortener.shortener :refer [shorten handle-redirect handle-redirect-with-legacy]]
   [url-shortener.report :refer [handle-report-page handle-create-report  handle-report-stream]]
   [url-shortener.reports.api :refer [handle-api-report]]   
   [url-shortener.admin.v1.handler :as handler-v1]
   [url-shortener.admin.v1.link-detail :as link-v1]
   [url-shortener.admin.v1.group-detail :as group-v1]
   [url-shortener.admin.v2.group-detail :as  group-v2]   
   [url-shortener.admin.v2.link-detail :as link-v2]
   [url-shortener.admin.v2.homepage :as homepage]
   [reitit.ring :as ring]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [muuntaja.middleware :refer [wrap-params wrap-format]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [clojure.tools.logging :as log]))

(defn ring-handler [{geoip :geoip redis :redis pubsub :pubsub}]
  (ring/router [["/" {:post shorten
                      :get homepage/serve}]
                ["/shorten" {:post shorten}]
                ["/report"        {:post handle-create-report}]
                ["/report/:token/stream"   {:get  (partial handle-report-stream pubsub)}]
                ["/report/:token"          {:get  handle-report-page}]
                ["/api/report/:token" {:get handle-api-report}]
                ["/admin"        {:get handler-v1/handle-admin}]
                ["/admin/v1/stream" {:get (partial handler-v1/handle-admin-stream pubsub)}]
                ["/admin/v1/group/:group-id"         {:get group-v1/handle-group-detail}]
                ["/admin/v1/group/:group-id/stream"  {:get (partial group-v1/handle-group-stream pubsub)}]
                ["/admin/v1/link/:path"             {:get  link-v1/handle-link-detail}]
                ["/admin/v2/group/:group-id"       {:get  group-v2/handle-group-detail}]
                ["/admin/v2/group/:group-id/stream" {:get (partial group-v2/handle-group-stream pubsub)}]
                ["/admin/v2/link/:path"        {:get  link-v2/handle-link-detail}]
                ["/admin/v2/link/:path/stream" {:get (partial link-v2/handle-link-stream pubsub)}]]))

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
