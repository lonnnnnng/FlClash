-- 作者 long：仅分析显式插桩的独立R8包；同步组合销毁、输入解绑与窗口移除分别记录，不把跨帧await当本地CPU。
-- 各标记可相互包含，wall/Running不能累加；frame_id为空的detach后业务清理仍须保留。
WITH marked AS (
  SELECT s.*, t.utid, t.tid, p.pid FROM slice s
  JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE p.name='com.lonnnnnng.flclash.performance' AND s.dur>=0
    AND (s.name GLOB 'kclash/dispose/*' OR s.name GLOB 'kclash/menu/cleanup/*')
)
SELECT m.id,m.name,m.ts,m.tid,
 (SELECT r.id FROM slice r WHERE r.track_id=m.track_id
   AND r.name GLOB 'Choreographer#doFrame [0-9]*' AND r.dur>0
   AND r.ts<=m.ts AND r.ts+r.dur>=m.ts+m.dur ORDER BY r.dur LIMIT 1) frame_id,
 round(m.dur/1e6,3) wall_ms,
 round(coalesce((SELECT sum(max(0,min(st.ts+st.dur,m.ts+m.dur)-max(st.ts,m.ts))) FROM thread_state st
   WHERE st.utid=m.utid AND st.state='Running' AND st.ts<m.ts+m.dur AND st.ts+st.dur>m.ts),0)/1e6,3) running_ms,
 round(coalesce((SELECT sum(max(0,min(st.ts+st.dur,m.ts+m.dur)-max(st.ts,m.ts))) FROM thread_state st
   WHERE st.utid=m.utid AND st.state IN ('R','R+') AND st.ts<m.ts+m.dur AND st.ts+st.dur>m.ts),0)/1e6,3) runnable_ms,
 round(coalesce((SELECT sum(max(0,min(st.ts+st.dur,m.ts+m.dur)-max(st.ts,m.ts))) FROM thread_state st
   WHERE st.utid=m.utid AND st.state IN ('S','I','D') AND st.ts<m.ts+m.dur AND st.ts+st.dur>m.ts),0)/1e6,3) waiting_ms
FROM marked m ORDER BY m.ts,m.dur DESC;
