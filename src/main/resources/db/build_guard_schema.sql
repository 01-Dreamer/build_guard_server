CREATE DATABASE IF NOT EXISTS build_guard DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE build_guard;

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '管理员用户名',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
  real_name VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  email VARCHAR(128) DEFAULT NULL COMMENT '通知邮箱',
  avatar_file_id BIGINT DEFAULT NULL COMMENT '头像文件ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  last_login_at DATETIME(3) DEFAULT NULL,
  last_login_ip VARCHAR(45) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号';

CREATE TABLE IF NOT EXISTS personnel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL COMMENT '员工姓名',
  phone VARCHAR(20) DEFAULT NULL COMMENT '手机号，仅展示',
  email VARCHAR(128) DEFAULT NULL COMMENT '通知邮箱',
  avatar_file_id BIGINT DEFAULT NULL COMMENT '头像文件ID',
  job_title VARCHAR(50) DEFAULT NULL COMMENT '岗位',
  team_name VARCHAR(80) DEFAULT NULL COMMENT '班组',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工信息';

CREATE TABLE IF NOT EXISTS personnel_face (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  personnel_id BIGINT NOT NULL,
  face_ref VARCHAR(128) NOT NULL COMMENT 'AI服务中的人脸引用',
  registered_at DATETIME(3) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  KEY idx_personnel_face_personnel (personnel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工人脸注册引用';

CREATE TABLE IF NOT EXISTS violation_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  personnel_id BIGINT DEFAULT NULL,
  violation_item VARCHAR(120) NOT NULL,
  fine_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  payment_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未支付 1已支付',
  source_alarm_id BIGINT DEFAULT NULL,
  occurred_at DATETIME(3) DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_violation_personnel (personnel_id),
  KEY idx_violation_alarm (source_alarm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工违规处理';

CREATE TABLE IF NOT EXISTS email_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  subject VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件模板';

CREATE TABLE IF NOT EXISTS email_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receiver_email VARCHAR(128) NOT NULL,
  subject VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  send_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1成功 2失败',
  biz_type VARCHAR(40) DEFAULT NULL,
  biz_id BIGINT DEFAULT NULL,
  sent_at DATETIME(3) DEFAULT NULL,
  error_message VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件发送记录';

CREATE TABLE IF NOT EXISTS project_site (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(255) DEFAULT NULL,
  manager_name VARCHAR(50) DEFAULT NULL,
  manager_phone VARCHAR(20) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工地项目';

CREATE TABLE IF NOT EXISTS site_area (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  site_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  KEY idx_site_area_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工地区域';

CREATE TABLE IF NOT EXISTS device_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_device_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型';

CREATE TABLE IF NOT EXISTS device_location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  area_id BIGINT DEFAULT NULL,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(64) DEFAULT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  KEY idx_device_location_area (area_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备位置';

CREATE TABLE IF NOT EXISTS device_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(64) NOT NULL,
  type_id BIGINT NOT NULL,
  location_id BIGINT DEFAULT NULL,
  model VARCHAR(80) DEFAULT NULL,
  manufacturer VARCHAR(80) DEFAULT NULL,
  install_date DATE DEFAULT NULL,
  online_status TINYINT NOT NULL DEFAULT 0 COMMENT '0离线 1在线',
  enabled TINYINT NOT NULL DEFAULT 1,
  x DECIMAL(10,2) DEFAULT NULL,
  y DECIMAL(10,2) DEFAULT NULL,
  photo_file_id BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_device_asset_code (code),
  KEY idx_device_asset_type (type_id),
  KEY idx_device_asset_location (location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备档案';

CREATE TABLE IF NOT EXISTS device_online_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  online_status TINYINT NOT NULL COMMENT '0离线 1在线',
  reported_at DATETIME(3) NOT NULL,
  source VARCHAR(40) DEFAULT 'sim',
  KEY idx_device_online_device_time (device_id, reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备上下线记录';

CREATE TABLE IF NOT EXISTS device_command (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  command_type VARCHAR(40) NOT NULL,
  command_payload JSON DEFAULT NULL,
  send_status TINYINT NOT NULL DEFAULT 0,
  sent_at DATETIME(3) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备控制指令';

CREATE TABLE IF NOT EXISTS tower_crane_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  front_arm_length DECIMAL(10,2) DEFAULT NULL,
  back_arm_length DECIMAL(10,2) DEFAULT NULL,
  max_height DECIMAL(10,2) DEFAULT NULL,
  rated_load DECIMAL(10,2) DEFAULT NULL,
  rated_moment DECIMAL(12,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='塔吊参数';

CREATE TABLE IF NOT EXISTS elevator_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  rated_load DECIMAL(10,2) DEFAULT NULL,
  rated_speed DECIMAL(10,2) DEFAULT NULL,
  rated_people INT DEFAULT NULL,
  max_height DECIMAL(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='升降机参数';

CREATE TABLE IF NOT EXISTS formwork_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  height DECIMAL(10,2) DEFAULT NULL,
  area DECIMAL(10,2) DEFAULT NULL,
  design_load DECIMAL(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高支模参数';

CREATE TABLE IF NOT EXISTS deep_pit_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  pit_depth DECIMAL(10,2) DEFAULT NULL,
  support_type VARCHAR(80) DEFAULT NULL,
  water_level_limit DECIMAL(10,2) DEFAULT NULL,
  settlement_limit DECIMAL(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='深基坑参数';

CREATE TABLE IF NOT EXISTS environment_device_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  sensor_type VARCHAR(80) DEFAULT NULL,
  sampling_interval INT DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境设备参数';

CREATE TABLE IF NOT EXISTS camera_device_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  camera_source VARCHAR(255) DEFAULT NULL COMMENT '电脑摄像头、本地文件或流地址',
  ai_monitor_types VARCHAR(255) DEFAULT NULL,
  enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头参数';

CREATE TABLE IF NOT EXISTS spray_device_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL UNIQUE,
  area_id BIGINT DEFAULT NULL,
  control_mode VARCHAR(40) DEFAULT 'auto',
  enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喷淋设备参数';

CREATE TABLE IF NOT EXISTS monitor_point (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(64) NOT NULL,
  device_id BIGINT NOT NULL,
  metric_code VARCHAR(64) NOT NULL,
  metric_name VARCHAR(80) NOT NULL,
  unit VARCHAR(32) DEFAULT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_monitor_point_code (code),
  KEY idx_monitor_point_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监测点';

CREATE TABLE IF NOT EXISTS monitor_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  monitor_point_id BIGINT NOT NULL,
  warn_upper DECIMAL(12,2) DEFAULT NULL,
  warn_lower DECIMAL(12,2) DEFAULT NULL,
  alarm_upper DECIMAL(12,2) DEFAULT NULL,
  alarm_lower DECIMAL(12,2) DEFAULT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  KEY idx_monitor_rule_point (monitor_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监测报警规则';

CREATE TABLE IF NOT EXISTS spray_link_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  monitor_point_id BIGINT NOT NULL,
  spray_device_id BIGINT NOT NULL,
  start_value DECIMAL(12,2) NOT NULL,
  close_value DECIMAL(12,2) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喷淋联动规则';

CREATE TABLE IF NOT EXISTS ai_monitor_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  camera_device_id BIGINT NOT NULL,
  detect_type VARCHAR(40) NOT NULL,
  confidence_threshold DECIMAL(5,4) NOT NULL DEFAULT 0.3500,
  enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI识别规则';

CREATE TABLE IF NOT EXISTS tower_prediction_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  window_size INT NOT NULL DEFAULT 20,
  risk_threshold DECIMAL(5,4) NOT NULL DEFAULT 0.7000,
  enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='塔吊预测规则';

CREATE TABLE IF NOT EXISTS alarm_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alarm_type VARCHAR(40) NOT NULL,
  alarm_level VARCHAR(20) NOT NULL,
  device_id BIGINT DEFAULT NULL,
  monitor_point_id BIGINT DEFAULT NULL,
  personnel_id BIGINT DEFAULT NULL,
  content VARCHAR(500) NOT NULL,
  alarm_value DECIMAL(12,2) DEFAULT NULL,
  unit VARCHAR(32) DEFAULT NULL,
  occurred_at DATETIME(3) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0未处理 1处理中 2已处理',
  snapshot_file_id BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_alarm_time (occurred_at),
  KEY idx_alarm_device (device_id),
  KEY idx_alarm_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一报警记录';

CREATE TABLE IF NOT EXISTS alarm_handle_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alarm_id BIGINT NOT NULL,
  handle_by VARCHAR(50) NOT NULL,
  handle_content VARCHAR(500) NOT NULL,
  handled_at DATETIME(3) NOT NULL,
  KEY idx_alarm_handle_alarm (alarm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警处理记录';

CREATE TABLE IF NOT EXISTS tower_work_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  start_time DATETIME(3) NOT NULL,
  end_time DATETIME(3) DEFAULT NULL,
  max_weight DECIMAL(10,2) DEFAULT NULL,
  max_height DECIMAL(10,2) DEFAULT NULL,
  max_moment DECIMAL(12,2) DEFAULT NULL,
  max_wind_speed DECIMAL(10,2) DEFAULT NULL,
  KEY idx_tower_work_device_time (device_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='塔吊作业统计';

CREATE TABLE IF NOT EXISTS elevator_work_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  start_time DATETIME(3) NOT NULL,
  end_time DATETIME(3) DEFAULT NULL,
  direction VARCHAR(20) DEFAULT NULL,
  load_weight DECIMAL(10,2) DEFAULT NULL,
  people_count INT DEFAULT NULL,
  max_height DECIMAL(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='升降机作业统计';

CREATE TABLE IF NOT EXISTS spray_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  spray_device_id BIGINT NOT NULL,
  start_time DATETIME(3) NOT NULL,
  duration_minutes INT NOT NULL,
  cycle_value INT DEFAULT NULL,
  cycle_unit VARCHAR(20) DEFAULT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  next_run_at DATETIME(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喷淋任务';

CREATE TABLE IF NOT EXISTS spray_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  spray_device_id BIGINT NOT NULL,
  task_id BIGINT DEFAULT NULL,
  operation_type VARCHAR(20) NOT NULL,
  trigger_type VARCHAR(40) DEFAULT NULL,
  started_at DATETIME(3) DEFAULT NULL,
  ended_at DATETIME(3) DEFAULT NULL,
  execute_status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='喷淋记录';

CREATE TABLE IF NOT EXISTS ai_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_type VARCHAR(40) NOT NULL COMMENT 'yolo_detection,tower_prediction,face_recognition',
  device_id BIGINT DEFAULT NULL,
  source_file_id BIGINT DEFAULT NULL,
  request_message_id VARCHAR(80) NOT NULL,
  result_message_id VARCHAR(80) DEFAULT NULL,
  task_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已发送 2成功 3失败',
  payload_json JSON DEFAULT NULL,
  result_json JSON DEFAULT NULL,
  error_message VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at DATETIME(3) DEFAULT NULL,
  UNIQUE KEY uk_ai_task_request_message (request_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI MQ任务';

CREATE TABLE IF NOT EXISTS ai_detection_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT DEFAULT NULL,
  camera_device_id BIGINT DEFAULT NULL,
  personnel_id BIGINT DEFAULT NULL,
  detect_type VARCHAR(40) NOT NULL,
  confidence DECIMAL(8,6) DEFAULT NULL,
  snapshot_file_id BIGINT DEFAULT NULL,
  result_json JSON DEFAULT NULL,
  occurred_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_ai_detection_task (task_id),
  KEY idx_ai_detection_time (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI检测结果';

CREATE TABLE IF NOT EXISTS ai_model_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(64) NOT NULL UNIQUE,
  model_name VARCHAR(80) NOT NULL,
  model_type VARCHAR(40) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置';

CREATE TABLE IF NOT EXISTS file_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bucket VARCHAR(80) NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  url VARCHAR(500) DEFAULT NULL,
  file_name VARCHAR(160) DEFAULT NULL,
  content_type VARCHAR(80) DEFAULT NULL,
  biz_type VARCHAR(40) DEFAULT NULL,
  biz_id BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_file_object (bucket, object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS文件元数据';

CREATE TABLE IF NOT EXISTS mq_message_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id VARCHAR(80) NOT NULL,
  topic VARCHAR(120) NOT NULL,
  device_code VARCHAR(64) DEFAULT NULL,
  consume_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1成功 2失败',
  error_message VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_mq_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消息日志';

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(80) NOT NULL UNIQUE,
  config_value VARCHAR(500) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT DEFAULT NULL,
  operation_type VARCHAR(40) NOT NULL,
  content VARCHAR(500) DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志';

INSERT INTO admin_user (username, password_hash, real_name, email, status)
SELECT 'admin', '$2a$10$SrSyoJ4zsU/tzJFFe6rxbeWxJxDqJyZiwljQUZKg4molMHl8QHoY.', '系统管理员', 'admin@example.com', 1
WHERE NOT EXISTS (SELECT 1 FROM admin_user WHERE username = 'admin');

INSERT INTO project_site (id, name, address, manager_name, manager_phone, status)
SELECT 1, '青银高速以东改造项目', '青银高速以东施工区域', '项目管理员', '13295482898', 1
WHERE NOT EXISTS (SELECT 1 FROM project_site WHERE id = 1);

INSERT INTO site_area (id, site_id, name, sort, status)
SELECT 1, 1, '东区', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM site_area WHERE id = 1);

INSERT INTO device_type (id, parent_id, name, code, sort, status)
SELECT 1, 0, '大型设备', 'large_equipment', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM device_type WHERE id = 1);

INSERT INTO device_type (id, parent_id, name, code, sort, status)
SELECT 2, 1, '塔吊', 'tower_crane', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM device_type WHERE id = 2);
