(ns pc.models.issue
  (:require [clojure.set :as set]
            [pc.datomic :as pcd]
            [pc.utils :as utils]
            [datomic.api :refer [db q] :as d]))

(defn all-issues [db]
  (map #(d/entity db (:e %)) (d/datoms db :aevt :issue/title)))

(defn uncompleted-issues [db]
  (map #(d/entity db %)
       (d/q '{:find [[?t ...]]
              :where [[?t :issue/title]
                      (not [?t :issue/status :issue.status/completed])]}
            db)))

(defn completed-issues [db]
  (map #(d/entity db %)
       (d/q '{:find [[?t ...]]
              :where [[?t :issue/title]
                      [?t :issue/status :issue.status/completed]]}
            db)))

(defn all-votes [db]
  (map #(d/entity db (:e %)) (d/datoms db :aevt :vote/cust)))

(defn find-by-frontend-id [db frontend-id]
  (let [candidate (d/entity db (:e (first (d/datoms db :avet :frontend/issue-id frontend-id))))]
    (when (:issue/title candidate)
      candidate)))

(defn find-by-doc [db doc]
  (d/entity db (:e (first (d/datoms db :vaet (:db/id doc) :issue/document)))))

(defn vote-read-api [vote]
  (-> vote
    (select-keys [:vote/cust
                  :frontend/issue-id])
    (update-in [:vote/cust] :cust/uuid)))

(defn comment-read-api [comment]
  (-> comment
    (select-keys [:comment/body
                  :comment/author
                  :comment/created-at
                  :comment/parent
                  :frontend/issue-id])
    (utils/update-when-in [:comment/parent] (fn [p] {:frontend/issue-id (:frontend/issue-id p)}))
    (utils/update-when-in [:comment/author] :cust/uuid)))

(defn read-api [issue]
  (-> issue
    (select-keys [:issue/title
                  :issue/description
                  :issue/author
                  :issue/document
                  :issue/created-at
                  :issue/votes
                  :issue/comments
                  :issue/status
                  :frontend/issue-id])
    (utils/update-when-in [:issue/document] :db/id)
    (utils/update-when-in [:issue/author] :cust/uuid)
    (utils/update-when-in [:issue/votes] #(set (map vote-read-api %)))
    (utils/update-when-in [:issue/comments] #(set (map comment-read-api %)))))

(defn summary-read-api [issue]
  (-> issue
    (select-keys [:issue/title
                  :issue/description
                  :issue/author
                  :issue/document
                  :issue/created-at
                  :issue/status
                  :frontend/issue-id])
    (utils/update-when-in [:issue/document] :db/id)
    (utils/update-when-in [:issue/author] :cust/uuid)))

(defn search-issues [db q]
  (d/q '[:find ?e ?score
         :in $ ?search
         :where (or [(fulltext $ :issue/description ?search) [[?e _ _ ?score]]]
                    [(fulltext $ :issue/title ?search) [[?e _ _ ?score]]])]
       db q))

(defn search-comments [db q]
  (d/q '[:find ?e ?score
         :in $ ?search
         :where [(fulltext $ :comment/body ?search) [[?e _ _ ?score]]]]
       db q))

(defn find-issue-by-comment [db comment]
  (d/entity db (:e (first (d/datoms db :vaet :issue/comments (:db/id comment) :issue/comments)))))

(defn issue-search-read-api [db [e score]]
  [(:frontend/issue-id (d/entity db e)) score])

(defn comment-search-read-api [db [e score]]
  [(:frontend/issue-id (find-issue-by-comment db {:db/id e})) score])

(defn valid-issue? [?issue]
  (let [actual-keys (set (keys (d/touch ?issue)))
        optional-keys #{:issue/comments :issue/votes :issue/document :issue/description :db/id :issue/status}
        required-keys #{:issue/title :issue/author :issue/created-at :frontend/issue-id}]
    (and (set/subset? required-keys actual-keys)
         (set/subset? actual-keys (set/union optional-keys required-keys)))))

(defn valid-comment? [?comment]
  (let [actual-keys (set (keys (d/touch ?comment)))
        optional-keys #{:comment/parent :db/id}
        required-keys #{:comment/body :comment/author :comment/created-at :frontend/issue-id}]
    (and (set/subset? required-keys actual-keys)
         (set/subset? actual-keys (set/union optional-keys required-keys)))))

(defn valid-vote? [?vote]
  (let [actual-keys (set (keys (d/touch ?vote)))
        optional-keys #{:db/id}
        required-keys #{:vote/cust :vote/cust-issue :frontend/issue-id}]
    (and (set/subset? required-keys actual-keys)
         (set/subset? actual-keys (set/union optional-keys required-keys)))))

(defn valid-document? [?doc]
  (let [actual-keys (set (keys (d/touch ?doc)))
        optional-keys #{:db/id}
        required-keys #{:document/name :document/creator :document/privacy}]
    (and (set/subset? required-keys actual-keys)
         (set/subset? actual-keys (set/union optional-keys required-keys)))))
