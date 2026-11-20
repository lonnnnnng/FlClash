-- 作者 long：同名MainActivity的新layer才是Dialog；点击基点用其之前的输入分发结束，限定首次Popup避免返回重建串窗。
WITH layers AS (
  SELECT a.layer_name, min(a.ts) AS start, max(a.ts + a.dur) AS finish
  FROM actual_frame_timeline_slice a JOIN process p USING (upid)
  WHERE p.name = 'com.lonnnnnng.flclash.performance' GROUP BY a.layer_name
), dialog AS (
  SELECT * FROM layers WHERE layer_name LIKE '%MainActivity%' ORDER BY start DESC LIMIT 1
), dispatch AS (
  SELECT max(s.ts + s.dur) AS finish FROM slice s
  JOIN thread_track tt ON tt.id = s.track_id JOIN thread t USING (utid) JOIN process p USING (upid), dialog d
  WHERE p.name = 'com.lonnnnnng.flclash.performance' AND s.name GLOB 'ViewPostImeInputStage*' AND s.ts < d.start
), first_frame AS (
  SELECT a.ts, a.dur FROM actual_frame_timeline_slice a, dialog d
  WHERE a.layer_name = d.layer_name ORDER BY a.ts LIMIT 1
), ime AS (
  SELECT s.name, s.ts, s.dur FROM slice s, dialog d
  WHERE s.ts >= d.start AND s.ts < d.start + 1500000000
    AND s.name IN ('IC.showRequestFromApiToImeReady', 'IMS.showSoftInput', 'InsetsAnimation: ime')
), events AS (
  SELECT 'dialog_first_frame_start' AS event, ts FROM first_frame
  UNION ALL SELECT 'dialog_first_frame_finish', ts + dur FROM first_frame
  UNION ALL SELECT 'popup_last_frame_finish', l.finish FROM layers l, dialog d
    WHERE l.layer_name LIKE '%弹出式窗口%' AND l.start < d.start
  UNION ALL SELECT name || '_start', ts FROM ime
  UNION ALL SELECT name || '_end', ts + dur FROM ime WHERE dur >= 0
)
SELECT event, ts, round((ts - dispatch.finish) / 1e6, 3) AS after_click_dispatch_ms
FROM events, dispatch ORDER BY ts;
-- IME ready/动画结束是系统观测点，不等于已注入字符成功；用真实输入回归补足功能证据。
