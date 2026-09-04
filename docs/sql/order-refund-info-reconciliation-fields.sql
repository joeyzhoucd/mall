ALTER TABLE oms_refund_info
  ADD COLUMN order_sn VARCHAR(64) DEFAULT NULL COMMENT 'order number' AFTER order_return_id,
  ADD COLUMN payment_channel VARCHAR(32) DEFAULT NULL COMMENT 'payment channel: alipay/wechat/credit_card' AFTER refund_sn,
  ADD COLUMN trade_no VARCHAR(96) DEFAULT NULL COMMENT 'gateway trade number' AFTER payment_channel,
  ADD COLUMN refund_trade_no VARCHAR(96) DEFAULT NULL COMMENT 'gateway refund trade number' AFTER trade_no,
  ADD COLUMN currency VARCHAR(16) DEFAULT NULL COMMENT 'refund currency' AFTER refund_trade_no,
  ADD KEY idx_refund_info_refund_sn (refund_sn),
  ADD KEY idx_refund_info_order_sn (order_sn);
