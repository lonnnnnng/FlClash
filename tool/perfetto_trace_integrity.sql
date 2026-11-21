-- 作者 long：全局提交前丢弃与环形缓冲覆盖是不同指标；保留零值和buffer索引，不能仅凭discarded非零判断缓冲写满。
-- 即使buffer无丢失，全局discarded来源未确定时仍不宣称trace完整，也不把chunk计数当作掉帧数。
SELECT name, idx, severity, source, value
FROM stats
WHERE name GLOB 'traced*'
   OR (value != 0 AND (severity IN ('error', 'data_loss') OR name GLOB '*lost*'))
ORDER BY name, idx;
