SET @schema_name = DATABASE();

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wms_ware_order_task_detail ADD COLUMN retry_count int NOT NULL DEFAULT 0 COMMENT ''stock release/deduct retry count'' AFTER lock_status',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'wms_ware_order_task_detail'
    AND column_name = 'retry_count'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wms_ware_order_task_detail ADD COLUMN ware_id bigint NULL COMMENT ''warehouse that locked this stock row'' AFTER retry_count',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'wms_ware_order_task_detail'
    AND column_name = 'ware_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wms_ware_order_task_detail ADD KEY idx_task_sku_status (task_id, sku_id, lock_status)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'wms_ware_order_task_detail'
    AND index_name = 'idx_task_sku_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE wms_ware_order_task_detail ADD KEY idx_lock_status_retry (lock_status, retry_count)',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'wms_ware_order_task_detail'
    AND index_name = 'idx_lock_status_retry'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
