-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: asg_db
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.24.04.2

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
-- Dumping data for table `accounts`
--

LOCK TABLES `accounts` WRITE;
/*!40000 ALTER TABLE `accounts` DISABLE KEYS */;
INSERT INTO `accounts` VALUES (1,'solace-broker-primary','AMQP','127.0.0.1',5672,'{\"username\":\"admin\",\"password\":\"admin\",\"vpn\":\"default\",\"client-id\":\"asg-gw-01\"}','ACTIVE','DISCONNECTED',NULL,NULL,'PLAIN',0,'KEEP','ACCEPT'),(2,'isode-mswitch-mta','X400','192.168.1.101',102,'{\"username\":\"mta-user\",\"password\":\"mta-password\",\"vpn\":\"default\"}','ACTIVE','DISCONNECTED',NULL,NULL,'PLAIN',0,'KEEP','ACCEPT');
/*!40000 ALTER TABLE `accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `connection_status`
--

LOCK TABLES `connection_status` WRITE;
/*!40000 ALTER TABLE `connection_status` DISABLE KEYS */;
/*!40000 ALTER TABLE `connection_status` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `cp_users`
--

LOCK TABLES `cp_users` WRITE;
/*!40000 ALTER TABLE `cp_users` DISABLE KEYS */;
INSERT INTO `cp_users` VALUES ('11111111-1111-1111-1111-111111111111','admin','$2a$10$9rwGdXi0PX2nRAEVfQ3zKe0Y/8t2Dx6uxE4HOCjiuvA7.IofHGJzC','ADMIN','2026-05-04 16:05:05','2026-05-05 15:17:12');
/*!40000 ALTER TABLE `cp_users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `error_statistics`
--

LOCK TABLES `error_statistics` WRITE;
/*!40000 ALTER TABLE `error_statistics` DISABLE KEYS */;
/*!40000 ALTER TABLE `error_statistics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gateway_config`
--

LOCK TABLES `gateway_config` WRITE;
/*!40000 ALTER TABLE `gateway_config` DISABLE KEYS */;
INSERT INTO `gateway_config` VALUES ('ALLOWED_ORIGINS','http://192.168.22.159:5173,http://localhost:5173,http://localhost:3000','CORS Allowed Origins (Frontend trên máy 159)','2026-05-04 16:05:05'),('ATSMHS_EXTENDED_CAPABLE_ADDRESSES','','Danh sách địa chỉ hỗ trợ Extended ATSMHS','2026-05-04 16:05:05'),('ATSMHS_SERVICE_LEVEL','CONTENT_BASED','EXTENDED / BASIC / CONTENT_BASED / RECIPIENTS_BASED','2026-05-04 16:05:05'),('AUTHORIZED_AMHS_ADDRESSES','','Whitelist địa chỉ AMHS (nếu dùng BY_LIST)','2026-05-04 16:05:05'),('AUTHORIZED_AMHS_PRMDS','','Whitelist PRMD (nếu dùng BY_PRMD)','2026-05-04 16:05:05'),('AUTHORIZED_AMHS_USERS','ALL','ALL / BY_LIST / BY_PRMD','2026-05-04 16:05:05'),('AUTHORIZED_SWIM_ENTERPRISES','','Whitelist Enterprise SWIM (nếu dùng BY_ENTERPRISE)','2026-05-04 16:05:05'),('AUTHORIZED_SWIM_USERS','ALL','ALL / BY_LIST / BY_ENTERPRISE','2026-05-04 16:05:05'),('CONVERSION_DIRECTION','BOTH','BOTH / AMHS_TO_SWIM / SWIM_TO_AMHS','2026-05-04 16:05:05'),('DEFAULT_ORIGINATOR_AFTN','VVHHZPZX','AFTN originator cho bản tin','2026-05-04 16:05:05'),('GATEWAY_IP','0.0.0.0','IP lắng nghe (0.0.0.0 để nghe mọi card mạng LAN)','2026-05-04 16:05:05'),('INBOUND_BATCH_SIZE','10','Số lượng tin AMHS xử lý mỗi lô','2026-05-04 16:05:05'),('JWT_EXPIRATION_MS','3600000','Thời hạn Token (ms) - Mặc định 1 giờ','2026-05-04 16:05:05'),('JWT_SECRET','asgGatewaySecretKey2026ChangeInProduction!','Khóa bí mật tạo JWT','2026-05-04 16:05:05'),('LOG_RETENTION_DAYS','30','Số ngày giữ log hệ thống','2026-05-04 16:05:05'),('MAX_MESSAGE_RECIPIENTS','20','Max recipients per message (0 = unlimited)','2026-05-04 16:05:05'),('MAX_MSG_DATA_SIZE','2097152','Kích thước tin tối đa (bytes) - Mặc định 2MB','2026-05-04 16:05:05'),('OUTBOUND_BATCH_SIZE','10','Số lượng tin SWIM xử lý mỗi lô','2026-05-04 16:05:05'),('POLL_INTERVAL_MS','500','Tần suất poll tin tức (ms)','2026-05-04 16:05:05'),('RETRY_DELAY_1ST_SECONDS','30','Thời gian chờ retry lần 1 (s)','2026-05-04 16:05:05'),('RETRY_DELAY_2ND_SECONDS','120','Thời gian chờ retry lần 2 (s)','2026-05-04 16:05:05'),('RETRY_DELAY_3RD_SECONDS','300','Thời gian chờ retry lần 3 (s)','2026-05-04 16:05:05'),('RETRY_MAX_COUNT','3','Số lần retry tối đa khi lỗi gửi tin','2026-05-04 16:05:05'),('SERVER_PORT_CP','8180','Cổng dịch vụ Dashboard (CP)','2026-05-04 16:05:05'),('SERVER_PORT_SWIM','8181','Cổng dịch vụ SWIM Component','2026-05-04 16:05:05'),('STRICT_COMPLIANCE_MODE','false','Bật chế độ kiểm tra EUR Doc 047 (S-06)','2026-05-04 16:05:05');
/*!40000 ALTER TABLE `gateway_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gateway_statistics_minute`
--

LOCK TABLES `gateway_statistics_minute` WRITE;
/*!40000 ALTER TABLE `gateway_statistics_minute` DISABLE KEYS */;
/*!40000 ALTER TABLE `gateway_statistics_minute` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gw_alert`
--

LOCK TABLES `gw_alert` WRITE;
/*!40000 ALTER TABLE `gw_alert` DISABLE KEYS */;
/*!40000 ALTER TABLE `gw_alert` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gwin`
--

LOCK TABLES `gwin` WRITE;
/*!40000 ALTER TABLE `gwin` DISABLE KEYS */;
/*!40000 ALTER TABLE `gwin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gwin_dispatch`
--

LOCK TABLES `gwin_dispatch` WRITE;
/*!40000 ALTER TABLE `gwin_dispatch` DISABLE KEYS */;
/*!40000 ALTER TABLE `gwin_dispatch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gwout`
--

LOCK TABLES `gwout` WRITE;
/*!40000 ALTER TABLE `gwout` DISABLE KEYS */;
INSERT INTO `gwout` VALUES (1,4,'2026-05-04 16:05:05','<?xml version=\"1.0\" encoding=\"UTF-8\"?><iwxxm:METAR xmlns:iwxxm=\"http://icao.int/iwxxm/3.0\" xmlns:aixm=\"http://www.aixm.aero/schema/5.1.1\" xmlns:gml=\"http://www.opengis.net/gml/3.2\" status=\"NORMAL\"><iwxxm:aerodrome><aixm:AirportHeliport gml:id=\"ah-vvhh\"><aixm:timeSlice><aixm:AirportHeliportTimeSlice gml:id=\"ahts-vvhh\"><aixm:locationIndicatorICAO>VVHH</aixm:locationIndicatorICAO></aixm:AirportHeliportTimeSlice></aixm:timeSlice></aixm:AirportHeliport></iwxxm:aerodrome></iwxxm:METAR>','VVHHZQZX','VVHHYNYX VVTSYNYX',NULL,NULL,NULL,'VN/HAN/20260407/000001','VVHH.20260407.001','070000',4,0,NULL,'text','ia5-text','text/plain',NULL,NULL,NULL,0,0,NULL,NULL),(2,5,'2026-05-04 16:05:05','<?xml version=\"1.0\" encoding=\"UTF-8\"?><fx:FlightPlan xmlns:fx=\"http://www.fixm.aero/flight/4.3\" xmlns:fb=\"http://www.fixm.aero/base/4.3\"><fx:departure><fx:departureAerodrome><fb:locationIndicator>VVCS</fb:locationIndicator></fx:departureAerodrome></fx:departure><fx:arrival><fx:destinationAerodrome><fb:locationIndicator>VVPQ</fb:locationIndicator></fx:destinationAerodrome></fx:arrival><fx:routeTrajectory><fx:route><fx:routeText>VVCS DCT VVDN DCT VVPQ</fx:routeText></fx:route></fx:routeTrajectory></fx:FlightPlan>','VVTSZQZX','VVTSYNYX',NULL,NULL,NULL,'VN/SGN/20260407/000002','VVTS.20260407.001','070030',5,0,NULL,'text','ia5-text','text/plain',NULL,NULL,NULL,0,0,NULL,NULL);
/*!40000 ALTER TABLE `gwout` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `gwout_dispatch`
--

LOCK TABLES `gwout_dispatch` WRITE;
/*!40000 ALTER TABLE `gwout_dispatch` DISABLE KEYS */;
/*!40000 ALTER TABLE `gwout_dispatch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `message_archive`
--

LOCK TABLES `message_archive` WRITE;
/*!40000 ALTER TABLE `message_archive` DISABLE KEYS */;
/*!40000 ALTER TABLE `message_archive` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `message_conversion_log`
--

LOCK TABLES `message_conversion_log` WRITE;
/*!40000 ALTER TABLE `message_conversion_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `message_conversion_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `message_log`
--

LOCK TABLES `message_log` WRITE;
/*!40000 ALTER TABLE `message_log` DISABLE KEYS */;
INSERT INTO `message_log` VALUES (12,'MSG-20260507-000101','CORR-A101','OUT','AMHS','SWIM',1,'MATCHED','METAR','GG','VVTSZQZX','VVTSYMYX VVTSZPZX','ats/met/metar',5,'XML','SUCCESS','ACK_RECEIVED',NULL,NULL,'<METAR>VVTS 070200Z...</METAR>','<iwxxm:METAR>...</iwxxm:METAR>','<ACK>OK</ACK>','2026-05-07 09:01:01','2026-05-07 09:01:02','2026-05-07 09:01:03','2026-05-07 09:01:04',512,'2026-05-07 09:01:04'),(13,'MSG-20260507-000102','CORR-A102','OUT','AMHS','SWIM',2,'MATCHED','SIGMET','SS','VVNBZQZX','VVTSZTZX','ats/met/sigmet',1,'XML','SUCCESS','ACK_RECEIVED',NULL,NULL,'<SIGMET>SEV TURB</SIGMET>','<iwxxm:SIGMET>...</iwxxm:SIGMET>','<ACK>OK</ACK>','2026-05-07 09:03:11','2026-05-07 09:03:12','2026-05-07 09:03:13','2026-05-07 09:03:15',845,'2026-05-07 09:03:15'),(14,'MSG-20260507-000103','CORR-A103','OUT','AMHS','SWIM',3,'MATCHED','FPL','FF','VVNBZQZX','VVTSZPZX','ats/fpl/filed',3,'JSON','FAILED','SEND_FAILED','BROKER_TIMEOUT','Cannot connect to Solace broker','(FPL-VJC123-IS...)',NULL,NULL,'2026-05-07 09:05:20','2026-05-07 09:05:21',NULL,NULL,4210,'2026-05-07 09:05:25'),(15,'MSG-20260507-000104','CORR-A104','IN','SWIM','AMHS',4,'MATCHED','NOTAM','KK','VVGLYNYX','VVTSZQZX','ats/notam',7,'JSON','SUCCESS','ACK_RECEIVED',NULL,NULL,'{\"notam\":\"RWY CLOSED\"}','(NOTAMN A1234/26...)','<ACK>DELIVERED</ACK>','2026-05-07 09:07:02','2026-05-07 09:07:03','2026-05-07 09:07:04','2026-05-07 09:07:05',620,'2026-05-07 09:07:05'),(16,'MSG-20260507-000105','CORR-A105','OUT','AMHS','SWIM',5,'NO_RULE','ASM','DD','VVTSZQZX','VVTSZTZX',NULL,2,'XML','FAILED','ROUTING_FAILED','ROUTE_NOT_FOUND','No routing rule matched','<ASM>...</ASM>',NULL,NULL,'2026-05-07 09:10:00',NULL,NULL,NULL,95,'2026-05-07 09:10:01'),(17,'MSG-20260507-000106','CORR-A106','IN','SWIM','AMHS',6,'MATCHED','FPL','FF','VVTSZQZX','VVNBZPZX','ats/fpl/update',3,'JSON','WAITING_ACK','WAITING_ACK',NULL,NULL,'{\"flight\":\"HVN225\"}','(FPL-HVN225-IS...)',NULL,'2026-05-07 09:12:15','2026-05-07 09:12:16','2026-05-07 09:12:18',NULL,1880,'2026-05-07 09:12:18'),(18,'MSG-20260507-000107','CORR-A107','OUT','AMHS','SWIM',7,'MATCHED','TAF','GG','VVTSZQZX','VVTSYMYX','ats/met/taf',5,'XML','SUCCESS','ACK_RECEIVED',NULL,NULL,'<TAF>VVTS...</TAF>','<iwxxm:TAF>...</iwxxm:TAF>','<ACK>OK</ACK>','2026-05-07 09:15:30','2026-05-07 09:15:31','2026-05-07 09:15:32','2026-05-07 09:15:33',455,'2026-05-07 09:15:33'),(19,'MSG-20260507-000108','CORR-A108','IN','SWIM','AMHS',8,'MATCHED','ALR','SS','VVCRZQZX','VVTSZQZX','ats/emergency/alert',1,'JSON','FAILED','VALIDATION_FAILED','INVALID_JSON','Missing mandatory field aircraftId','{\"alert\":}',NULL,NULL,'2026-05-07 09:18:45',NULL,NULL,NULL,210,'2026-05-07 09:18:46'),(20,'MSG-20260507-000109','CORR-A109','OUT','AMHS','SWIM',9,'MATCHED','CHG','FF','VVTSZQZX','VVNBZPZX','ats/fpl/change',4,'XML','SENDING','SENDING',NULL,NULL,'(CHG-VJC888...)','<fixm:FlightChange>...</fixm:FlightChange>',NULL,'2026-05-07 09:22:11','2026-05-07 09:22:12',NULL,NULL,980,'2026-05-07 09:22:13'),(21,'MSG-20260507-000110','CORR-A110','OUT','AMHS','SWIM',10,'MATCHED','CNL','KK','VVTSZQZX','VVTSZTZX','ats/fpl/cancel',8,'JSON','ACK_TIMEOUT','WAITING_ACK','ACK_TIMEOUT','No ACK received within 30 seconds','(CNL-VJC321...)','{\"cancelFlight\":\"VJC321\"}',NULL,'2026-05-07 09:25:41','2026-05-07 09:25:42','2026-05-07 09:25:43',NULL,30000,'2026-05-07 09:26:13'),(22,'MSG-20260507-000021','CORR-7A91BC22','OUT','AMHS','SWIM',4,'MATCHED','SIGMET','SS','VVNBZQZX','VVTSZQZX VVTSZTZX','ats/met/sigmet',1,'XML','SUCCESS','ACK_RECEIVED',NULL,NULL,'<SIGMET>SEV TURBULENCE OBSERVED</SIGMET>','<iwxxm:SIGMET xmlns:iwxxm=\"http://icao.int/iwxxm/3.0\">\n        <iwxxm:analysis>SEV TURBULENCE OBSERVED</iwxxm:analysis>\n     </iwxxm:SIGMET>','<ACK>MESSAGE ACCEPTED</ACK>','2026-05-07 09:42:11','2026-05-07 09:42:12','2026-05-07 09:42:13','2026-05-07 09:42:14',842,'2026-05-07 10:58:22'),(23,'MSG-20260507-000112','CORR-A112','OUT','AMHS','SWIM',5,'MATCHED','METAR','GG','VVTSZQZX','VVTSYMYX VVNBYMYX','ats/met/metar',5,'XML',NULL,'TRANSFORMING','IWXXM_CONVERT_ERROR','Failed to convert TAC METAR to IWXXM XML','METAR VVTS 071030Z 22008KT 9999 FEW020 33/26 Q1008 NOSIG',NULL,NULL,'2026-05-07 10:32:11','2026-05-07 10:32:12',NULL,NULL,1880,'2026-05-07 11:02:04');
/*!40000 ALTER TABLE `message_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `message_type_registry`
--

LOCK TABLES `message_type_registry` WRITE;
/*!40000 ALTER TABLE `message_type_registry` DISABLE KEYS */;
INSERT INTO `message_type_registry` VALUES (1,'METAR','<iwxxm:METAR','easy',1,1,'IWXXM METAR'),(2,'FPL','<fx:FlightPlan','easy',1,1,'FIXM Flight Plan'),(3,'NOTAM','<aixm:Event','medium',1,1,'AIXM NOTAM'),(4,'TAF','<iwxxm:TAF','easy',1,1,'IWXXM TAF'),(5,'SIGMET','<iwxxm:SIGMET','medium',1,1,'IWXXM SIGMET'),(6,'SPECI','<iwxxm:SPECI','easy',1,1,'IWXXM SPECI'),(7,'METAR_TEXT','METAR ','easy',1,1,'Legacy TAC METAR'),(8,'SPECI_TEXT','SPECI ','easy',1,1,'Legacy TAC SPECI'),(9,'FPL_TEXT','(FPL-','easy',1,1,'Legacy TAC FPL'),(10,'DEP_TEXT','(DEP-','easy',1,1,'Legacy TAC DEP'),(11,'ARR_TEXT','(ARR-','easy',1,1,'Legacy TAC ARR'),(12,'CHG_TEXT','(CHG-','easy',1,1,'Legacy TAC CHG'),(13,'CNL_TEXT','(CNL-','easy',1,1,'Legacy TAC CNL'),(14,'DLA_TEXT','(DLA-','easy',1,1,'Legacy TAC DLA'),(15,'NOTAM_TEXT','NOTAM ','easy',1,1,'Legacy TAC NOTAM'),(16,'TAF_TEXT','TAF ','easy',1,1,'Legacy TAC TAF'),(17,'SIGMET_TEXT','SIGMET ','easy',1,1,'Legacy TAC SIGMET'),(18,'AIRMET_TEXT','AIRMET ','easy',1,1,'Legacy TAC AIRMET'),(19,'GAMET_TEXT','GAMET ','easy',1,1,'Legacy TAC GAMET'),(20,'ARS_TEXT','(ARS-','easy',1,1,'Legacy TAC AIREP AIREP SPECIAL'),(21,'ARP_TEXT','(ARP-','easy',1,1,'Legacy TAC AIREP'),(22,'VAA_TEXT','VAA ','easy',1,1,'Legacy TAC Volcanic Ash Advisory'),(23,'TCA_TEXT','TCA ','easy',1,1,'Legacy TAC Tropical Cyclone Advisory'),(24,'ASHTAM_TEXT','ASHTAM ','easy',1,1,'Legacy TAC ASHTAM');
/*!40000 ALTER TABLE `message_type_registry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `message_type_statistics`
--

LOCK TABLES `message_type_statistics` WRITE;
/*!40000 ALTER TABLE `message_type_statistics` DISABLE KEYS */;
/*!40000 ALTER TABLE `message_type_statistics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `performance_metrics`
--

LOCK TABLES `performance_metrics` WRITE;
/*!40000 ALTER TABLE `performance_metrics` DISABLE KEYS */;
/*!40000 ALTER TABLE `performance_metrics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `routing`
--

LOCK TABLES `routing` WRITE;
/*!40000 ALTER TABLE `routing` DISABLE KEYS */;
INSERT INTO `routing` VALUES (45,'OUT',NULL,NULL,NULL,'VVNBZQZX','SIGMET','ats/met/sigmet',1,1,'Severe weather – highest priority','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','SS',0),(46,'OUT',NULL,NULL,NULL,'VVTSZQZX','ASHTAM','ats/aim/ashtam',2,1,'Volcanic ash warning','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','SS',1),(47,'OUT',NULL,NULL,NULL,'VVDNZQZX','AIRMET','ats/met/airmet',5,1,'Regional weather hazard','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','DD',2),(48,'OUT',NULL,NULL,NULL,'VVNBZQZX','SNOWTAM','ats/aim/snowtam',6,1,'Runway contamination','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','DD',3),(49,'OUT',NULL,NULL,NULL,'VVNBZQZX','METAR','ats/met/metar',10,1,'Routine METAR','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','FF',4),(50,'OUT',NULL,NULL,NULL,'VVTSZQZX','SPECI','ats/met/speci',10,0,'','2026-05-05 16:56:49','2026-05-06 16:03:41','admin','FF',5),(51,'OUT',NULL,NULL,NULL,'VVDNZQZX','FPL','ats/flightplan/filed',15,1,'Filed flight plan','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','GG',6),(52,'OUT',NULL,NULL,NULL,'VVNBZQZX','CHG','ats/flightplan/change',15,1,'Flight plan change','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','GG',7),(53,'OUT',NULL,NULL,NULL,'VVTSZQZX','CNL','ats/flightplan/cancel',20,1,'Cancel flight plan','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','KK',8),(54,'OUT',NULL,NULL,NULL,'VVNBZQZX','DLA','ats/flightplan/delay',25,1,'Delay flight plan – lowest','2026-05-05 16:56:49','2026-05-05 16:56:49','admin','KK',9),(55,'IN','ats/met/sigmethaha',NULL,'VVHHZQZX VVTSZQZX VVDNZQZX','VVNBZQZX',NULL,'',1,1,'SIGMET to all ACC','2026-05-05 16:56:53','2026-05-06 15:30:35','admin','SS',9),(56,'IN','ats/aim/ashtam',NULL,'VVHHZQZX VVTSZQZX','VVNBZQZX',NULL,NULL,2,1,'ASHTAM volcanic ash','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','SS',1),(57,'IN','ats/met/airmet',NULL,'VVHHZQZX VVDNZQZX','VVNBZQZX',NULL,NULL,4,1,'AIRMET regional','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','DD',2),(58,'IN','ats/aim/snowtam',NULL,'VVNBZTZX','VVNBZQZX',NULL,NULL,5,1,'Snowtam to airport ops','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','DD',3),(59,'IN','ats/met/metar',NULL,'VVHHZTZX VVDNZTZX','VVNBZQZX',NULL,'',10,1,'Routine METAR distribution','2026-05-05 16:56:53','2026-05-06 15:25:32','admin','FF',4),(60,'IN','ats/met/speci',NULL,'VVHHZTZX','VVNBZQZX',NULL,'',10,1,'SPECI to MET office','2026-05-05 16:56:53','2026-05-06 15:39:25','admin','FF',5),(61,'IN','ats/flightplan/filed',NULL,'VVHHZPZX VVTSZPZX','VVNBZQZX',NULL,NULL,15,1,'Incoming FPL','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','GG',6),(62,'IN','ats/flightplan/change',NULL,'VVHHZPZX','VVNBZQZX',NULL,NULL,18,1,'Incoming CHG','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','GG',7),(63,'IN','ats/flightplan/cancel',NULL,'VVHHZPZX','VVNBZQZX',NULL,NULL,20,1,'Incoming CNL','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','KK',8),(64,'IN','ats/flightplan/delay',NULL,'VVHHZPZX','VVNBZQZX',NULL,NULL,25,1,'Incoming DLA – lowest priority','2026-05-05 16:56:53','2026-05-05 16:56:53','admin','KK',9),(65,'OUT','ats/met/metar','METAR','VVHHZTZX VVTSZDYX','VVHHZQZX','METAR','ats/met/metar',100,1,'METAR routing to SWIM','2026-05-06 10:38:06','2026-05-06 10:38:06',NULL,'FF',3),(66,'IN','ats/met/metar',NULL,'VVHHZTZX VVDNZTZX','VVNBZQZX',NULL,'',100,1,'Routine METAR distribution','2026-05-06 11:11:44','2026-05-06 11:11:44',NULL,'FF',3),(67,'IN','ats/met/sigmet',NULL,'VVHHZQZX VVTSZQZX VVDNZQZX','VVNBZQZX',NULL,'',100,0,'SIGMET to all ACC','2026-05-06 11:12:19','2026-05-06 11:12:19',NULL,'FF',3),(69,'IN','ats/met/sigmet',NULL,'VVHHZQZX VVTSZQZX VVDNZQZX','VVNBZQZX',NULL,'',100,1,'SIGMET to all ACC','2026-05-06 13:32:47','2026-05-06 13:32:47',NULL,'FF',3),(70,'IN','ats/met/sigmet',NULL,'VVHHZQZX VVTSZQZX VVDNZQZX','VVNBZQZX',NULL,'',100,0,'SIGMET to all ACC','2026-05-06 13:34:09','2026-05-06 13:34:09',NULL,'FF',3),(71,'IN','ats/aim/ashtam',NULL,'VVHHZQZX VVTSZQZX','VVNBZQZX',NULL,'',100,1,'ASHTAM volcanic ash','2026-05-06 14:11:48','2026-05-06 14:11:48',NULL,'SS',1),(72,'IN','ats/met/airmet',NULL,'VVHHZQZX VVDNZQZX','VVNBZQZX',NULL,'',100,1,'AIRMET regional','2026-05-06 14:14:32','2026-05-06 14:14:32',NULL,'DD',2),(73,'IN','ats/aim/ashtam',NULL,'VVHHZQZX VVTSZQZX','VVNBZQZX',NULL,'',100,1,'ASHTAM volcanic ash','2026-05-06 14:14:35','2026-05-06 14:14:35',NULL,'SS',1),(74,'IN','ats/aim/ashtam',NULL,'VVHHZQZX VVTSZQZX','VVNBZQZX',NULL,'',100,1,'ASHTAM volcanic ash','2026-05-06 14:15:01','2026-05-06 14:15:01',NULL,'SS',1);
/*!40000 ALTER TABLE `routing` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `routing_statistics`
--

LOCK TABLES `routing_statistics` WRITE;
/*!40000 ALTER TABLE `routing_statistics` DISABLE KEYS */;
/*!40000 ALTER TABLE `routing_statistics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `server_info`
--

LOCK TABLES `server_info` WRITE;
/*!40000 ALTER TABLE `server_info` DISABLE KEYS */;
INSERT INTO `server_info` VALUES ('0096c6e4-c9ba-4bb1-b5dc-66f2a7634611','ASG Gateway Parent Project Version 1','127.0.1.1','DESKTOP-Q23EH77','@project.parent.version@'),('07dd74aa-1b63-49ba-ac96-08ccb1c70a84','ASG Gateway Parent Project Version 1','127.0.1.1','DESKTOP-Q23EH77','1.0.0');
/*!40000 ALTER TABLE `server_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `system_history`
--

LOCK TABLES `system_history` WRITE;
/*!40000 ALTER TABLE `system_history` DISABLE KEYS */;
INSERT INTO `system_history` VALUES (32,'system',NULL,'2026-06-12 15:59:30.826908','APPLICATION_STOP','INFO','Application stopped'),(33,'system',NULL,'2026-06-12 15:59:54.942160','APPLICATION_START','INFO','Application started'),(34,'system',NULL,'2026-06-12 15:59:55.850492','HIGH_MEMORY','WARN','Memory usage reached 45.66%'),(35,'system',NULL,'2026-06-12 17:02:27.736100','APPLICATION_STOP','INFO','Application stopped'),(36,'system',NULL,'2026-06-12 17:09:37.469253','APPLICATION_START','INFO','Application started'),(37,'system',NULL,'2026-06-12 17:09:38.350266','HIGH_MEMORY','WARN','Memory usage reached 45.89%'),(38,'system',NULL,'2026-06-12 17:19:00.542976','APPLICATION_STOP','INFO','Application stopped'),(39,'system',NULL,'2026-06-12 17:19:18.903759','APPLICATION_START','INFO','Application started'),(40,'system',NULL,'2026-06-12 17:19:19.650883','HIGH_MEMORY','WARN','Memory usage reached 43.02%'),(41,'system',NULL,'2026-06-12 17:26:20.141570','APPLICATION_STOP','INFO','Application stopped'),(42,'system',NULL,'2026-06-12 17:26:48.422003','APPLICATION_START','INFO','Application started'),(43,'system',NULL,'2026-06-12 17:26:49.517974','HIGH_MEMORY','WARN','Memory usage reached 45.26%'),(44,'system',NULL,'2026-06-12 17:29:26.840388','APPLICATION_STOP','INFO','Application stopped'),(45,'system',NULL,'2026-06-12 17:29:52.695235','APPLICATION_START','INFO','Application started'),(46,'system',NULL,'2026-06-12 17:29:53.285034','HIGH_MEMORY','WARN','Memory usage reached 48.03%'),(47,'system',NULL,'2026-06-12 17:33:11.941804','APPLICATION_STOP','INFO','Application stopped'),(48,'system',NULL,'2026-06-12 17:37:01.207392','APPLICATION_START','INFO','Application started'),(49,'system',NULL,'2026-06-12 17:37:01.824458','HIGH_MEMORY','WARN','Memory usage reached 45.01%'),(50,'system',NULL,'2026-06-15 14:56:08.833525','APPLICATION_START','INFO','Application started'),(51,'system',NULL,'2026-06-15 14:56:09.668957','HIGH_MEMORY','WARN','Memory usage reached 36.16%'),(52,'system',NULL,'2026-06-15 15:02:50.803321','APPLICATION_STOP','INFO','Application stopped'),(53,'system',NULL,'2026-06-15 15:04:22.553785','APPLICATION_STOP','INFO','Application stopped'),(54,'system',NULL,'2026-06-15 15:14:13.821311','APPLICATION_STOP','INFO','Application stopped'),(55,'system',NULL,'2026-06-15 15:14:37.624254','APPLICATION_START','INFO','Application started'),(56,'system',NULL,'2026-06-15 15:14:38.140657','HIGH_MEMORY','WARN','Memory usage reached 46.28%'),(57,'system',NULL,'2026-06-15 16:01:13.674660','APPLICATION_STOP','INFO','Application stopped'),(58,'system',NULL,'2026-06-15 16:01:43.674351','APPLICATION_START','INFO','Application started'),(59,'system',NULL,'2026-06-15 16:01:44.252114','HIGH_MEMORY','WARN','Memory usage reached 48.75%'),(60,'system',NULL,'2026-06-15 16:02:26.784299','APPLICATION_STOP','INFO','Application stopped'),(61,'system',NULL,'2026-06-15 16:04:39.863596','APPLICATION_START','INFO','Application started'),(62,'system',NULL,'2026-06-15 16:04:40.359568','HIGH_MEMORY','WARN','Memory usage reached 44.59%'),(63,'system',NULL,'2026-06-15 16:05:12.419544','APPLICATION_STOP','INFO','Application stopped'),(64,'system',NULL,'2026-06-15 16:07:17.683376','APPLICATION_START','INFO','Application started'),(65,'system',NULL,'2026-06-15 16:07:18.430158','HIGH_MEMORY','WARN','Memory usage reached 44.19%'),(66,'system',NULL,'2026-06-15 16:07:23.122783','APPLICATION_STOP','INFO','Application stopped'),(67,'system',NULL,'2026-06-15 16:14:18.427598','APPLICATION_START','INFO','Application started'),(68,'system',NULL,'2026-06-15 16:14:19.093442','HIGH_MEMORY','WARN','Memory usage reached 47.15%'),(69,'system',NULL,'2026-06-15 16:45:48.825298','APPLICATION_STOP','INFO','Application stopped'),(70,'system',NULL,'2026-06-15 17:04:25.427541','APPLICATION_START','INFO','Application started'),(71,'system',NULL,'2026-06-15 17:04:26.090586','HIGH_MEMORY','WARN','Memory usage reached 47.36%'),(72,'system',NULL,'2026-06-16 09:01:23.581167','APPLICATION_START','INFO','Application started'),(73,'system',NULL,'2026-06-16 09:01:24.478560','HIGH_MEMORY','WARN','Memory usage reached 36.90%'),(74,'system',NULL,'2026-06-16 09:01:24.817162','APPLICATION_STOP','INFO','Application stopped'),(75,'system',NULL,'2026-06-17 15:54:19.064481','APPLICATION_START','INFO','Application started'),(76,'system',NULL,'2026-06-17 15:54:19.395714','HIGH_MEMORY','WARN','Memory usage reached 36.19%'),(77,'SYSTEM','Bộ nhớ RAM vượt ngưỡng 90% trên máy chủ Gateway','2026-06-17 16:44:50.755208','HIGH_MEMORY','WARNING','Cảnh báo: Bộ nhớ RAM cao'),(78,'SYSTEM','Bộ nhớ RAM vượt ngưỡng 90% trên máy chủ Gateway','2026-06-17 16:45:00.608869','HIGH_MEMORY','WARNING','Cảnh báo: Bộ nhớ RAM cao'),(79,'system',NULL,'2026-06-17 17:15:15.371214','APPLICATION_STOP','INFO','Application stopped'),(80,'system',NULL,'2026-06-17 17:15:42.233774','APPLICATION_START','INFO','Application started'),(81,'system',NULL,'2026-06-17 17:15:42.636158','HIGH_MEMORY','WARN','Memory usage reached 60.37%'),(82,'system',NULL,'2026-06-17 17:41:46.976848','APPLICATION_STOP','INFO','Application stopped'),(83,'system',NULL,'2026-06-17 17:42:04.307606','APPLICATION_START','INFO','Application started'),(84,'system',NULL,'2026-06-17 17:42:04.857552','HIGH_MEMORY','WARN','Memory usage reached 55.70%'),(85,'system',NULL,'2026-06-17 18:01:32.946827','APPLICATION_STOP','INFO','Application stopped'),(86,'system',NULL,'2026-06-18 09:22:36.844567','APPLICATION_START','INFO','Application started'),(87,'system',NULL,'2026-06-18 09:22:37.148866','HIGH_MEMORY','WARN','Memory usage reached 36.30%'),(88,'system',NULL,'2026-06-18 15:39:59.761293','ACCOUNT_UPDATED','INFO','Account \'isode-mswitch-mta\' updated. ');
/*!40000 ALTER TABLE `system_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `system_log`
--

LOCK TABLES `system_log` WRITE;
/*!40000 ALTER TABLE `system_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `system_statistics`
--

LOCK TABLES `system_statistics` WRITE;
/*!40000 ALTER TABLE `system_statistics` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_statistics` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `user_system_history`
--

LOCK TABLES `user_system_history` WRITE;
/*!40000 ALTER TABLE `user_system_history` DISABLE KEYS */;
INSERT INTO `user_system_history` VALUES (11,_binary '',33,1),(12,_binary '\0',33,2),(13,_binary '',34,1),(14,_binary '\0',34,2),(15,_binary '',35,1),(16,_binary '\0',35,2),(17,_binary '',36,1),(18,_binary '\0',36,2),(19,_binary '',37,1),(20,_binary '\0',37,2),(21,_binary '',38,1),(22,_binary '\0',38,2),(23,_binary '',39,1),(24,_binary '\0',39,2),(25,_binary '',40,1),(26,_binary '\0',40,2),(27,_binary '',41,1),(28,_binary '\0',41,2),(29,_binary '',42,1),(30,_binary '\0',42,2),(31,_binary '',43,1),(32,_binary '\0',43,2),(33,_binary '',44,1),(34,_binary '\0',44,2),(35,_binary '',45,1),(36,_binary '\0',45,2),(37,_binary '',46,1),(38,_binary '\0',46,2),(39,_binary '',47,1),(40,_binary '\0',47,2),(41,_binary '',48,1),(42,_binary '\0',48,2),(43,_binary '',49,1),(44,_binary '\0',49,2),(45,_binary '',50,1),(46,_binary '\0',50,2),(47,_binary '',51,1),(48,_binary '\0',51,2),(49,_binary '',52,1),(50,_binary '\0',52,2),(51,_binary '',53,1),(52,_binary '\0',53,2),(53,_binary '',54,1),(54,_binary '\0',54,2),(55,_binary '',55,1),(56,_binary '\0',55,2),(57,_binary '',56,1),(58,_binary '\0',56,2),(59,_binary '',57,1),(60,_binary '\0',57,2),(61,_binary '',58,1),(62,_binary '\0',58,2),(63,_binary '',59,1),(64,_binary '\0',59,2),(65,_binary '',60,1),(66,_binary '\0',60,2),(67,_binary '',61,1),(68,_binary '\0',61,2),(69,_binary '',62,1),(70,_binary '\0',62,2),(71,_binary '',63,1),(72,_binary '\0',63,2),(73,_binary '',64,1),(74,_binary '\0',64,2),(75,_binary '',65,1),(76,_binary '\0',65,2),(77,_binary '',66,1),(78,_binary '\0',66,2),(79,_binary '',67,1),(80,_binary '\0',67,2),(81,_binary '',68,1),(82,_binary '\0',68,2),(83,_binary '',69,1),(84,_binary '\0',69,2),(85,_binary '',70,1),(86,_binary '\0',70,2),(87,_binary '',71,1),(88,_binary '\0',71,2),(89,_binary '',72,1),(90,_binary '\0',72,2),(91,_binary '',73,1),(92,_binary '\0',73,2),(93,_binary '',74,1),(94,_binary '\0',74,2),(95,_binary '',75,1),(96,_binary '\0',75,2),(97,_binary '',76,1),(98,_binary '\0',76,2),(99,_binary '',77,1),(100,_binary '',78,1),(101,_binary '',79,1),(102,_binary '\0',79,2),(103,_binary '',80,1),(104,_binary '\0',80,2),(105,_binary '',81,1),(106,_binary '\0',81,2),(107,_binary '\0',82,1),(108,_binary '\0',82,2),(109,_binary '\0',83,1),(110,_binary '\0',83,2),(111,_binary '\0',84,1),(112,_binary '\0',84,2),(113,_binary '\0',85,1),(114,_binary '\0',85,2),(115,_binary '\0',86,1),(116,_binary '\0',86,2),(117,_binary '\0',87,1),(118,_binary '\0',87,2),(119,_binary '\0',88,1),(120,_binary '\0',88,2);
/*!40000 ALTER TABLE `user_system_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,NULL,'2026-06-12 10:12:21.000000','admin@example.com','System Administrator',_binary '','2026-06-18 15:35:27.384742',NULL,'1','admin','2026-06-12 10:12:21.000000','admin'),(2,NULL,'2026-06-12 10:12:21.000000','viewer@example.com','Viewer User',_binary '',NULL,NULL,'1','viewer','2026-06-12 10:12:21.000000','viewer');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-22 10:15:14
