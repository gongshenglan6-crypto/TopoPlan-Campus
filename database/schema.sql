-- TopoPlan schema only; no private records or model keys.
CREATE DATABASE IF NOT EXISTS topo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE topo;
SET NAMES utf8mb4;

CREATE TABLE `admin_configs` (
  `id` bigint(20) NOT NULL,
  `item_key` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `module` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `secret_payload` text COLLATE utf8mb4_unicode_ci
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_models` (
  `id` bigint(20) NOT NULL,
  `allowed_tiers` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cached_token_price` decimal(18,8) NOT NULL,
  `context_window` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) NOT NULL,
  `encrypted_api_key` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_token_price` decimal(18,8) NOT NULL,
  `max_concurrency` int(11) NOT NULL,
  `max_output_tokens` int(11) NOT NULL,
  `model_id` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_token_price` decimal(18,8) NOT NULL,
  `price_unit` bigint(20) NOT NULL,
  `priority` int(11) NOT NULL,
  `provider_name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `temperature` decimal(8,4) NOT NULL,
  `timeout_seconds` int(11) NOT NULL,
  `topp` decimal(8,4) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_selectable` bit(1) NOT NULL,
  `current_concurrency` int(11) NOT NULL,
  `last_latency_ms` bigint(20) NOT NULL,
  `quota_multiplier` decimal(18,8) NOT NULL,
  `rpm_limit` int(11) NOT NULL,
  `tpm_limit` bigint(20) NOT NULL,
  `last_test_at` datetime(6) DEFAULT NULL,
  `last_test_error_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_test_error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_test_latency_ms` bigint(20) NOT NULL DEFAULT '0',
  `last_test_status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_TESTED',
  `consecutive_failures` int(11) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `health_cooldown_until` datetime(6) DEFAULT NULL,
  `health_status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_failure_at` datetime(6) DEFAULT NULL,
  `last_success_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_model_task_bindings` (
  `id` bigint(20) NOT NULL,
  `business_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) NOT NULL,
  `model_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_usage_logs` (
  `id` bigint(20) NOT NULL,
  `business_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_cost` decimal(18,8) NOT NULL,
  `input_tokens` bigint(20) NOT NULL,
  `input_unit_price` decimal(18,8) NOT NULL,
  `latency_ms` bigint(20) NOT NULL,
  `model_id` bigint(20) NOT NULL,
  `output_cost` decimal(18,8) NOT NULL,
  `output_tokens` bigint(20) NOT NULL,
  `output_unit_price` decimal(18,8) NOT NULL,
  `provider` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_cost` decimal(18,8) NOT NULL,
  `total_tokens` bigint(20) NOT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_user_quotas` (
  `id` bigint(20) NOT NULL,
  `ai_chat_remaining` int(11) NOT NULL,
  `current_month_cost` decimal(18,8) NOT NULL,
  `document_generate_remaining` int(11) NOT NULL,
  `monthly_token_limit` bigint(20) NOT NULL,
  `requirement_analysis_remaining` int(11) NOT NULL,
  `reset_at` datetime(6) NOT NULL,
  `topology_generate_remaining` int(11) NOT NULL,
  `total_used_tokens` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `used_input_tokens` bigint(20) NOT NULL,
  `used_output_tokens` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `web_search_remaining` int(11) NOT NULL,
  `allowed_model_ids` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `custom_daily_calls` int(11) DEFAULT NULL,
  `rpm_limit` int(11) DEFAULT NULL,
  `tpm_limit` bigint(20) DEFAULT NULL,
  `reserved_ai_calls` int(11) NOT NULL,
  `reserved_knowledge_qa_calls` int(11) NOT NULL,
  `reserved_network_plan_calls` int(11) NOT NULL,
  `reserved_tokens` bigint(20) NOT NULL,
  `reserved_topology_calls` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_logs` (
  `id` bigint(20) NOT NULL,
  `action` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint(20) DEFAULT NULL,
  `level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_messages` (
  `id` bigint(20) NOT NULL,
  `content` varchar(8000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_sessions` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `pinned` bit(1) NOT NULL,
  `project_id` bigint(20) NOT NULL,
  `title` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `conversations` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_message_at` datetime(6) NOT NULL,
  `model_id` bigint(20) DEFAULT NULL,
  `pinned` bit(1) NOT NULL,
  `project_id` bigint(20) DEFAULT NULL,
  `title` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `public_token_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `conversation_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `conversation_messages` (
  `id` bigint(20) NOT NULL,
  `content` varchar(12000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `input_tokens` bigint(20) NOT NULL,
  `model_id` bigint(20) DEFAULT NULL,
  `output_tokens` bigint(20) NOT NULL,
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_tokens` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `client_message_id` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `knowledge_chunks` (
  `id` bigint(20) NOT NULL,
  `chunk_index` int(11) NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `document_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `knowledge_documents` (
  `id` bigint(20) NOT NULL,
  `category` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `chunk_count` int(11) NOT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `extracted_text` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `format` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_content` mediumblob NOT NULL,
  `size` bigint(20) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `uploader` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `login_records` (
  `id` bigint(20) NOT NULL,
  `account_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `browser` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `device` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `login_identifier` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `login_method` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operating_system` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `risk_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` tinytext COLLATE utf8mb4_unicode_ci,
  `user_id` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `projects` (
  `id` bigint(20) NOT NULL,
  `color` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_count` int(11) NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `terminals` int(11) NOT NULL,
  `topology_version` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `creation_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'manual',
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `creation_request_id` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `public_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project_files` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `format` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `project_id` bigint(20) NOT NULL,
  `size` bigint(20) NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `extracted_text` mediumtext COLLATE utf8mb4_unicode_ci
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refresh_tokens` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `jti` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `risk_request_counters` (
  `id` bigint(20) NOT NULL,
  `bucket_start` datetime(6) NOT NULL,
  `failure_count` bigint(20) NOT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_count` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `system_service_health` (
  `id` bigint(20) NOT NULL,
  `checked_at` datetime(6) NOT NULL,
  `cpu_percent` double DEFAULT NULL,
  `detail` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_rate` double DEFAULT NULL,
  `memory_percent` double DEFAULT NULL,
  `response_time_ms` bigint(20) DEFAULT NULL,
  `service_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status_changed_at` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `topologies` (
  `id` bigint(20) NOT NULL,
  `edges_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `nodes_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `project_id` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` int(11) NOT NULL,
  `zoom` double NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `topology_versions` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `edges_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `nodes_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `project_id` bigint(20) NOT NULL,
  `version` int(11) NOT NULL,
  `zoom` double NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `daily_quota` int(11) NOT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tier` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar` tinytext COLLATE utf8mb4_unicode_ci,
  `subscription_expires_at` datetime(6) DEFAULT NULL,
  `subscription_started_at` datetime(6) DEFAULT NULL,
  `token_version` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `admin_configs`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_admin_config_module_key` (`module`,`item_key`);

ALTER TABLE `ai_models`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `ai_model_task_bindings`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_ai_model_task` (`model_id`,`business_type`);

ALTER TABLE `ai_usage_logs`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UKhei7bn65apr7o5rc6g6e16a0o` (`request_id`),
  ADD KEY `idx_ai_usage_logs_created_at` (`created_at`),
  ADD KEY `idx_ai_usage_logs_user_created_at` (`user_id`,`created_at`),
  ADD KEY `idx_ai_usage_logs_business_created_at` (`business_type`,`created_at`);

ALTER TABLE `ai_user_quotas`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UK4u540hcjukufmi74a54012hxa` (`user_id`);

ALTER TABLE `audit_logs`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `chat_messages`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `chat_sessions`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `conversations`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UKq07914pcv9kk9toltnas25mrh` (`public_id`),
  ADD UNIQUE KEY `UKmvqhjpvo46qiqbn84q6quip03` (`public_token_hash`);

ALTER TABLE `conversation_messages`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_conversation_message_client` (`conversation_id`,`user_id`,`client_message_id`);

ALTER TABLE `knowledge_chunks`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `knowledge_documents`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `login_records`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_login_records_created_at` (`created_at`),
  ADD KEY `idx_login_records_user_id` (`user_id`);

ALTER TABLE `projects`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UK53wjnl4016isd16aoyfet6bu7` (`creation_request_id`),
  ADD UNIQUE KEY `UK1u4xomqvjov7mnx23fmwevuvo` (`public_id`);

ALTER TABLE `project_files`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `refresh_tokens`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UK18kw8ppjw4gmmgdns9ecyg17b` (`jti`),
  ADD UNIQUE KEY `UKo2mlirhldriil2y7krapq4frt` (`token_hash`);

ALTER TABLE `risk_request_counters`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_risk_request_counter_ip_bucket` (`ip_address`,`bucket_start`);

ALTER TABLE `system_service_health`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_system_service_health_key` (`service_key`);

ALTER TABLE `topologies`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UKjl9n4ihifvdh8hgj8nas6k1g5` (`project_id`);

ALTER TABLE `topology_versions`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  ADD UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`);

ALTER TABLE `admin_configs`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=100;

ALTER TABLE `ai_models`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

ALTER TABLE `ai_model_task_bindings`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=100;

ALTER TABLE `ai_usage_logs`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=120;

ALTER TABLE `ai_user_quotas`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

ALTER TABLE `audit_logs`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=751;

ALTER TABLE `chat_messages`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `chat_sessions`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `conversations`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=74;

ALTER TABLE `conversation_messages`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=219;

ALTER TABLE `knowledge_chunks`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=71;

ALTER TABLE `knowledge_documents`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

ALTER TABLE `login_records`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=197;

ALTER TABLE `projects`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=15;

ALTER TABLE `project_files`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

ALTER TABLE `refresh_tokens`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=356;

ALTER TABLE `risk_request_counters`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6340;

ALTER TABLE `system_service_health`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

ALTER TABLE `topologies`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

ALTER TABLE `topology_versions`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=401;

ALTER TABLE `users`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;
