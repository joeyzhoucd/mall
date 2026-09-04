ALTER TABLE oms_payment_info
  ADD COLUMN payment_channel VARCHAR(32) DEFAULT NULL COMMENT 'payment channel: alipay/wechat/credit_card' AFTER alipay_trade_no,
  ADD COLUMN payment_currency VARCHAR(16) DEFAULT NULL COMMENT 'payment currency' AFTER total_amount,
  ADD KEY idx_payment_info_reconcile (payment_status, payment_channel, create_time);
