(ns repetition.hunter.tool
  (:require [repetition.hunter.core :as core]))

(def valid-sorts #{:complexity :repetition})

(defn- validate-sort [sort]
  (when (and sort (not (valid-sorts sort)))
    (throw (ex-info
             (str "Invalid :sort " sort ". Expected one of: :complexity, :repetition")
             {:sort sort})))
  sort)

(defn- normalize-paths [{:keys [path paths]}]
  (cond
    (and path paths)
    (throw (ex-info "Provide exactly one of :path or :paths" {:path path :paths paths}))

    (and (nil? path) (nil? paths))
    (throw (ex-info "Provide exactly one of :path or :paths" {:path path :paths paths}))

    (nil? path)
    (if (and (vector? paths) (seq paths) (every? string? paths))
      paths
      (throw (ex-info ":paths must be a non-empty vector of strings" {:paths paths})))

    (string? path)
    [path]

    :else
    (throw (ex-info ":path must be a string" {:path path}))))

(defn hunt
  "Run repetition analysis on source files. Accepts keyword arguments:
   :path    — single file path (string)
   :paths   — multiple file paths (vector of strings)
   :platform — :clj or :cljs (required for .cljc files)
   :sort    — :complexity or :repetition
   :filter  — map with :min-repetition, :min-complexity, :remove-flat"
  [& {:keys [path paths platform sort filter]}]
  (let [normalized-paths (normalize-paths {:path path :paths paths})
        validated-sort (validate-sort sort)
        results (core/results-from-paths normalized-paths
                                         {:platform platform
                                          :sort validated-sort
                                          :filter filter})]
    (core/print-results results)))

(defn hunt-tool
  "Tools-mode entrypoint for clj -T:rh. Takes a single map of options."
  [{:keys [path paths platform sort filter]}]
  (hunt :path path :paths paths :platform platform :sort sort :filter filter))