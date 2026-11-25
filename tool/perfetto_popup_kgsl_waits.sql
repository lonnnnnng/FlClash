-- 作者 long：RenderThread的KGSL等待以入口/出口事件配对，判断它是否与动画、Popup帧和DrawFrames根切片重叠。
-- 作者 long：时间重叠只能证明同窗相关，不能单独证明该等待造成整帧超时。
WITH events AS (
  SELECT e.ts, e.name, e.utid,
         MAX(CASE WHEN a.flat_key = 'context_id' THEN a.int_value END) AS context_id,
         MAX(CASE WHEN a.flat_key = 'wait_ts' THEN a.int_value END) AS wait_timestamp
  FROM __intrinsic_ftrace_event e
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  LEFT JOIN args a USING (arg_set_id)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
    AND t.name = 'RenderThread'
    AND e.name IN ('kgsl_waittimestamp_entry', 'kgsl_waittimestamp_exit')
  GROUP BY e.id, e.ts, e.name, e.utid
), paired AS (
  SELECT ts AS wait_start, utid, name, context_id, wait_timestamp,
         LEAD(ts) OVER (PARTITION BY utid ORDER BY ts) AS wait_end,
         LEAD(name) OVER (PARTITION BY utid ORDER BY ts) AS next_name
  FROM events
), waits AS (
  SELECT wait_start, wait_end, utid, context_id, wait_timestamp
  FROM paired
  WHERE name = 'kgsl_waittimestamp_entry'
    AND next_name = 'kgsl_waittimestamp_exit'
    AND wait_end > wait_start
), phases AS (
  SELECT ts, dur, name
  FROM slice
  WHERE name IN ('kclash/menu/open-window', 'kclash/menu/close-window')
), roots AS (
  SELECT s.id, s.ts, s.dur, tt.utid,
         CAST(substr(s.name, instr(s.name, ' ') + 1) AS INT) AS token
  FROM slice s
  JOIN thread_track tt ON tt.id = s.track_id
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
    AND t.name = 'RenderThread'
    AND s.name GLOB 'DrawFrames [0-9]*'
    AND s.dur > 0
), frames AS (
  SELECT DISTINCT f.id, f.ts, f.dur, f.surface_frame_token AS token,
         f.layer_name, f.jank_type, f.present_type
  FROM actual_frame_timeline_slice f
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
    AND f.layer_name GLOB '*弹出式窗口*'
)
SELECT w.wait_start, w.wait_end,
       ROUND((w.wait_end - w.wait_start) / 1e6, 3) AS wait_ms,
       w.context_id, w.wait_timestamp,
       phase.name AS animation_phase,
       COUNT(DISTINCT r.id) AS overlapped_render_roots,
       GROUP_CONCAT(DISTINCT r.id) AS render_root_ids,
       GROUP_CONCAT(DISTINCT r.token) AS render_tokens,
       COUNT(DISTINCT f.id) AS overlapped_popup_frame_rows,
       GROUP_CONCAT(DISTINCT f.id) AS popup_frame_ids,
       GROUP_CONCAT(DISTINCT f.token) AS popup_frame_tokens,
       GROUP_CONCAT(DISTINCT f.jank_type) AS overlapped_popup_jank_types,
       GROUP_CONCAT(DISTINCT f.present_type) AS overlapped_present_types
FROM waits w
LEFT JOIN phases phase ON w.wait_start >= phase.ts AND w.wait_start < phase.ts + phase.dur
LEFT JOIN roots r ON r.utid = w.utid
  AND r.ts < w.wait_end AND r.ts + r.dur > w.wait_start
LEFT JOIN frames f ON f.ts < w.wait_end AND f.ts + f.dur > w.wait_start
GROUP BY w.wait_start, w.wait_end, w.context_id, w.wait_timestamp, phase.name
ORDER BY w.wait_start;
