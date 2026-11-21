-- 作者 long：图形管线首次编译与缓冲等待分别归因到同一个真实RenderThread根；诊断包数据不进入普通R8性能分母。
-- 使用 trace_processor query --format csv -f 本文件 TRACE。子段包含在根墙钟内，不能再加到根耗时上。
WITH roots AS (
  SELECT s.*, t.utid
  FROM slice s JOIN thread_track tt ON tt.id = s.track_id
  JOIN thread t USING (utid) JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance' AND t.name = 'RenderThread'
    AND s.name GLOB 'DrawFrames [0-9]*' AND s.dur > 0
), work AS (
  SELECT r.id root_id, s.* FROM roots r JOIN slice s
    ON s.track_id = r.track_id AND s.ts >= r.ts AND s.ts + s.dur <= r.ts + r.dur
    AND s.depth > r.depth AND s.dur >= 0
  WHERE s.name IN ('shader_compile', 'cache_miss', 'driver_compile_shader',
    'driver_link_program', 'waitForBufferRelease')
    AND NOT EXISTS (
      SELECT 1 FROM slice parent WHERE parent.track_id = s.track_id AND parent.name = s.name
        AND parent.depth > r.depth AND parent.depth < s.depth
        AND parent.ts <= s.ts AND parent.ts + parent.dur >= s.ts + s.dur
    )
)
SELECT r.id, r.name, r.ts, round(r.dur / 1e6, 3) wall_ms,
  round((SELECT sum(max(0, min(st.ts + st.dur, r.ts + r.dur) - max(st.ts, r.ts)))
    FROM thread_state st WHERE st.utid = r.utid AND st.state = 'Running'
      AND st.ts < r.ts + r.dur AND st.ts + st.dur > r.ts) / 1e6, 3) running_ms,
  sum(CASE WHEN w.name = 'shader_compile' THEN 1 ELSE 0 END) shader_count,
  round(sum(CASE WHEN w.name = 'shader_compile' THEN w.dur ELSE 0 END) / 1e6, 3) shader_wall_ms,
  round(sum(CASE WHEN w.name = 'cache_miss' THEN w.dur ELSE 0 END) / 1e6, 3) cache_miss_wall_ms,
  round(sum(CASE WHEN w.name = 'driver_compile_shader' THEN w.dur ELSE 0 END) / 1e6, 3) driver_compile_wall_ms,
  round(sum(CASE WHEN w.name = 'driver_link_program' THEN w.dur ELSE 0 END) / 1e6, 3) driver_link_wall_ms,
  round(sum(CASE WHEN w.name = 'waitForBufferRelease' THEN w.dur ELSE 0 END) / 1e6, 3) buffer_wait_wall_ms
FROM roots r LEFT JOIN work w ON w.root_id = r.id
GROUP BY r.id ORDER BY r.ts;
