(ns url-shortener.admin.v2.coming-soon
  (:require
   [hiccup2.core :as h]
   [hiccup.page :refer [doctype include-css]]
   [ring.util.response :refer [response content-type]]))

;; ============================================================================
;; tuppu.net · /coming-soon · coming_soon.clj
;;
;; A single holding page for all unimplemented nav targets:
;; /register  /login  /docs  /pricing
;;
;; Layout: .cover centres .cover__principal vertically (full-height flex col).
;; Card:   .start-card (from start.css) — max 44rem, padded, bordered surface.
;; Nav:    wordmark-only — no sign-in/sign-up links (they are this page).
;; ============================================================================


;; ---------------------------------------------------------------------------
;; Minimal nav — wordmark only, no outbound action links
;; ---------------------------------------------------------------------------

(defn- nav []
  [:nav {:class "hp-nav"}
   [:div {:class "spread center"}
    [:a {:href "/" :class "wordmark"}
     "tuppu" [:span {:class "wordmark__dot"} ".net"]]]])


;; ---------------------------------------------------------------------------
;; Page
;; ---------------------------------------------------------------------------

(defn page []
  (str
   (h/html {:mode :html}
     (doctype :html5)
     [:html {:lang "en"}
      [:head
       [:meta {:charset "UTF-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
       [:title "coming soon — tuppu.net"]
       (include-css "/css/v2/tokens.css"
                    "/css/v2/reset.css"
                    "/css/v2/primitives.css"
                    "/css/v2/components.css"
                    "/css/v2/start.css")]
      [:body {:class "cover"}
       (nav)
       [:main {:class "cover__principal"}
        [:div.center
         [:div {:class "start-card"}

          ;; Header ────────────────────────────────────────────────────────
          [:div {:class "stack stack--xs"}
           [:span {:class "field__label"} "under construction"]
           [:h1 {:class "start-card__title"} "Built in the open."]
           [:p {:class "start-card__sub"}
            "tuppu.net is moving fast and being shaped in public. "
            "This is the right moment to make your voice heard — "
            "tell us what you need, what's missing, what should come next. "
            "Early contributors set the direction."]]

          ;; Discord CTA ───────────────────────────────────────────────────
          [:div {:class "stack stack--xs"}
           [:span {:class "field__label"} "join the conversation"]
           [:a {:href   "https://discord.gg/ARYDmB2BZ"   ; ← replace with real invite
                :class  "btn--submit"
                :style  "display:inline-block; text-align:center;"}
            "find us on Discord →"]
           [:p {:class "field__hint"}
            "look for the #tuppu channel"]]

          ;; Back ──────────────────────────────────────────────────────────
          [:div
           [:a {:href "/" :class "back-link"} "← back to tuppu.net"]]]]]]])))


(defn serve [_]
  (-> (response (page))
      (content-type "text/html")))
