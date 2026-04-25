(ns url-shortener.shared.pages.v2.dashboard
  (:require
   [hiccup.page :refer [html5 include-css]]
   [url-shortener.shared.utils :refer [display-name dev?]]))

(defn- head-links []
  (if true ; (dev?)
    (include-css "/css/v2/tokens.css"
                 "/css/v2/reset.css"
                 "/css/v2/primitives.css"
                 "/css/v2/components.css"
                 "/css/v2/dashboard.css")
        (include-css "/css/styles.min.css")))

(defn group-dashboard-page [group-id stream-url back-link]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str (display-name group-id) " — Analytics")]
     (head-links)
     [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.js"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
    [:body
     [:div {:data-signals "{connected:false,stats:{total_clicks:0,unique_visitors:0,links:0},countries:{},platforms:{},daily:{},links:[],feed:[],confirmed:{}}"
            :data-init    stream-url
            :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}
      [:main.center
       [:div.stack
        ;; Header: .spread--end handles space-between layout;
        ;; .header adds padding + border decoration
        [:header.spread.spread--end.header
         [:div.stack.stack--tight
          [:div.cluster.cluster--tight
           [:span.label-tiny "tuppu.net · " (display-name group-id) " ·"]
           [:span.live-dot {:data-class "{active: $connected}"}]
           [:span.label-tiny {:data-text "$connected ? 'live' : 'connecting…'"} "connecting..."]]
          [:div.total-clicks {:data-text "$stats.total_clicks.toLocaleString()"}]
          [:p.sub-label "total clicks"]]
         [:div.header-right "Q = quantitative · O = ordinal · N = nominal"]]

        ;; Two-column layout: sidebar-layout (row-reverse: side declared first in DOM,
        ;; but rendered right; main grows to fill remaining space)
        [:div.sidebar-layout
         [:div.sidebar-layout__side
          [:div.pnl.stack
           [:div.pnl-title "Overview"]
           [:div.switcher.switcher--stat
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.total_clicks.toLocaleString()"}]
             [:div.stat-lbl "Clicks"]]
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.unique_visitors.toLocaleString()"}]
             [:div.stat-lbl "Visitors"]]
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.links.toLocaleString()"}]
             [:div.stat-lbl "Links"]]]]
          [:div.pnl.stack
           [:div.pnl-title "Top countries"]
           [:div.stack.stack--xs {:id "countries-panel"}]]
          [:div.pnl.stack
           [:div.pnl-title "Sources"]
           [:div.stack.stack--xs {:id "platforms-panel"}]
           [:p.note "No pie — angle encodes Q data poorly."]]
          [:div.pnl.stack.pnl--confirmed.pnl--conditional
           {:id        "confirmed-pnl"
            :data-show "$confirmed && Object.keys($confirmed).length > 0"}
           [:div.pnl-title "Platform confirmation"]
           [:span.enc-tag "Observed platform ∈ intended targets"]
           [:div.stack.stack--xs {:id "confirmed-panel"}]]]

         [:div.sidebar-layout__main
          [:div.pnl.stack {:id "ts-panel" :hidden true}
           [:div.pnl-title "Clicks over time — daily"]
           [:div.chart-wrap
            [:canvas {:id "tsChart"}]]]
          [:div.pnl.stack.pnl--conditional
           {:id        "feed-pnl"
            :data-show "$feed && $feed.length > 0"}
           [:div.pnl-title "Recent clicks"]
           [:div.stack.stack--xs {:id "feed-panel"}]]
          [:div.pnl.stack
           [:div.pnl-title "Links by volume"]
           [:div.stack.stack--xs {:id "links-panel"}]]]]

        (when back-link
          [:div.back-link [:a {:href (second back-link)} (first back-link)]])]]]
     [:script {:src "/js/dashboard.js"}]]))

(defn link-dashboard-page [path url description stream-url back-link]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str (or description url) " — Analytics")]
     (head-links)
     [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.js"}]
     [:script {:type "module"
               :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]]
    [:body
     [:div {:data-signals "{connected:false,stats:{total_clicks:0,unique_visitors:0},countries:{},platforms:{},daily:{},feed:[],confirmed:{}}"
            :data-init    stream-url
            :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"}
      [:main.center
       [:div.stack
        [:header.spread.spread--end.header
         [:div.stack.stack--tight
          [:div.cluster.cluster--tight
           [:span.label-tiny "tuppu.net · " path " ·"]
           [:span.live-dot {:data-class "{active: $connected}"}]
           [:span.label-tiny {:data-text "$connected ? 'live' : 'connecting…'"} "connecting..."]]
          [:div.total-clicks {:data-text "$stats.total_clicks.toLocaleString()"}]
          [:p.sub-label "total clicks"]]
         [:div.header-right "Q = quantitative · O = ordinal · N = nominal"]]

        [:div.sidebar-layout
         [:div.sidebar-layout__side
          [:div.pnl.stack
           [:div.pnl-title "Overview"]
           [:div.switcher.switcher--stat
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.total_clicks.toLocaleString()"}]
             [:div.stat-lbl "Clicks"]]
            [:div.stat-cell
             [:div.stat-val {:data-text "$stats.unique_visitors.toLocaleString()"}]
             [:div.stat-lbl "Visitors"]]]]
          [:div.pnl.stack
           [:div.pnl-title "Top countries"]
           [:div.stack.stack--xs {:id "countries-panel"}]]
          [:div.pnl.stack
           [:div.pnl-title "Sources"]
           [:div.stack.stack--xs {:id "platforms-panel"}]
           [:p.note "No pie — angle encodes Q data poorly."]]
          [:div.pnl.stack.pnl--confirmed.pnl--conditional
           {:id        "confirmed-pnl"
            :data-show "$confirmed && Object.keys($confirmed).length > 0"}
           [:div.pnl-title "Platform confirmation"]
           [:span.enc-tag "Observed platform ∈ intended targets"]
           [:div.stack.stack--xs {:id "confirmed-panel"}]]]

         [:div.sidebar-layout__main
          [:div.pnl.stack {:id "ts-panel" :hidden true}
           [:div.pnl-title "Clicks over time — daily"]
           [:div.chart-wrap
            [:canvas {:id "tsChart"}]]]
          [:div.pnl.stack.pnl--conditional
           {:id        "feed-pnl"
            :data-show "$feed && $feed.length > 0"}
           [:div.pnl-title "Recent clicks"]
           [:div.stack.stack--xs {:id "feed-panel"}]]
          [:div.pnl.stack
           [:div.pnl-title "Destination"]
           [:div.stack.stack--xs
            [:a.link-url {:href url :target "_blank"} url]
            (when (seq description)
              [:div.link-desc description])]]]]

        (when back-link
          [:div.back-link [:a {:href (second back-link)} (first back-link)]])]]]
     [:script {:src "/js/dashboard.js"}]]))




