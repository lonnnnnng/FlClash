-- 作者 long：从线程帧而非FrameTimeline起表，保留仅拆窗、不再提交图像的关闭工作；同token多窗口不重复计线程耗时。
-- 目标为独立完整R8包。通过trace_processor -q本文件查询，UI/RT墙钟与实际Running分开，父子切片不得相加。
WITH roots AS (
  SELECT s.*, t.utid,
         CASE WHEN t.tid = p.pid THEN 'UI' ELSE 'RenderThread' END AS role,
         CAST(substr(s.name, instr(s.name, ' ') + 1) AS INT) AS token
  FROM slice s
  JOIN thread_track tt ON tt.id = s.track_id
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance' AND s.dur > 0
    AND ((t.tid = p.pid AND s.name GLOB 'Choreographer#doFrame [0-9]*')
      OR (t.name = 'RenderThread' AND s.name GLOB 'DrawFrames [0-9]*'))
), categories AS (
  SELECT s.*, CASE
    WHEN name = 'Compose:recompose' THEN 'recompose'
    WHEN name = 'Compose:onRemembered' THEN 'remembered'
    WHEN name = 'Compose:onForgotten' THEN 'forgotten'
    WHEN name = 'measure' THEN 'measure'
    WHEN name GLOB '*relayoutWindow#*' THEN 'relayout'
    WHEN name = 'layout' THEN 'layout'
    WHEN name GLOB 'draw-VRI*' THEN 'draw'
    WHEN name = 'waitForBufferRelease' THEN 'buffer'
    WHEN name = 'binder transaction' THEN 'binder'
  END AS work_category FROM slice s WHERE dur >= 0
), measured AS (
  SELECT r.*,
         (SELECT sum(max(0, min(st.ts + st.dur, r.ts + r.dur) - max(st.ts, r.ts)))
          FROM thread_state st WHERE st.utid = r.utid AND st.state = 'Running'
            AND st.ts < r.ts + r.dur AND st.ts + st.dur > r.ts) AS running,
         (SELECT sum(max(0, min(st.ts + st.dur, r.ts + r.dur) - max(st.ts, r.ts)))
          FROM thread_state st WHERE st.utid = r.utid AND st.state IN ('R', 'R+')
            AND st.ts < r.ts + r.dur AND st.ts + st.dur > r.ts) AS runnable,
         (SELECT sum(max(0, min(st.ts + st.dur, r.ts + r.dur) - max(st.ts, r.ts)))
          FROM thread_state st WHERE st.utid = r.utid AND st.state IN ('S', 'I', 'D')
            AND st.ts < r.ts + r.dur AND st.ts + st.dur > r.ts) AS waiting
  FROM roots r
)
SELECT r.role, r.id, r.token, r.ts, r.dur,
       round(r.dur / 1e6, 3) AS wall_ms,
       round(r.running / 1e6, 3) AS running_ms,
       round(r.runnable / 1e6, 3) AS runnable_ms,
       round(r.waiting / 1e6, 3) AS waiting_ms,
       (SELECT count(*) FROM actual_frame_timeline_slice f JOIN process p USING (upid)
        WHERE p.name = 'com.lonnnnnng.flclash.performance' AND f.surface_frame_token = r.token) AS timeline_rows,
       round(sum(CASE WHEN s.name = 'Compose:recompose' THEN s.dur END) / 1e6, 3) AS recompose_ms,
       round(sum(CASE WHEN s.name = 'Compose:onRemembered' THEN s.dur END) / 1e6, 3) AS remembered_ms,
       round(sum(CASE WHEN s.name = 'Compose:onForgotten' THEN s.dur END) / 1e6, 3) AS forgotten_ms,
       round(sum(CASE WHEN s.name = 'measure' THEN s.dur END) / 1e6, 3) AS measure_ms,
       round(sum(CASE WHEN s.name GLOB '*relayoutWindow#*' THEN s.dur END) / 1e6, 3) AS relayout_ms,
       round(sum(CASE WHEN s.name = 'layout' THEN s.dur END) / 1e6, 3) AS layout_ms,
       round(sum(CASE WHEN s.name GLOB 'draw-VRI*' THEN s.dur END) / 1e6, 3) AS draw_ms,
       round(sum(CASE WHEN s.name = 'waitForBufferRelease' THEN s.dur END) / 1e6, 3) AS buffer_release_wait_ms,
       round(sum(CASE WHEN s.name = 'binder transaction' THEN s.dur END) / 1e6, 3) AS binder_ms
FROM measured r
LEFT JOIN categories s ON s.track_id = r.track_id AND s.depth > r.depth
  AND s.ts >= r.ts AND s.ts + s.dur <= r.ts + r.dur AND s.dur >= 0
  -- 作者 long：窗口释放会嵌套同名onForgotten；只取根内最外层同类区间，不能把包含的子段重复加到父耗时上。
  AND s.work_category IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM categories parent WHERE parent.track_id = s.track_id
      AND parent.work_category = s.work_category AND parent.depth > r.depth AND parent.depth < s.depth
      AND parent.ts >= r.ts AND parent.ts + parent.dur <= r.ts + r.dur
      AND parent.ts <= s.ts AND parent.ts + parent.dur >= s.ts + s.dur
  )
GROUP BY r.id
ORDER BY r.ts, r.role;
