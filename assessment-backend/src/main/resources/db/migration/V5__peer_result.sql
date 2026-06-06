-- V5: 他评表增加 peer_result 字段，使用通用得分计算规则
-- 与自评 self_result 对齐，用于存储按指标类型计算后的结果

ALTER TABLE biz_peer_evaluation
    ADD COLUMN peer_result DECIMAL(10,2) DEFAULT NULL COMMENT '他评结果(按通用得分规则计算)' AFTER peer_score;
