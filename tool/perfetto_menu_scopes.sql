-- 作者 long：仅分析显式 kclashMenuTrace=true 的 performanceBenchmark 诊断包。
-- 用 trace_processor query --format csv -f 本文件 TRACE 导出；每行含子区间，嵌套行不可相加。
WITH scopes AS (
  SELECT s.*, t.utid
  FROM slice s
  JOIN thread_track tt ON tt.id = s.track_id
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance'
    AND s.name GLOB 'kclash/*' AND s.dur >= 0
)
SELECT s.id, s.name, s.ts, round(s.dur / 1e6, 3) AS wall_ms,
  round((SELECT sum(max(0, min(st.ts + st.dur, s.ts + s.dur) - max(st.ts, s.ts)))
    FROM thread_state st
    WHERE st.utid = s.utid AND st.state = 'Running'
      AND st.ts < s.ts + s.dur AND st.ts + st.dur > s.ts) / 1e6, 3) AS running_ms,
  (SELECT root.name FROM slice root
    WHERE root.track_id = s.track_id AND root.name GLOB 'Choreographer#doFrame *'
      AND root.ts <= s.ts AND root.ts + root.dur >= s.ts + s.dur
    ORDER BY root.dur LIMIT 1) AS frame
FROM scopes s ORDER BY s.ts;
