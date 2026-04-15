(ns url-shortener.admin.v1.link-detail
  (:require   
   [taoensso.carmine :as redis]
   [url-shortener.shared.utils :refer [find-or-create-report!]]
   [url-shortener.schema :refer [reports-key]]
   [ring.util.response :refer [redirect not-found]]))

(defn handle-link-detail [{{path :path} :path-params}]
  (if (redis/wcar nil (redis/hget path "owner-id"))
    (redirect (str "/report/" (find-or-create-report! path reports-key)))
    (not-found "Link not found")))
