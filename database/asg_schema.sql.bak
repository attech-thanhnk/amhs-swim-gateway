-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: asg_db
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.22.04.2

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `accounts`
--

DROP TABLE IF EXISTS `accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_name` varchar(50) DEFAULT NULL,
  `protocol` varchar(20) DEFAULT NULL,
  `host` varchar(255) DEFAULT NULL,
  `port` int DEFAULT NULL,
  `config_json` text,
  `status` varchar(20) DEFAULT NULL,
  `bind_status` varchar(20) DEFAULT NULL,
  `certificate_path` varchar(500) DEFAULT NULL,
  `certificate_passphrase` text,
  `sasl_mechanism` varchar(20) DEFAULT NULL,
  `tls_enabled` tinyint(1) DEFAULT '0',
  `signed_messages_action` varchar(30) DEFAULT NULL,
  `unsigned_messages_action` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `account_name` (`account_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cp_users`
--

DROP TABLE IF EXISTS `cp_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cp_users` (
  `uuid` varchar(36) NOT NULL,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` varchar(20) DEFAULT 'USER',
  `created_at` datetime DEFAULT NULL,
  `last_login` datetime DEFAULT NULL,
  PRIMARY KEY (`uuid`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_config`
--

DROP TABLE IF EXISTS `gateway_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_config` (
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(500) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gw_alert`
--

DROP TABLE IF EXISTS `gw_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gw_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(30) NOT NULL,
  `severity` varchar(10) NOT NULL,
  `message` text NOT NULL,
  `ref_table` varchar(50) DEFAULT NULL,
  `ref_id` bigint DEFAULT NULL,
  `status` varchar(20) DEFAULT 'NEW',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `acknowledged_at` datetime DEFAULT NULL,
  `acknowledged_by` varchar(100) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gwin`
--

DROP TABLE IF EXISTS `gwin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gwin` (
  `cpa` varchar(1) DEFAULT 'N',
  `msgid` bigint NOT NULL AUTO_INCREMENT,
  `priority` tinyint DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  `xml_payload` mediumtext,
  `TEXT` mediumtext,
  `source` varchar(200) DEFAULT NULL,
  `subject` varchar(100) DEFAULT NULL,
  `amqp_properties` text,
  `body_type` varchar(10) DEFAULT 'text',
  `origin` varchar(200) DEFAULT NULL,
  `message_id` varchar(255) DEFAULT NULL,
  `address` varchar(250) DEFAULT NULL,
  `status` int DEFAULT NULL,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `originator` varchar(128) DEFAULT NULL,
  `addressing_source` varchar(200) DEFAULT NULL,
  `rejection_reason` varchar(64) DEFAULT NULL,
  `rejection_diagnostic` varchar(64) DEFAULT NULL,
  `retry_count` int DEFAULT '0',
  `last_retry_at` datetime DEFAULT NULL,
  `payload_content` mediumtext,
  `error_type` int DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  UNIQUE KEY `message_id` (`message_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gwin_dispatch`
--

DROP TABLE IF EXISTS `gwin_dispatch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gwin_dispatch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gwin_id` bigint NOT NULL,
  `amhs_address` varchar(100) NOT NULL,
  `amhs_account` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error` text,
  `failed_step` varchar(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_gwin_dispatch_msg` (`gwin_id`),
  CONSTRAINT `fk_gwin_dispatch_msg` FOREIGN KEY (`gwin_id`) REFERENCES `gwin` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gwout`
--

DROP TABLE IF EXISTS `gwout`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gwout` (
  `msgid` bigint NOT NULL AUTO_INCREMENT,
  `priority` int DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  `TEXT` varchar(3200) DEFAULT NULL,
  `origin` varchar(8) DEFAULT NULL,
  `address` varchar(250) DEFAULT NULL,
  `optional_heading` varchar(60) DEFAULT NULL,
  `amhs_ttl` datetime DEFAULT NULL,
  `amhs_registered_id` varchar(200) DEFAULT NULL,
  `amhsid` varchar(200) DEFAULT NULL,
  `ipm_id` varchar(200) DEFAULT NULL,
  `filing_time` varchar(6) DEFAULT NULL,
  `priority2` int DEFAULT NULL,
  `status` int DEFAULT NULL,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `body_type` varchar(10) DEFAULT 'text',
  `body_part_type` varchar(50) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `message_signed` varchar(20) DEFAULT NULL,
  `rejection_reason` varchar(64) DEFAULT NULL,
  `rejection_diagnostic` varchar(64) DEFAULT NULL,
  `amhs_delivery_report` tinyint(1) DEFAULT '0',
  `retry_count` int DEFAULT '0',
  `last_retry_at` datetime DEFAULT NULL,
  `payload_content` mediumtext,
  `error_type` int DEFAULT NULL,
  PRIMARY KEY (`msgid`),
  KEY `priority2` (`priority2`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gwout_dispatch`
--

DROP TABLE IF EXISTS `gwout_dispatch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gwout_dispatch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gwout_id` bigint NOT NULL,
  `recipient` varchar(100) NOT NULL,
  `message_type` varchar(50) DEFAULT NULL,
  `scope` varchar(10) DEFAULT NULL,
  `topic` varchar(100) DEFAULT NULL,
  `amqp_account` varchar(50) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error` text,
  `failed_step` varchar(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sent_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_gwout_dispatch_msg` (`gwout_id`),
  CONSTRAINT `fk_gwout_dispatch_msg` FOREIGN KEY (`gwout_id`) REFERENCES `gwout` (`msgid`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_archive`
--

DROP TABLE IF EXISTS `message_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_archive` (
  `uuid` varchar(36) NOT NULL,
  `msg_id` varchar(100) DEFAULT NULL,
  `mts_id` varchar(100) DEFAULT NULL,
  `ipm_id` varchar(100) DEFAULT NULL,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `recipients` text,
  `priority` varchar(2) DEFAULT NULL,
  `direction` varchar(20) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  `raw_content` text,
  `processing_status` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_conversion_log`
--

DROP TABLE IF EXISTS `message_conversion_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_conversion_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reference_id` bigint DEFAULT NULL,
  `date` varchar(8) DEFAULT NULL,
  `type` varchar(4) DEFAULT NULL,
  `category` varchar(12) DEFAULT NULL,
  `message_id` varchar(256) DEFAULT NULL,
  `ipm_id` varchar(256) DEFAULT NULL,
  `mts_id` varchar(256) DEFAULT NULL,
  `amqp_message_id` varchar(256) DEFAULT NULL,
  `priority` varchar(12) DEFAULT NULL,
  `ohi` varchar(64) DEFAULT NULL,
  `origin` varchar(128) DEFAULT NULL,
  `filing_time` varchar(20) DEFAULT NULL,
  `subject` varchar(512) DEFAULT NULL,
  `content` text,
  `converted_time` datetime DEFAULT NULL,
  `status` varchar(8) DEFAULT NULL,
  `action_taken` varchar(50) DEFAULT NULL,
  `non_delivery_reason` varchar(64) DEFAULT NULL,
  `non_delivery_diagnostic` varchar(64) DEFAULT NULL,
  `supplementary_info` varchar(512) DEFAULT NULL,
  `remark` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_log`
--

DROP TABLE IF EXISTS `message_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ack_at` datetime(6) DEFAULT NULL,
  `ack_payload` longtext,
  `amhs_message_type` varchar(50) DEFAULT NULL,
  `amhs_priority` varchar(10) DEFAULT NULL,
  `content_type` varchar(30) DEFAULT NULL,
  `correlation_id` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `direction` varchar(10) DEFAULT NULL,
  `error_code` varchar(100) DEFAULT NULL,
  `error_message` text,
  `message_id` varchar(100) NOT NULL,
  `originator` varchar(8) DEFAULT NULL,
  `processing_status` varchar(255) DEFAULT NULL,
  `processing_step` varchar(50) DEFAULT NULL,
  `processing_time_ms` int DEFAULT NULL,
  `raw_payload` longtext,
  `received_at` datetime(6) DEFAULT NULL,
  `recipients` text,
  `routed_at` datetime(6) DEFAULT NULL,
  `routing_result` varchar(50) DEFAULT NULL,
  `routing_rule_id` int DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `source_system` varchar(20) DEFAULT NULL,
  `swim_priority` int DEFAULT NULL,
  `swim_topic` varchar(255) DEFAULT NULL,
  `target_system` varchar(20) DEFAULT NULL,
  `transformed_payload` longtext,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `message_type_registry`
--

DROP TABLE IF EXISTS `message_type_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_type_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_type` varchar(50) NOT NULL,
  `detect_pattern` varchar(255) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `note` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `message_type` (`message_type`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `performance_metrics`
--

DROP TABLE IF EXISTS `performance_metrics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_metrics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `timestamp` datetime DEFAULT NULL,
  `cpu_usage` float DEFAULT NULL,
  `heap_memory` float DEFAULT NULL,
  `msg_in_count` int DEFAULT NULL,
  `msg_out_count` int DEFAULT NULL,
  `active_threads` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1368 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `routing`
--

DROP TABLE IF EXISTS `routing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `routing` (
  `id` int NOT NULL AUTO_INCREMENT,
  `direction` varchar(3) NOT NULL,
  `receive_topic` varchar(100) DEFAULT NULL,
  `message_filter` varchar(100) DEFAULT NULL,
  `recipients` varchar(500) DEFAULT NULL,
  `originator` varchar(8) DEFAULT NULL,
  `message_type` varchar(50) DEFAULT NULL,
  `send_topic` varchar(100) DEFAULT NULL,
  `priority` int DEFAULT '100',
  `active` tinyint(1) DEFAULT '1',
  `convert_to_json` tinyint(1) DEFAULT '0',
  `note` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(50) DEFAULT NULL,
  `priority_amhs` varchar(255) DEFAULT NULL,
  `priority_swim` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `server_info`
--

DROP TABLE IF EXISTS `server_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `server_info` (
  `uuid` varchar(36) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `ip_address` varchar(255) DEFAULT NULL,
  `server_name` varchar(255) DEFAULT NULL,
  `version` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_history`
--

DROP TABLE IF EXISTS `system_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_time` datetime NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `severity` varchar(20) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` text,
  `created_by` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=51643 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_log`
--

DROP TABLE IF EXISTS `system_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_log` (
  `uuid` varchar(36) NOT NULL,
  `timestamp` datetime DEFAULT NULL,
  `level` varchar(10) DEFAULT NULL,
  `module` varchar(30) DEFAULT NULL,
  `content` text,
  `status` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-10 10:15:50
