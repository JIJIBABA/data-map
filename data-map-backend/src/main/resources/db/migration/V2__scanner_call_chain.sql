ALTER TABLE field_usage_scenario
  ADD COLUMN call_chain TEXT NULL COMMENT '完整调用链 JSON 数组' AFTER source_api_name,
  ADD COLUMN entry_info TEXT NULL COMMENT '入口信息 JSON 对象' AFTER call_chain,
  ADD COLUMN method_description VARCHAR(512) NULL COMMENT '方法含义' AFTER entry_info,
  ADD COLUMN description_source VARCHAR(16) NULL COMMENT 'COMMENT/AI/NONE' AFTER method_description;
