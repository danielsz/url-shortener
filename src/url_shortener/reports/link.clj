(ns url-shortener.reports.link
  (:require   
   [hiccup2.core :as h]
   [hiccup.page :refer [html5]]
   [taoensso.carmine :as redis]
   [clojure.tools.logging :as log]
   [url-shortener.shared.utils :refer [generate-token epoch-now]]
   [url-shortener.schema :refer [reports-key report-key ips-key referrers-key daily-key weekly-key monthly-key countries-key TTL-REPORT]]
   [ring.util.response :refer [response status redirect not-found]]
   [url-shortener.shared.sse-fragments :refer [sparkline]]))


;; -- Page shell ---------------------------------------------------------------

(defn link-report-page [token path url description]
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
        :data-init    (str "@get('/report/" token "/stream')")
        :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}
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



(defn create-link-report! [owner-id path]
  (let [actual-owner (redis/wcar nil (redis/hget path "owner-id"))]
    (if (= actual-owner owner-id)
      (let [token (generate-token)]
        (redis/wcar nil
          (redis/hset    (report-key token)
                         "type"      "link"
                         "target-id" path
                         "owner-id"  owner-id
                         "created"   (epoch-now))
          (redis/expire  (report-key token) TTL-REPORT)
          (redis/sadd    (reports-key path) token))
        (response (str (System/getProperty "shortener.service") "report/" token)))
      (status 403))))


(defn handle-link-report [token report]
  (let [path        (get report "target-id")
        [url description]
        (redis/wcar nil
          (redis/hget path "url")
          (redis/hget path "description"))]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (link-report-page token path url description)}))


;; -- SSE fragments ------------------------------------------------------------



(defn render-stats [path]
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
  (let [[daily weekly monthly]
        (redis/wcar nil
          (redis/hgetall (daily-key path))
          (redis/hgetall (weekly-key path))
          (redis/hgetall (monthly-key path)))]
    (str (h/html
      [:div {:id "chart" :class "stack"}
       [:div.box.stack
        [:span.stat__label "Daily"]
        (if (seq daily)
          (sparkline (apply hash-map daily))
          [:span.stat__muted "No data yet"])]
       [:div.box.stack
        [:span.stat__label "Weekly"]
        (if (seq weekly)
          (sparkline (apply hash-map weekly))
          [:span.stat__muted "No data yet"])]
       [:div.box.stack
        [:span.stat__label "Monthly"]
        (if (seq monthly)
          (sparkline (apply hash-map monthly))
          [:span.stat__muted "No data yet"])]]))))


(defn render-countries [path]
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

(defn render-referrers [path]
  (let [refs (redis/wcar nil (redis/lrange (referrers-key path) 0 19))]
    (str (h/html
      [:div {:id "referrers" :class "box stack"}
       [:span.stat__label "Recent Referrers"]
       (if (seq refs)
         (for [r refs]
           [:div.referrer-row r])
         [:span.stat__muted "No data yet"])]))))


(defn handle-link-detail [{{path :path} :path-params}]
  (if-let [owner-id (redis/wcar nil (redis/hget path "owner-id"))]
    (let [existing-token (redis/wcar nil (redis/srandmember (reports-key path)))
          valid-token    (when existing-token
                           (when (pos? (redis/wcar nil (redis/exists (report-key existing-token))))
                             existing-token))]
      (when (and existing-token (not valid-token))
        (redis/wcar nil (redis/srem (reports-key path) existing-token)))
      (if valid-token
        (redirect (str "/report/" valid-token))
        (let [token (generate-token)]
          (redis/wcar nil
            (redis/hset    (report-key token) "type"      "link"
                                              "target-id" path
                                              "owner-id"  owner-id
                                              "created"   (epoch-now))
            (redis/expire  (report-key token) TTL-REPORT)
            (redis/sadd    (reports-key path) token))
          (redirect (str "/report/" token)))))
    (not-found "Link not found")))



