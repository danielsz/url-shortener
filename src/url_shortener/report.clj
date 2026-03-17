(ns url-shortener.report
  (:require
   [hiccup2.core :as h]
   [hiccup.page :refer [html5]]
   [taoensso.carmine :as redis]
   [url-shortener.utils :refer [generate-token]]
   [clojure.tools.logging :as log]
   [url-shortener.schema :refer [reports-key report-key ips-key referrers-key daily-key countries-key TTL-REPORT]]
   [ring.util.response :refer [response bad-request header status not-found redirect]]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open]]
   [clojure.core.async :as a :refer [<!! >!! >! <! chan thread timeout go sub unsub sliding-buffer close!]])
  (:import
   java.time.Instant))

(defn handle-create-report [{{:keys [user path]} :params}]
  (let [owner (redis/wcar nil (redis/hget path "user"))]
    (if (= owner user)
      (let [token (generate-token)]
        (redis/wcar nil
                    (redis/hset  (report-key token) "owner" user "path" path "created" (.getEpochSecond (Instant/now)))
                    (redis/expire (report-key token) TTL-REPORT)
                    (redis/sadd   (reports-key path) token))
        (response (str (System/getProperty "shortener.service") "report/" token)))
      (status 403))))

(defn- resolve-report [token]
  (let [r (redis/wcar nil (redis/hgetall (report-key token)))]
    (when (seq r)
      (apply hash-map r))))

(defn handle-api-report [{{token :token} :path-params}]
  (let [report (resolve-report token)]
    (if (seq report)
      (let [path (get report "path")
            [clicks url unique-ips daily referrers countries]
            (redis/wcar nil
              (redis/hget    path "clicks")
              (redis/hget    path "url")
              (redis/zcard   (ips-key path))
              (redis/hgetall (daily-key path))
              (redis/lrange  (referrers-key path) 0 -1)
              (redis/hgetall (str path ":countries")))]
        {:status  200
         :body    {:path       path
                   :url        url
                   :clicks     clicks
                   :unique-ips unique-ips
                   :daily      (apply hash-map daily)
                   :referrers  referrers
                   :countries  (apply hash-map countries)}})
      {:status 404 :body "Report not found or expired"})))

;; -- SSE fragments ------------------------------------------------------------

(defn- sparkline [daily-map]
  (let [days   (sort (keys daily-map))
        values (map #(parse-long (get daily-map %)) days)
        max-v  (apply max 1 values)]
    [:div.sparkline
     (for [[day v] (map vector days values)]
       [:div.sparkline__bar
        {:style (str "--pct:" (int (* 100 (/ v max-v))))
         :title (str day ": " v)}])]))

(defn- render-stats [path]
  (let [[clicks unique-ips]
        (redis/wcar nil
          (redis/hget  path "clicks")
          (redis/zcard (ips-key path)))]
    (str (h/html
      [:div {:id "stats" :class "switcher"}
       [:div.box.stack
        [:span.stat__label "Total Clicks"]
        [:span.stat__value (or clicks "0")]]
       [:div.box.stack
        [:span.stat__label "Unique Visitors"]
        [:span.stat__value (or unique-ips 0)]]]))))

(defn- render-chart [path]
  (let [daily (redis/wcar nil (redis/hgetall (daily-key path)))]
    (str (h/html
      [:div {:id "chart" :class "box stack"}
       [:span.stat__label "Daily Clicks"]
       (if (seq daily)
         (sparkline (apply hash-map daily))
         [:span.stat__muted "No data yet"])]))))

(defn- render-countries [path]
  (let [raw    (redis/wcar nil (redis/hgetall (countries-key path)))
        totals (update-vals (if (seq raw) (apply hash-map raw) {}) parse-long)
        sorted (sort-by val > totals)
        max-v  (apply max 1 (map val sorted))]
    (str (h/html
      [:div {:id "countries" :class "box stack"}
       [:span.stat__label "Top Countries"]
       (if (seq sorted)
         (for [[country cnt] (take 10 sorted)]
           [:div.country-row
            [:span country]
            [:span cnt]
            [:div.country-row__bar
             {:style (str "--pct:" (int (* 100 (/ cnt max-v))))}]])
         [:span.stat__muted "No data yet"])]))))

(defn- render-referrers [path]
  (let [refs (redis/wcar nil (redis/lrange (referrers-key path) 0 19))]
    (str (h/html
      [:div {:id "referrers" :class "box stack"}
       [:span.stat__label "Recent Referrers"]
       (if (seq refs)
         (for [r refs]
           [:div.referrer-row r])
         [:span.stat__muted "No data yet"])]))))

;; -- Page shell ---------------------------------------------------------------

(defn- report-page [token path url description]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Link Analytics"]
     [:link {:rel "stylesheet" :href "/css/tokens.css"}]
     [:link {:rel "stylesheet" :href "/css/layout.css"}]
     [:link {:rel "stylesheet" :href "/css/report.css"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
    [:body
     [:div.center
      [:div.stack
       {:data-signals "{connected: false}"
        :data-init    (str "@get('/report/" token "/stream')")}
       [:div.box.stack
        [:span.stat__label "Short Link"]
        [:a {:href (str (System/getProperty "shortener.service") path)}
         (str (System/getProperty "shortener.service") path)]]
       [:div.box.stack
        [:span.stat__label "Destination"]
        [:a {:href url} url]]
       (when (seq description)
         [:div.box
          [:span.report__description description]])
       [:div.cluster
        [:span.live-dot {:data-class "{active: $connected}"}]
        [:span.stat__label "Live"]]
       [:div {:id "stats"}]
       [:div {:id "chart"}]
       [:div {:id "countries"}]
       [:div {:id "referrers"}]]]]))

(defn handle-report [{{token :token} :path-params}]
  (if-let [{path "path"} (resolve-report token)]
    (let [[url description]
          (redis/wcar nil
            (redis/hget path "url")
            (redis/hget path "description"))]
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (report-page token path url description)})
    (not-found "Report not found or expired")))

(defn handle-report-stream [pubsub {{token :token} :path-params :as request}]
  (if-let [{path "path"} (resolve-report token)]
    (->sse-response request
      {on-open
       (fn [sse]
         (let [ch       (chan (sliding-buffer 10))
               cleanup! (fn [e]
                          (log/debug "report stream closed" (.getMessage e))
                          (unsub (:publication pubsub) :click ch)
                          (close! ch))]
           (sub (:publication pubsub) :click ch)
           (try
             (d*/patch-signals! sse "{\"connected\": true}")
             (d*/patch-elements! sse (render-stats path))
             (d*/patch-elements! sse (render-chart path))
             (d*/patch-elements! sse (render-countries path))
             (d*/patch-elements! sse (render-referrers path))
             (loop []
               (when-let [event (<!! ch)]
                 (when (= (:path event) path)
                   (try
                     (d*/patch-elements! sse (render-stats path))
                     (d*/patch-elements! sse (render-chart path))
                     (d*/patch-elements! sse (render-countries path))
                     (d*/patch-elements! sse (render-referrers path))
                     (catch Exception e (cleanup! e))))
                 (recur)))
             (catch Exception e (cleanup! e)))))})
    (not-found "Report not found or expired")))


