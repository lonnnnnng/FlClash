-- 作者 long：FrameTimeline的每行id代表一个layer frame；按id统计，避免RenderThread重复token扩大慢帧数。
-- 作者 long：Popup窗口只按实际layer名筛选，打开/关闭区间使用标记起点落入半开窗口的frame行。
WITH frames AS (
  SELECT f.id AS frame_id, f.ts, f.dur, f.surface_frame_token AS token,
         f.layer_name, f.jank_type, f.present_type, f.gpu_composition,
         f.latched_fence_state, f.latched_unsignaled_count
  FROM actual_frame_timeline_slice f
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
    AND f.layer_name GLOB '*弹出式窗口*'
), phases AS (
  SELECT ts, dur, name
  FROM slice
  WHERE name IN ('kclash/menu/open-window', 'kclash/menu/close-window')
), summary AS (
  SELECT 'all-popup' AS scope, COUNT(DISTINCT frame_id) AS frame_rows,
         COUNT(DISTINCT token) AS tokens,
         SUM(jank_type LIKE '%App Deadline Missed%') AS app_deadline_missed,
         SUM(jank_type LIKE '%Dropped Frame%') AS dropped_frame,
         SUM(jank_type LIKE '%Buffer Stuffing%') AS buffer_stuffing,
         SUM(latched_fence_state IS NOT NULL) AS populated_fence_state,
         SUM(COALESCE(latched_unsignaled_count, 0) > 0) AS unsignaled_fence_rows,
         GROUP_CONCAT(DISTINCT gpu_composition) AS gpu_composition_values
  FROM frames
  UNION ALL
  SELECT p.name AS scope, COUNT(DISTINCT f.frame_id) AS frame_rows,
         COUNT(DISTINCT f.token) AS tokens,
         SUM(f.jank_type LIKE '%App Deadline Missed%') AS app_deadline_missed,
         SUM(f.jank_type LIKE '%Dropped Frame%') AS dropped_frame,
         SUM(f.jank_type LIKE '%Buffer Stuffing%') AS buffer_stuffing,
         SUM(f.latched_fence_state IS NOT NULL) AS populated_fence_state,
         SUM(COALESCE(f.latched_unsignaled_count, 0) > 0) AS unsignaled_fence_rows,
         GROUP_CONCAT(DISTINCT f.gpu_composition) AS gpu_composition_values
  FROM phases p
  LEFT JOIN frames f ON f.ts >= p.ts AND f.ts < p.ts + p.dur
  GROUP BY p.name
)
SELECT * FROM summary ORDER BY scope;
