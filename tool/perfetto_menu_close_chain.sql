-- 作者 long：只沿普通performance包释放组合内的同步Binder因果边取证，不把同时间发生的系统工作猜作菜单原因。
-- 每行均为包容区间；root/forgotten/transaction/reply可互相包含，严禁相加。Running来自同线程调度交集。
WITH ui_roots AS (
  SELECT s.* FROM slice s JOIN thread_track tt ON tt.id=s.track_id
  JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE p.name='com.lonnnnnng.flclash.performance' AND t.tid=p.pid
    AND s.name GLOB 'Choreographer#doFrame [0-9]*' AND s.dur>0
), forgotten AS (
  SELECT r.id root_id, s.* FROM ui_roots r JOIN slice s ON s.track_id=r.track_id
    AND s.ts>=r.ts AND s.ts+s.dur<=r.ts+r.dur AND s.depth>r.depth
  WHERE s.name='Compose:onForgotten' AND s.dur>0 AND NOT EXISTS (
    SELECT 1 FROM slice parent WHERE parent.track_id=s.track_id AND parent.name=s.name
      AND parent.depth>r.depth AND parent.depth<s.depth AND parent.ts<=s.ts AND parent.ts+parent.dur>=s.ts+s.dur
  )
), links AS (
  SELECT f.root_id, f.id forgotten_id, b.id transaction_id, reply.id reply_id
  FROM forgotten f JOIN slice b ON b.track_id=f.track_id AND b.ts>=f.ts AND b.ts+b.dur<=f.ts+f.dur
  JOIN flow edge ON edge.slice_out=b.id JOIN slice reply ON reply.id=edge.slice_in
  JOIN thread_track rt ON rt.id=reply.track_id JOIN thread t ON t.utid=rt.utid JOIN process p USING(upid)
  WHERE b.name='binder transaction' AND reply.name='binder reply' AND p.name='system_server' AND b.dur>0 AND reply.dur>0
), stages AS (
  SELECT root_id, 'ui-root' stage, root_id id FROM links
  UNION SELECT root_id, 'onForgotten', forgotten_id FROM links
  UNION SELECT root_id, 'app-binder', transaction_id FROM links
  UNION SELECT root_id, 'system-reply', reply_id FROM links
  UNION SELECT l.root_id, 'system-work', child.id FROM links l JOIN slice reply ON reply.id=l.reply_id
    JOIN slice child ON child.track_id=reply.track_id AND child.ts>=reply.ts AND child.ts+child.dur<=reply.ts+reply.dur
    WHERE child.name IN ('removeInputChannel','performSurfacePlacement','wmUpdateFocus') AND child.dur>=0
  UNION SELECT l.root_id, 'nested-binder-reply', target.id FROM links l JOIN slice reply ON reply.id=l.reply_id
    JOIN slice child ON child.track_id=reply.track_id AND child.ts>=reply.ts AND child.ts+child.dur<=reply.ts+reply.dur
    JOIN flow edge ON edge.slice_out=child.id JOIN slice target ON target.id=edge.slice_in
    WHERE child.name='binder transaction' AND target.name='binder reply' AND target.dur>=0
), intervals AS (
  SELECT stages.*,s.name,s.ts,s.dur,t.utid,t.tid,t.name thread_name,p.name process_name
  FROM stages JOIN slice s USING(id) JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid) JOIN process p USING(upid)
)
SELECT root_id,stage,id,process_name,thread_name,tid,name,ts,round(dur/1e6,3) wall_ms,
 round((SELECT sum(max(0,min(st.ts+st.dur,i.ts+i.dur)-max(st.ts,i.ts))) FROM thread_state st
    WHERE st.utid=i.utid AND st.state='Running' AND st.ts<i.ts+i.dur AND st.ts+st.dur>i.ts)/1e6,3) running_ms,
 round((SELECT sum(max(0,min(st.ts+st.dur,i.ts+i.dur)-max(st.ts,i.ts))) FROM thread_state st
    WHERE st.utid=i.utid AND st.state IN ('R','R+') AND st.ts<i.ts+i.dur AND st.ts+st.dur>i.ts)/1e6,3) runnable_ms,
 round((SELECT sum(max(0,min(st.ts+st.dur,i.ts+i.dur)-max(st.ts,i.ts))) FROM thread_state st
    WHERE st.utid=i.utid AND st.state IN ('S','I','D') AND st.ts<i.ts+i.dur AND st.ts+st.dur>i.ts)/1e6,3) waiting_ms
FROM intervals i ORDER BY root_id,ts,dur DESC;
