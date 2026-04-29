(ns url-shortener.admin.v2.homepage
  (:require
   [hiccup2.core :as h]
   [hiccup.page :refer [doctype include-css]]
   [ring.util.response :refer [response content-type]]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
   [url-shortener.shared.analytics :as analytics]
   [url-shortener.shared.utils :refer [dev?]]
   [clojure.core.async :as async]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]))



;; ============================================================================
;; tuppu.net · homepage · homepage.clj
;;
;; Layout philosophy
;; ─────────────────
;; Components own decoration: color, border, typography, padding.
;; Layout is composed from Every Layout primitives in the HTML:
;;
;;   .stack     vertical rhythm      (.stack--tight .stack--xs .stack--2xs
;;                                    .stack--m .stack--loose)
;;   .cluster   horizontal grouping  (.cluster--tight .cluster--xs
;;                                    .cluster--s .cluster--m)
;;   .spread    space-between row    (.spread--end)
;;   .grid      auto-fit grid        (--grid-min-size custom property)
;;   .center    max-width centering
;;   .sidebar-layout  sidebar + main
;;
;; Primitive helpers below are thin — they produce correct class strings
;; and accept extra-classes for component decoration.
;; ============================================================================

;; ---------------------------------------------------------------------------
;; Layout primitive helpers
;; ---------------------------------------------------------------------------

(defn stack
  "Vertical stack. variant: :tight :xs :2xs :m :loose
   extra-class: string of additional classes (component decoration)"
  ([children] (stack {} children))
  ([{:keys [variant extra-class]} children]
   (into [:div {:class (cond-> "stack"
                         variant     (str " stack--"   (name variant))
                         extra-class (str " " extra-class))}]
         children)))

(defn cluster
  "Horizontal wrapping cluster. variant: :tight :xs :s :m
   tag: element to use (default :div)"
  ([children] (cluster {} children))
  ([{:keys [variant extra-class tag]
     :or   {tag :div}} children]
   (into [tag {:class (cond-> "cluster"
                         variant     (str " cluster--"  (name variant))
                         extra-class (str " " extra-class))}]
         children)))

(defn spread
  "Space-between row. variant: :end (aligns to flex-end)"
  ([children] (spread {} children))
  ([{:keys [variant extra-class]} children]
   (into [:div {:class (cond-> "spread"
                          variant     (str " spread--"  (name variant))
                          extra-class (str " " extra-class))}]
         children)))

(defn grid
  "Auto-fit grid. min-size: CSS length string, e.g. \"18rem\"
   extra-class: string of additional classes (component decoration)"
  ([children] (grid {} children))
  ([{:keys [min-size gap extra-class]
     :or   {min-size "16rem"}} children]
   (into [:div {:class (cond-> "grid"
                          extra-class (str " " extra-class))
                :style (str "--grid-min-size:" min-size
                            (when gap (str ";--grid-gap:" gap)))}]
         children)))

;; ---------------------------------------------------------------------------
;; Shared atoms
;; ---------------------------------------------------------------------------

(defn live-dot []
  [:span {:class "live-dot live-dot--blue" :data-class "{active: $connected}"}])

(defn section-label [text]
  [:p {:class "section-label"} text])

(defn btn-cta [text]
  [:button {:class "btn btn--cta"} text])

(defn btn-primary
  ([text]                   (btn-primary text nil nil))
  ([text extra-class]       (btn-primary text extra-class nil))
  ([text extra-class href]
   (let [cls (cond-> "btn btn--primary"
               extra-class (str " " extra-class))]
     (if href
       [:a {:href href :class cls} text]
       [:button {:class cls} text]))))

(defn btn-ghost
  ([text]      (btn-ghost text nil))
  ([text href]
   (if href
     [:a {:href href :class "btn btn--ghost"} text]
     [:button {:class "btn btn--ghost"} text])))



;; ---------------------------------------------------------------------------
;; Nav
;; ---------------------------------------------------------------------------

(defn nav []
  [:nav {:class "hp-nav"}
   ;; .center constrains width; .spread pushes wordmark and links apart
   (spread {:extra-class "center"}
     [[:a {:href "/" :class "wordmark"}
       "tuppu" [:span {:class "wordmark__dot"} ".net"]]
      ;; nav links are themselves a cluster
      (cluster {:variant :m :extra-class "hp-nav__links" :tag :ul}
        [[:li [:a {:href "/docs"} "docs"]]
         [:li [:a {:href "/pricing"} "pricing"]]
         [:li [:a {:href "/register" :role "button" :class "btn btn--cta"} "sign in →"]]])])])

;; ---------------------------------------------------------------------------
;; Hero
;; ---------------------------------------------------------------------------


(defn hero []
  [:section {:class "hero"}
   ;; .center constrains width; .stack--loose drives hero section rhythm
   (stack {:variant :loose :extra-class "center"}
     [;; eyebrow: live dot + text side by side
      (cluster {:variant :xs :extra-class "hero__eyebrow"}
        [(live-dot)
         [:span "live · link analytics · platform intelligence"]])

      [:h1 {:class "hero__headline"}
       "Short links." [:br] [:em "Deep signal."]]

      [:p {:class "hero__sub"}
       "Every link you shorten carries an intent — Twitter, Bluesky, an email newsletter. "
       "Tuppu tracks which platform actually delivered, in real time."]

      ;; CTA buttons: cluster wraps on narrow viewports
      (cluster {:variant :xs :extra-class "hero__cta-row"}
        [(btn-primary "start tracking →" nil "/start")
         (btn-ghost "see a live dashboard ↗")])

      ;; Ticker: live stats in a wrapping cluster
      (cluster {:variant :m :extra-class "hero__ticker"}
               [[:span {:class "ticker-stat"}  [:strong {:data-text "$total_clicks.toLocaleString()"} "--"] " " "clicks"]
                [:span {:class "ticker-stat"} [:strong {:data-text "$links.toLocaleString()"} "--"] " " "active links"]
                [:span {:class "ticker-stat"} [:strong {:data-text "$unique_visitors.toLocaleString()"} "--"] " " "unique visitors"]              
                [:span {:class "ticker-stat"} [:strong {:data-text "$groups.toLocaleString()"} "--"] " " "groups"]])])])

;; ---------------------------------------------------------------------------
;; How it works
;; ---------------------------------------------------------------------------

(defn concept-step [{:keys [num title body example]}]
  ;; .concept-step owns decoration; .stack--xs drives internal spacing
  (stack {:variant :xs :extra-class "concept-step"}
    [[:span {:class "concept-step__num"}   num]
     [:h2  {:class "concept-step__title"}  title]
     [:p   {:class "concept-step__body"}   body]
     [:div {:class "concept-step__example"} example]]))

(defn how-it-works []
  [:section {:class "hp-section"}
   (stack {:extra-class "center"}
     [(section-label "how it works")
      ;; .grid collapses below 18rem per cell; .concept-grid overrides --grid-min-size
      (grid {:min-size "18rem" :extra-class "concept-grid"}
        [(concept-step
          {:num     "01 ·"
           :title   "Shorten"
           :body    "Paste your destination URL. We generate a clean, memorable
                     short link ready to share anywhere."
           :example [:span "→ " [:code "tuppu.net/dg4k2"] " → your product page"]})

         (concept-step
          {:num     "02 ·"
           :title   "Target"
           :body    "Tell us where you're publishing: Twitter, Bluesky, a newsletter,
                     your bio link. Tuppu records your intent."
           :example [:span "platforms: " [:code "twitter"] " · "
                           [:code "bluesky"] " · " [:code "email"]]})

         (concept-step
          {:num     "03 ·"
           :title   "Measure"
           :body    "Watch clicks arrive live. See exactly which platform drove traffic —
                     against your declared intent, not a raw referrer string."
           :example [:span "twitter: " [:code "68%"]
                           " · bluesky: " [:code "24%"]
                           " · other: " [:code "8%"]]})])])])

;; ---------------------------------------------------------------------------
;; Dashboard preview
;; ---------------------------------------------------------------------------

(defn- render-hp-platforms [platforms]
  (let [total (apply + (vals platforms))
        rows  (->> platforms (sort-by val >) (take 4))]
    (str (h/html
      [:div {:id "hp-platforms"}
       (for [[platform cnt] rows]
         [:div {:class "platform-row"}
          [:div {:class "cluster cluster--tight plat-name"}
           [:div {:class "plat-dot"
                  :style (str "background:var(--c-" platform ")")}]
           [:span platform]]
          [:div {:class "plat-bar-track"}
           [:div {:class "plat-bar-fill"
                  :style (str "inline-size:"
                              (when (pos? total)
                                (format "%.1f" (* 100.0 (/ cnt total))))
                              "%")}]]
          [:span {:class "plat-count"} (format "%,d" cnt)]])]))))

(defn dashboard-preview []
  [:section {:class "hp-section"}
   (stack {:variant :m :extra-class "center"}
     [;; .spread--end: section-label at bottom-left, note at bottom-right
      (spread {:variant :end :extra-class "preview-header"}
        [(section-label "live dashboard")
         [:span {:class "text-tiny text-muted"} "real data, anonymized"]])

      [:div {:class "preview-frame"}

       ;; browser chrome: cluster puts dots + url in a row
       (cluster {:variant :xs :extra-class "preview-chrome"}
         [[:div {:class "chrome-dots"}      ; fixed-pixel gap, handled in CSS
           [:div {:class "chrome-dot"}]
           [:div {:class "chrome-dot"}]
           [:div {:class "chrome-dot"}]]
          [:span {:class "chrome-url"} "tuppu.net/admin/v2/group/…"]])

       ;; dashboard body sections separated by stack spacing
       (stack {:variant :m :extra-class "preview-body"}
         [;; title row: total + live badge pushed apart; baseline-aligned
          ;; .dash-title-row overrides .spread's align-items to :baseline
          (spread {:extra-class "dash-title-row"}
            [(stack {:variant :tight}
                    [[:div {:class "dash-total" :data-text "$total_clicks.toLocaleString()"}]
                     [:div {:class "dash-label"} "total clicks"]])
             ;; live badge: dot + text
             (cluster {:variant :tight :extra-class "dash-live"}
               [(live-dot)
                [:span "live"]])])

          ;; sub-stats: three stat cells in a wrapping cluster
          (cluster {:variant :s}
            [(stack {:variant :tight :extra-class "dash-stat"}
               [[:span {:class "dash-stat__val" :data-text "$unique_visitors.toLocaleString()"}]
                [:span {:class "dash-stat__lbl"} "visitors"]])
             (stack {:variant :tight :extra-class "dash-stat"}
                    [[:span {:class "dash-stat__val" :data-text "$links.toLocaleString()"}]
                     [:span {:class "dash-stat__lbl"} "links"]])
             (stack {:variant :tight :extra-class "dash-stat"}
                    [[:span {:class "dash-stat__val"
                             :data-text "($unique_visitors > 0 ? ($total_clicks / $unique_visitors).toFixed(1) : '—') + '×'"}]
                     [:span {:class "dash-stat__lbl"} "clicks / visitor"]])])

          ;; platform breakdown: tight stack of rows
          [:div.stack.stack--tight
           [:p {:class "platform-section-title"} "by platform"]
           [:div {:id "hp-platforms"}]]])]])])

;; ---------------------------------------------------------------------------
;; Features
;; ---------------------------------------------------------------------------

(defn feature-item [{:keys [title body]}]
  ;; .feature-item owns decoration; .stack--2xs drives internal spacing
  (stack {:variant :2xs :extra-class "feature-item"}
    [[:h3 {:class "feature-item__title"} title]
     [:p  {:class "feature-item__body"}  body]]))

(def ^:private feature-data
  [{:title "Platform attribution"
    :body  "Declare your intended targets when you shorten. See clicks confirmed
            against those targets, not just a raw referrer log."}
   {:title "Live streaming"
    :body  "The dashboard updates in real time via server-sent events.
            No polling, no refresh, no lag. Watch your campaign land."}
   {:title "Group analytics"
    :body  "Organise links into groups — a campaign, a product, a season.
            Compare performance across the entire group at a glance."}
   {:title "No noise"
    :body  "A dashboard designed after Bertin's visual encoding principles.
            Every pixel encodes meaning. Nothing decorative, nothing redundant."}])

(defn features []
  [:section {:class "hp-section"}
   (stack {:extra-class "center"}
     [(section-label "built for intent")
      ;; .features-grid overrides --grid-min-size to 16rem
      (grid {:min-size "16rem" :extra-class "features-grid"}
        (map feature-item feature-data))])])

;; ---------------------------------------------------------------------------
;; CTA
;; ---------------------------------------------------------------------------

(defn cta []
  [:section {:class "hp-section hp-section--no-border hp-section--center"}
   [:div {:class "center"}
    ;; .cta-stack is a centred stack — stays in homepage.css because
    ;; .stack intentionally does not centre its children
    [:div {:class "cta-stack"}
     (section-label "get started")
     [:h2 {:class "cta-headline"}
      "Know where your audience actually comes from."]
     (btn-primary "create your first link →" "btn--primary-lg" "/start")
     [:p {:class "text-muted text-tiny"} "free to start · no credit card"]]]])

;; ---------------------------------------------------------------------------
;; Footer
;; ---------------------------------------------------------------------------

(defn footer []
  [:footer {:class "hp-footer"}
   ;; .center constrains width; .spread pushes three children apart
   (spread {:extra-class "center hp-footer__inner"}
     [[:span {:class "wordmark"}
       "tuppu" [:span {:class "wordmark__dot"} ".net"]]
      (cluster {:variant :m :extra-class "hp-footer__links" :tag :ul}
        [[:li [:a {:href "/coming-soon"} "privacy"]]
         [:li [:a {:href "/coming-soon"} "terms"]]
         [:li [:a {:href "/coming-soon"} "api"]]
         [:li [:a {:href "/coming-soon"} "status"]]])
      [:span "Q = quantitative · O = ordinal · N = nominal"]])])


;; ---------------------------------------------------------------------------
;; Page
;; ---------------------------------------------------------------------------

(defn head []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
   [:title "tuppu.net — link analytics with platform intelligence"]
   (if true ;(dev?)
     (include-css "/css/v2/tokens.css"
                  "/css/v2/reset.css"
                  "/css/v2/primitives.css"
                  "/css/v2/components.css"
                  "/css/v2/homepage.css")
     (include-css "/css/styles.min.css"))
   [:script {:type "module"
             :src  "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.8/bundles/datastar.js"}]])

(defn page []
  (str
   (h/html {:mode :html}
     (doctype :html5)
     [:html {:lang "en"}
      (head)
      [:body {:data-signals "{connected:false,total_clicks:0,unique_visitors:0,links:0,groups:0}"
              :data-on:datastar-fetch "el === evt.detail.el && ((evt.detail.type.startsWith('datastar') && ($connected = true)) || (['retrying', 'error', 'finished'].includes(evt.detail.type) && ($connected = false)))"
              :data-init    "@get('/stream')"}
       (nav)
       (hero)
       (how-it-works)
       (dashboard-preview)
       (features)
       (cta)
       (footer)]])))


(defn handle-homepage-stream [pubsub request]
  (let [ch-atom  (atom nil)
        cleanup! (fn []
                   (when-let [ch @ch-atom]
                     (async/unsub (:publication pubsub) :analytics-update ch)
                     (async/close! ch)
                     (reset! ch-atom nil)))]
    (->sse-response request
      {on-open
       (fn [sse]
         (let [ch (async/chan (async/sliding-buffer 10))
               stats (analytics/global-stats)
               signals (select-keys stats [:total_clicks :unique_visitors :groups :links])]
           (reset! ch-atom ch)
           (async/sub (:publication pubsub) :analytics-update ch)
           (try
             (d*/patch-signals! sse (json/generate-string signals))
             (d*/patch-elements! sse (render-hp-platforms (:platforms stats)))
             (loop []
               (when-let [_ (async/<!! ch)]
                 (try
                   (let [stats (analytics/global-stats)
                         signals (select-keys stats [:total_clicks :unique_visitors :groups :links])]
                     (d*/patch-signals! sse (json/generate-string signals))
                     (d*/patch-elements! sse (render-hp-platforms (:platforms stats))))
                   (catch Exception e (log/error "homepage push failed" (.getMessage e))))
                 (recur)))
             (catch Exception e (log/error "homepage stream failed" (.getMessage e))))))
       on-close
       (fn [sse status]
         (log/debug "homepage stream closed" status)
         (cleanup!))})))


(defn serve [_]
  (-> (response (page))
     (content-type "text/html")))
