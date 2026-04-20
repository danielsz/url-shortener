(ns url-shortener.admin.v2.link-detail
  (:require
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
    [url-shortener.shared.analytics :as analytics]
    [taoensso.carmine :as redis]
    [cheshire.core :as json]
    [url-shortener.shared.pages.v2 :as pages]))


(defn- link-detail-page [path]
  (let [[url desc] (redis/wcar nil (redis/hget path "url") (redis/hget path "description"))]
    (pages/link-dashboard-page path url desc
      (str "@get('/admin/v2/link/" path "/stream')")
      ["← Admin Dashboard" "/admin"])))


(defn handle-link-detail [{{path :path} :path-params}]
  {:status  200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-store"}
   :body    (link-detail-page path)})

(defn handle-link-stream [pubsub {{path :path} :path-params :as request}]
  (let [ch-atom   (atom nil)
        feed-atom (atom [])
        cleanup!  (fn []
                    (when-let [ch @ch-atom]
                      (async/unsub (:publication pubsub) :analytics-update ch)
                      (async/close! ch)
                      (reset! ch-atom nil)))]
    (->sse-response request
      {on-open
       (fn [sse]
         (let [ch (async/chan (async/sliding-buffer 10))]
           (reset! ch-atom ch)
           (async/sub (:publication pubsub) :analytics-update ch)
           (try
             (d*/patch-signals! sse (json/generate-string (analytics/link-signals path)))
             (loop []
               (when-let [event (async/<!! ch)]
                 (when (= (:path event) path)
                    (try
                     (let [item {:short (str (System/getProperty "shortener.service") path)
                                 :url   (redis/wcar nil (redis/hget path "url"))
                                 :time  (-> (java.time.LocalTime/now) (.truncatedTo java.time.temporal.ChronoUnit/SECONDS) str)}]
                       (swap! feed-atom #(vec (take 5 (cons item %)))))
                     (d*/patch-signals! sse (json/generate-string (assoc (analytics/link-signals path) :feed @feed-atom)))
                     (catch Exception e (log/error "push failed" (.getMessage e)))))
                 (recur)))
             (catch Exception e (log/error "stream failed for" path (.getMessage e))))))
       on-close
       (fn [sse status]
         (log/debug "link stream v2 closed" path status)
         (cleanup!))})))
