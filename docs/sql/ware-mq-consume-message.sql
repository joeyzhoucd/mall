CREATE TABLE IF NOT EXISTS wms_mq_consume_message (
  id bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  consumer_group varchar(64) NOT NULL COMMENT 'consumer group or listener name',
  message_key varchar(160) NOT NULL COMMENT 'business idempotent message key',
  business_type varchar(64) NOT NULL COMMENT 'business type',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0 processing, 1 success, 2 failed',
  consume_count int NOT NULL DEFAULT 0 COMMENT 'consume attempts',
  last_error varchar(500) DEFAULT NULL COMMENT 'last consume error',
  success_time datetime DEFAULT NULL COMMENT 'success time',
  create_time datetime NOT NULL COMMENT 'create time',
  update_time datetime NOT NULL COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ware_mq_consume_key (consumer_group, message_key),
  KEY idx_ware_mq_consume_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ware mq consume idempotent record';
