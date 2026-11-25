-- 作者 long：RenderThread 根切片以slice.id为身份，token只负责关联，不用token折叠独立绘制工作。
-- 作者 long：FrameTimeline按frame id保留；同token可能关联主窗与多个Popup，或多个根任务，统计帧数必须对frame_id去重。
WITH roots AS (
  SELECT s.id AS root_id, s.ts AS root_ts, s.dur AS root_dur,
         s.track_id, s.depth, tt.utid,
         CAST(substr(s.name, instr(s.name, ' ') + 1) AS INT) AS token
  FROM slice s
  JOIN thread_track tt ON tt.id = s.track_id
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
    AND t.name = 'RenderThread'
    AND s.name GLOB 'DrawFrames [0-9]*'
    AND s.dur > 0
), waits AS (
  SELECT r.root_id, s.id AS wait_id, s.dur
  FROM roots r
  JOIN slice s ON s.track_id = r.track_id
    AND s.ts >= r.root_ts AND s.ts + s.dur <= r.root_ts + r.root_dur
    AND s.depth > r.depth AND s.name = 'waitForBufferRelease' AND s.dur > 0
  WHERE NOT EXISTS (
    SELECT 1 FROM slice parent
    WHERE parent.track_id = s.track_id AND parent.name = s.name
      AND parent.depth > r.depth AND parent.depth < s.depth
      AND parent.ts <= s.ts AND parent.ts + parent.dur >= s.ts + s.dur
  )
), waits_by_root AS (
  SELECT root_id, COUNT(DISTINCT wait_id) AS buffer_wait_count,
         SUM(dur) AS buffer_wait_wall_ns, MAX(dur) AS max_buffer_wait_ns
  FROM waits
  GROUP BY root_id
), frames AS (
  SELECT f.id AS frame_id, f.ts AS frame_ts, f.dur AS frame_dur,
         f.surface_frame_token AS token, f.layer_name, f.jank_type,
         f.present_type, f.gpu_composition, f.latched_fence_state,
         f.latched_unsignaled_count
  FROM actual_frame_timeline_slice f
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
)
SELECT r.root_id, r.token, r.root_ts, r.root_dur,
       ROUND(r.root_dur / 1e6, 3) AS root_wall_ms,
       ROUND(COALESCE((
         SELECT SUM(MAX(0, MIN(st.ts + st.dur, r.root_ts + r.root_dur) - MAX(st.ts, r.root_ts)))
         FROM thread_state st WHERE st.utid = r.utid AND st.state = 'Running'
           AND st.ts < r.root_ts + r.root_dur AND st.ts + st.dur > r.root_ts
       ), 0) / 1e6, 3) AS root_running_ms,
       ROUND(COALESCE((
         SELECT SUM(MAX(0, MIN(st.ts + st.dur, r.root_ts + r.root_dur) - MAX(st.ts, r.root_ts)))
         FROM thread_state st WHERE st.utid = r.utid AND st.state IN ('R', 'R+')
           AND st.ts < r.root_ts + r.root_dur AND st.ts + st.dur > r.root_ts
       ), 0) / 1e6, 3) AS root_runnable_ms,
       ROUND(COALESCE((
         SELECT SUM(MAX(0, MIN(st.ts + st.dur, r.root_ts + r.root_dur) - MAX(st.ts, r.root_ts)))
         FROM thread_state st WHERE st.utid = r.utid AND st.state IN ('S', 'I', 'D')
           AND st.ts < r.root_ts + r.root_dur AND st.ts + st.dur > r.root_ts
       ), 0) / 1e6, 3) AS root_sleeping_or_io_ms,
       COALESCE(w.buffer_wait_count, 0) AS buffer_wait_count,
       ROUND(COALESCE(w.buffer_wait_wall_ns, 0) / 1e6, 3) AS buffer_wait_wall_ms,
       ROUND(COALESCE(w.max_buffer_wait_ns, 0) / 1e6, 3) AS max_buffer_wait_ms,
       f.frame_id, f.frame_ts, f.frame_dur, f.layer_name, f.jank_type,
       f.present_type, f.gpu_composition, f.latched_fence_state,
       f.latched_unsignaled_count,
       ROUND(MAX(0, MIN(r.root_ts + r.root_dur, f.frame_ts + f.frame_dur) -
                     MAX(r.root_ts, f.frame_ts)) / 1e6, 3) AS root_frame_overlap_ms
FROM roots r
LEFT JOIN waits_by_root w ON w.root_id = r.root_id
LEFT JOIN frames f ON f.token = r.token
  AND r.root_ts < f.frame_ts + f.frame_dur
  AND r.root_ts + r.root_dur > f.frame_ts
ORDER BY r.root_ts, r.root_id, f.frame_ts, f.frame_id;
