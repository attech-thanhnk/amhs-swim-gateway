# AMHS-SWIM Gateway v2 - Frontend API Documentation

**Version:** 2.0.0
**Base URL:** `http://localhost:8180/api`
**Last Updated:** 2026-04-23

---

## 📑 Table of Contents

1. [Authentication](#1-authentication)
2. [User Management](#2-user-management)
3. [Routing Management](#3-routing-management)
4. [Gateway Configuration](#4-gateway-configuration)
5. [Messages Management](#5-messages-management)
6. [Unrouted Messages](#6-unrouted-messages)
7. [Alerts Management](#7-alerts-management)
8. [Accounts Management](#8-accounts-management)
9. [Traffic Logs](#9-traffic-logs)
10. [System Monitoring](#10-system-monitoring)
11. [System Metrics](#11-system-metrics)
12. [System Logs](#12-system-logs)
13. [Archive Management](#13-archive-management)
14. [Admin Operations](#14-admin-operations)
15. [Metadata](#15-metadata)
16. [Addressing Statistics](#16-addressing-statistics)
17. [Entity Schemas](#17-entity-schemas)
18. [Error Handling](#18-error-handling)

---

## 1. Authentication

### 1.1 Login
```
POST /api/auth/login
```

**Request:**
```json
{
  "username": "admin",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "username": "admin",
  "role": "ADMIN"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid username or password"
}
```

---

### 1.2 Logout
```
POST /api/auth/logout
```

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

---

### 1.3 Refresh Token
```
POST /api/auth/refresh
Headers: Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600
}
```

---

### 1.4 Change Password
```
POST /api/auth/change-password
Headers: Authorization: Bearer <token>
```

**Request:**
```json
{
  "oldPassword": "oldpass123",
  "newPassword": "newpass456"
}
```

**Response (200 OK):**
```json
{
  "message": "Password changed successfully"
}
```

---

## 2. User Management

### 2.1 List All Users
```
GET /api/admin/users
```

**Response (200 OK):**
```json
[
  {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "admin",
    "role": "ADMIN",
    "lastLogin": "2024-01-15T10:30:00",
    "createdAt": "2024-01-01T00:00:00"
  }
]
```

---

### 2.2 Get User by UUID
```
GET /api/admin/users/{uuid}
```

**Response (200 OK):**
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "username": "admin",
  "role": "ADMIN",
  "lastLogin": "2024-01-15T10:30:00",
  "createdAt": "2024-01-01T00:00:00"
}
```

---

### 2.3 Create User
```
POST /api/admin/users
```

**Request:**
```json
{
  "username": "operator01",
  "password": "pass123",
  "role": "OPERATOR"
}
```

**Roles:** `ADMIN`, `OPERATOR`, `USER`

**Response (201 Created):**
```json
{
  "uuid": "660e8400-e29b-41d4-a716-446655440001",
  "username": "operator01",
  "role": "OPERATOR",
  "createdAt": "2024-01-15T12:00:00"
}
```

---

### 2.4 Update User
```
PUT /api/admin/users/{uuid}
```

**Request:**
```json
{
  "username": "operator01_updated",
  "password": "newpass456",
  "role": "USER"
}
```

**Response (200 OK):**
```json
{
  "uuid": "660e8400-e29b-41d4-a716-446655440001",
  "username": "operator01_updated",
  "role": "USER"
}
```

---

### 2.5 Delete User
```
DELETE /api/admin/users/{uuid}
```

**Response (204 No Content)**

---

## 3. Routing Management

### 3.1 List All Routing Rules
```
GET /api/routing
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "direction": "OUT",
    "messageType": "METAR",
    "sendTopic": "ats/met/metar",
    "priority": 100,
    "active": true,
    "note": "METAR routing to SWIM",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  },
  {
    "id": 2,
    "direction": "IN",
    "receiveTopic": "ats/fpl",
    "messageFilter": "FPL",
    "recipients": "VVHHZTZX VVTSZDYX",
    "originator": "VVHHZQZX",
    "priority": 90,
    "active": true,
    "note": "Flight plan routing to AMHS"
  }
]
```

**Field Descriptions:**
- `direction`: `IN` (SWIM→AMHS) or `OUT` (AMHS→SWIM)
- `messageType`: Detected message type (for OUT direction)
- `sendTopic`: AMQP topic to publish (for OUT direction)
- `receiveTopic`: AMQP topic to subscribe (for IN direction)
- `messageFilter`: Optional content filter
- `recipients`: Space-separated AFTN addresses (for IN direction)
- `originator`: AFTN originator address (for IN direction)
- `priority`: Lower number = higher priority
- `active`: Enable/disable rule

---

### 3.2 Get Routing Rule by ID
```
GET /api/routing/{id}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "direction": "OUT",
  "messageType": "METAR",
  "sendTopic": "ats/met/metar",
  "priority": 100,
  "active": true,
  "note": "METAR routing to SWIM"
}
```

---

### 3.3 Create Routing Rule
```
POST /api/routing
```

**Request (Outbound - AMHS→SWIM):**
```json
{
  "direction": "OUT",
  "messageType": "TAF",
  "sendTopic": "ats/met/taf",
  "priority": 95,
  "active": true,
  "note": "TAF routing to SWIM"
}
```

**Request (Inbound - SWIM→AMHS):**
```json
{
  "direction": "IN",
  "receiveTopic": "ats/fpl",
  "messageFilter": "FPL",
  "recipients": "VVHHZTZX",
  "originator": "VVHHZQZX",
  "priority": 90,
  "active": true,
  "note": "Flight plan routing"
}
```

**Validation:**
- `direction` is required (`IN` or `OUT`)
- Topics with dots (`.`) are automatically converted to slashes (`/`)

**Response (201 Created):**
```json
{
  "id": 3,
  "direction": "OUT",
  "messageType": "TAF",
  "sendTopic": "ats/met/taf",
  "priority": 95,
  "active": true,
  "createdAt": "2024-01-15T12:00:00"
}
```

---

### 3.4 Update Routing Rule
```
PUT /api/routing/{id}
```

**Request (partial update supported):**
```json
{
  "priority": 85,
  "active": false,
  "note": "Temporarily disabled"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "priority": 85,
  "active": false,
  "note": "Temporarily disabled",
  "updatedAt": "2024-01-15T13:00:00"
}
```

---

### 3.5 Delete Routing Rule
```
DELETE /api/routing/{id}
```

**Response (204 No Content)**

---

## 4. Gateway Configuration

### 4.1 List All Configurations
```
GET /api/config
```

**Response (200 OK):**
```json
[
  {
    "configKey": "RETRY_MAX_COUNT",
    "configValue": "3",
    "description": "Maximum retry attempts for failed messages",
    "updatedAt": "2024-01-15T10:00:00"
  },
  {
    "configKey": "RETRY_DELAY_1ST_SECONDS",
    "configValue": "30",
    "description": "First retry delay in seconds",
    "updatedAt": "2024-01-15T10:00:00"
  },
  {
    "configKey": "STRICT_COMPLIANCE_MODE",
    "configValue": "false",
    "description": "Enable EUR Doc 047 strict validation",
    "updatedAt": "2024-01-15T10:00:00"
  }
]
```

**Common Configuration Keys:**
- `RETRY_MAX_COUNT` - Max retry attempts (default: 3)
- `RETRY_DELAY_1ST_SECONDS` - 1st retry delay (default: 30)
- `RETRY_DELAY_2ND_SECONDS` - 2nd retry delay (default: 120)
- `RETRY_DELAY_3RD_SECONDS` - 3rd retry delay (default: 300)
- `STRICT_COMPLIANCE_MODE` - EUR Doc 047 strict mode (true/false)
- `OUTBOUND_BATCH_SIZE` - Batch size for polling (default: 50)
- `POLL_INTERVAL_MS` - Poll interval in milliseconds (default: 5000)
- `DEFAULT_ORIGINATOR_AFTN` - Default AFTN originator

---

### 4.2 Get Configuration by Key
```
GET /api/config/{key}
```

**Example:**
```
GET /api/config/RETRY_MAX_COUNT
```

**Response (200 OK):**
```json
{
  "configKey": "RETRY_MAX_COUNT",
  "configValue": "3",
  "description": "Maximum retry attempts for failed messages",
  "updatedAt": "2024-01-15T10:00:00"
}
```

---

### 4.3 Update Configuration
```
PUT /api/config/{key}
```

**Request:**
```json
{
  "value": "5"
}
```

**Response (200 OK):**
```json
{
  "configKey": "RETRY_MAX_COUNT",
  "configValue": "5",
  "description": "Maximum retry attempts for failed messages",
  "updatedAt": "2024-01-15T14:00:00"
}
```

---

## 5. Messages Management

### 5.1 Get Inbound Messages (SWIM→AMHS)
```
GET /api/messages/inbound
```

**Query Parameters:**
- `status` (integer) - Filter by status
  - `0` = PENDING
  - `1` = PROCESSING
  - `2` = SENT
  - `3` = FAILED
  - `4` = DEAD
  - `5` = UNROUTED
- `source` (string) - Filter by AMQP topic
- `fromTime` (string) - ISO 8601 timestamp
- `toTime` (string) - ISO 8601 timestamp
- `page` (integer, default=0) - Page number
- `size` (integer, default=50) - Page size

**Example:**
```
GET /api/messages/inbound?status=0&source=ats/met/metar&page=0&size=20
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "msgid": 1234,
      "messageId": "nm-b2b-fum-20240115-12345",
      "source": "ats/met/metar",
      "subject": "METAR",
      "priority": 2,
      "time": "2024-01-15T10:00:00",
      "text": "METAR VVNB 151000Z 27005KT 9999 FEW020 28/24 Q1012",
      "origin": "VVHHZQZX",
      "address": "VVHHZTZX VVTSZDYX",
      "addressingSource": "ROUTING_RULE",
      "status": 0
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "page": 0
}
```

---

### 5.2 Get Inbound Message Details
```
GET /api/messages/inbound/{msgid}
```

**Response (200 OK):**
```json
{
  "message": {
    "msgid": 1234,
    "messageId": "nm-b2b-fum-20240115-12345",
    "source": "ats/met/metar",
    "subject": "METAR",
    "priority": 2,
    "time": "2024-01-15T10:00:00",
    "text": "METAR VVNB 151000Z 27005KT 9999 FEW020 28/24 Q1012",
    "origin": "VVHHZQZX",
    "address": "VVHHZTZX VVTSZDYX",
    "addressingSource": "ROUTING_RULE",
    "status": 2
  },
  "dispatches": [
    {
      "id": 5001,
      "gwinId": 1234,
      "amhsAddress": "VVHHZTZX",
      "status": "SENT",
      "retryCount": 0,
      "sentAt": "2024-01-15T10:00:30"
    },
    {
      "id": 5002,
      "gwinId": 1234,
      "amhsAddress": "VVTSZDYX",
      "status": "SENT",
      "retryCount": 0,
      "sentAt": "2024-01-15T10:00:31"
    }
  ]
}
```

---

### 5.3 Get Outbound Messages (AMHS→SWIM)
```
GET /api/messages/outbound
```

**Query Parameters:**
- `status` (integer) - Filter by status
  - `0` = PENDING
  - `1` = PROCESSING
  - `2` = SENT
  - `3` = DEAD
- `fromTime` (string) - ISO 8601 timestamp
- `toTime` (string) - ISO 8601 timestamp
- `page` (integer, default=0)
- `size` (integer, default=50)

**Example:**
```
GET /api/messages/outbound?status=2&page=0&size=20
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "msgid": 5678,
      "amhsid": "<20240115100000.12345@amhs.vatm.vn>",
      "priority": 2,
      "time": "2024-01-15T10:00:00",
      "filingTime": "150000",
      "text": "METAR VVNB 151000Z 27005KT 9999 FEW020 28/24 Q1012",
      "origin": "VVHHZQZX",
      "address": "VVHHZTZX VVTSZDYX",
      "status": 2
    }
  ],
  "totalElements": 200,
  "totalPages": 10,
  "page": 0
}
```

---

### 5.4 Get Outbound Message Details
```
GET /api/messages/outbound/{msgid}
```

**Response (200 OK):**
```json
{
  "message": {
    "msgid": 5678,
    "amhsid": "<20240115100000.12345@amhs.vatm.vn>",
    "priority": 2,
    "time": "2024-01-15T10:00:00",
    "text": "METAR VVNB 151000Z 27005KT 9999 FEW020 28/24 Q1012",
    "origin": "VVHHZQZX",
    "status": 2
  },
  "dispatches": [
    {
      "id": 9001,
      "gwoutId": 5678,
      "recipient": "VVHHZTZX",
      "messageType": "METAR",
      "topic": "ats/met/metar",
      "status": "SENT",
      "sentAt": "2024-01-15T10:00:25"
    }
  ]
}
```

---

### 5.5 Retry Inbound Message
```
POST /api/messages/inbound/{msgid}/retry
```

**Response (200 OK):**
```json
{
  "success": true,
  "msgid": 1234,
  "message": "Queued for retry"
}
```

---

### 5.6 Retry Outbound Message
```
POST /api/messages/outbound/{msgid}/retry
```

**Response (200 OK):**
```json
{
  "success": true,
  "msgid": 5678,
  "message": "Queued for retry"
}
```

---

### 5.7 Delete Inbound Message
```
DELETE /api/messages/inbound/{msgid}
```

**Response (204 No Content)**

---

### 5.8 Delete Outbound Message
```
DELETE /api/messages/outbound/{msgid}
```

**Response (204 No Content)**

---

## 6. Unrouted Messages

### 6.1 Get All Unrouted Messages
```
GET /api/addressing/unrouted
```

**Query Parameters:**
- `fromTime` (string) - ISO 8601 timestamp
- `toTime` (string) - ISO 8601 timestamp
- `source` (string) - Filter by AMQP topic
- `page` (integer, default=0)
- `size` (integer, default=50)
- `sort` (string, default="time,desc") - Sort field,direction

**Example:**
```
GET /api/addressing/unrouted?page=0&size=20&sort=time,desc
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "msgid": 9999,
      "messageId": "nm-b2b-unknown-20240115-99999",
      "source": "ats/unknown/topic",
      "subject": "UNKNOWN",
      "time": "2024-01-15T10:00:00",
      "text": "Unknown message content...",
      "addressingSource": "UNRESOLVED",
      "status": 5
    }
  ],
  "totalElements": 25,
  "totalPages": 2,
  "currentPage": 0,
  "size": 20
}
```

---

### 6.2 Get Unrouted Message by ID
```
GET /api/addressing/unrouted/{msgid}
```

**Response (200 OK):**
```json
{
  "msgid": 9999,
  "messageId": "nm-b2b-unknown-20240115-99999",
  "source": "ats/unknown/topic",
  "text": "Unknown message content...",
  "addressingSource": "UNRESOLVED",
  "status": 5
}
```

---

### 6.3 Manually Route Message
```
POST /api/addressing/unrouted/{msgid}/route
```

**Request:**
```json
{
  "originator": "VVHHZQZX",
  "recipients": "VVHHZTZX VVTSZDYX",
  "note": "Manually routed by operator"
}
```

**Validation:**
- `originator`: Required, 8 uppercase letters
- `recipients`: Required, max 500 characters
- `note`: Optional, max 500 characters

**Response (200 OK):**
```json
{
  "msgid": 9999,
  "origin": "VVHHZQZX",
  "address": "VVHHZTZX VVTSZDYX",
  "addressingSource": "MANUAL_ROUTE",
  "status": 0
}
```

---

### 6.4 Reject Message
```
POST /api/addressing/unrouted/{msgid}/reject
```

**Request:**
```json
{
  "reason": "Invalid message format",
  "note": "Message does not conform to ICAO standards"
}
```

**Response (200 OK):**
```json
{
  "msgid": 9999,
  "status": 4
}
```

---

### 6.5 Batch Route Messages
```
POST /api/addressing/unrouted/batch-route
```

**Request:**
```json
{
  "msgids": [9999, 10000, 10001],
  "originator": "VVHHZQZX",
  "recipients": "VVHHZTZX VVTSZDYX",
  "note": "Batch routed by operator"
}
```

**Response (200 OK):**
```json
{
  "processed": 3,
  "succeeded": 2,
  "failed": 1,
  "errors": [
    {
      "msgid": 10001,
      "error": "Message not found"
    }
  ]
}
```

---

## 7. Alerts Management

### 7.1 List All Alerts
```
GET /api/alerts
```

**Response (200 OK):**
```json
[
  {
    "id": 101,
    "alertType": "CONNECTION_LOST",
    "severity": "CRITICAL",
    "message": "Lost connection to LOCAL_BROKER after 3 retry attempts",
    "refTable": "accounts",
    "refId": 5,
    "status": "NEW",
    "createdAt": "2024-01-15T10:00:00"
  },
  {
    "id": 102,
    "alertType": "MESSAGE_DEAD",
    "severity": "WARNING",
    "message": "Message 1234 marked as DEAD after max retries",
    "status": "ACKNOWLEDGED",
    "acknowledgedAt": "2024-01-15T09:35:00",
    "acknowledgedBy": "operator01"
  }
]
```

**Alert Types:**
- `CONNECTION_LOST` - AMQP/AMHS connection lost
- `MESSAGE_DEAD` - Message failed after max retries
- `QUEUE_BACKLOG` - Message queue backlog warning
- `CONVERT_ERROR` - Message conversion error
- `ROUTING_ERROR` - Routing resolution error
- `VALIDATION_ERROR` - Message validation error

**Severity Levels:**
- `INFO` - Informational
- `WARNING` - Warning
- `ERROR` - Error
- `CRITICAL` - Critical

**Status Values:**
- `NEW` - New alert
- `ACKNOWLEDGED` - Alert acknowledged by operator
- `RESOLVED` - Alert resolved

---

### 7.2 Get Alerts by Status
```
GET /api/alerts/status/{status}
```

**Example:**
```
GET /api/alerts/status/NEW
```

**Response (200 OK):**
```json
[
  {
    "id": 101,
    "alertType": "CONNECTION_LOST",
    "severity": "CRITICAL",
    "message": "Lost connection to LOCAL_BROKER",
    "status": "NEW",
    "createdAt": "2024-01-15T10:00:00"
  }
]
```

---

### 7.3 Acknowledge Alert
```
PUT /api/alerts/{id}/ack
Headers: Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "id": 101,
  "alertType": "CONNECTION_LOST",
  "severity": "CRITICAL",
  "status": "ACKNOWLEDGED",
  "acknowledgedAt": "2024-01-15T10:05:00",
  "acknowledgedBy": "operator01"
}
```

---

### 7.4 Resolve Alert
```
PUT /api/alerts/{id}/resolve
```

**Response (200 OK):**
```json
{
  "id": 101,
  "status": "RESOLVED",
  "resolvedAt": "2024-01-15T10:15:00"
}
```

---

## 8. Accounts Management

### 8.1 List All Accounts
```
GET /api/accounts
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "accountName": "LOCAL_BROKER",
    "protocol": "AMQP",
    "host": "192.168.1.100",
    "port": 5672,
    "status": "ACTIVE",
    "bindStatus": "CONNECTED",
    "tlsEnabled": false,
    "saslMechanism": "PLAIN"
  },
  {
    "id": 2,
    "accountName": "AMHS_MTA",
    "protocol": "X400",
    "host": "192.168.1.200",
    "port": 102,
    "status": "ACTIVE",
    "bindStatus": "CONNECTED"
  }
]
```

**Protocol Types:**
- `AMQP` - AMQP/JMS broker connection
- `X400` - X.400 AMHS MTA connection

**Bind Status:**
- `CONNECTED` - Connected
- `DISCONNECTED` - Disconnected
- `CONNECTING` - Connecting

---

### 8.2 Get Account by ID
```
GET /api/accounts/{id}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "accountName": "LOCAL_BROKER",
  "protocol": "AMQP",
  "host": "192.168.1.100",
  "port": 5672,
  "configJson": "{\"username\":\"admin\"}",
  "status": "ACTIVE",
  "bindStatus": "CONNECTED",
  "tlsEnabled": false,
  "saslMechanism": "PLAIN"
}
```

---

### 8.3 Create Account
```
POST /api/accounts
```

**Request:**
```json
{
  "accountName": "REMOTE_BROKER",
  "protocol": "AMQP",
  "host": "192.168.1.150",
  "port": 5672,
  "configJson": "{\"username\":\"user\",\"password\":\"pass\"}",
  "tlsEnabled": true,
  "saslMechanism": "PLAIN"
}
```

**Response (201 Created):**
```json
{
  "id": 3,
  "accountName": "REMOTE_BROKER",
  "protocol": "AMQP",
  "host": "192.168.1.150",
  "port": 5672,
  "status": "ACTIVE",
  "bindStatus": "DISCONNECTED",
  "tlsEnabled": true
}
```

---

### 8.4 Update Account
```
PUT /api/accounts/{id}
```

**Request:**
```json
{
  "host": "192.168.1.151",
  "port": 5673,
  "tlsEnabled": true
}
```

**Response (200 OK):**
```json
{
  "id": 3,
  "host": "192.168.1.151",
  "port": 5673,
  "tlsEnabled": true,
  "updatedAt": "2024-01-15T14:00:00"
}
```

---

### 8.5 Delete Account
```
DELETE /api/accounts/{id}
```

**Response (204 No Content)**

---

### 8.6 Connect Account
```
POST /api/accounts/{id}/connect
```

**Response (200 OK):**
```json
{
  "result": "success",
  "message": "Account enabled"
}
```

---

### 8.7 Disconnect Account
```
POST /api/accounts/{id}/disconnect
```

**Response (200 OK):**
```json
{
  "result": "success",
  "message": "Account disabled"
}
```

---

### 8.8 Test Connection
```
POST /api/accounts/{id}/test-connection
```

**Response (200 OK):**
```json
{
  "result": "success",
  "latencyMs": 25,
  "message": "Connection successful"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Cannot connect to 192.168.1.100:5672 (Timeout)"
}
```

---

## 9. Traffic Logs

### 9.1 List Traffic Logs
```
GET /api/traffic-logs
```

**Query Parameters:**
- `from` (string) - ISO 8601 timestamp
- `to` (string) - ISO 8601 timestamp
- `direction` (string, default="ALL") - ALL, AMHS_TO_SWIM, SWIM_TO_AMHS
- `status` (string, default="ALL") - ALL, OK, ERROR, REJECT
- `page` (integer, default=0)
- `size` (integer, default=50)

**Example:**
```
GET /api/traffic-logs?direction=AMHS_TO_SWIM&status=OK&page=0&size=20
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1001,
      "referenceId": 5678,
      "date": "20240115",
      "type": "AMHS",
      "category": "OUT",
      "messageId": "<20240115100000.12345@amhs.vatm.vn>",
      "priority": "NORMAL",
      "origin": "VVHHZQZX",
      "subject": "METAR",
      "convertedTime": "2024-01-15T10:00:00",
      "status": "OK",
      "actionTaken": "convert-as-amqp"
    }
  ],
  "totalElements": 1000,
  "totalPages": 50,
  "page": 0
}
```

---

### 9.2 Get Traffic Log by ID
```
GET /api/traffic-logs/{id}
```

**Response (200 OK):**
```json
{
  "id": 1001,
  "referenceId": 5678,
  "messageId": "<20240115100000.12345@amhs.vatm.vn>",
  "content": "METAR VVNB 151000Z 27005KT 9999 FEW020 28/24 Q1012",
  "convertedTime": "2024-01-15T10:00:00",
  "status": "OK"
}
```

---

## 10. System Monitoring

### 10.1 Get System Statistics
```
GET /api/monitor/stats
```

**Response (200 OK):**
```json
{
  "server": {
    "uptime": 86400,
    "version": "2.0.0",
    "heapUsedMb": 512,
    "heapMaxMb": 2048
  },
  "trafficCumulative": {
    "inbound": 125000,
    "outbound": 118000
  },
  "database": {
    "gw_out": {
      "new": 15,
      "processing": 3,
      "error": 2
    },
    "gw_in": {
      "new": 8,
      "error": 1
    }
  },
  "connections": {
    "amqp": [
      {
        "id": 1,
        "name": "LOCAL_BROKER",
        "status": "CONNECTED"
      }
    ],
    "amhs": [
      {
        "id": 2,
        "name": "AMHS_MTA",
        "status": "CONNECTED"
      }
    ],
    "activeAmqp": 1
  }
}
```

---

## 11. System Metrics

### 11.1 Get System Metrics
```
GET /api/system/metrics
```

**Query Parameters:**
- `last` (integer, default=60) - Minutes of historical data

**Example:**
```
GET /api/system/metrics?last=30
```

**Response (200 OK):**
```json
{
  "current": {
    "cpuUsage": 45.5,
    "heapMemoryMb": 512,
    "activeThreads": 25,
    "msgInCount": 125000,
    "msgOutCount": 118000
  },
  "history": [
    {
      "timestamp": "2024-01-15T09:00:00Z",
      "cpuUsage": 42.3,
      "heapMemory": 480.5,
      "msgInCount": 124800,
      "msgOutCount": 117800
    }
  ]
}
```

---

## 12. System Logs

### 12.1 Get System Logs
```
GET /api/logs
```

**Query Parameters:**
- `level` (string, default="ALL") - ALL, INFO, WARN, ERROR
- `module` (string, default="ALL") - ALL, AMHS_COMPONENT, SWIM_COMPONENT, ITCU, CP
- `after` (string) - ISO 8601 timestamp
- `page` (integer, default=0)
- `size` (integer, default=100)

**Example:**
```
GET /api/logs?level=ERROR&module=SWIM_COMPONENT&page=0&size=50
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "uuid": "log-uuid-001",
      "timestamp": "2024-01-15T10:00:00",
      "level": "ERROR",
      "module": "SWIM_COMPONENT",
      "content": "Failed to publish message to topic ats/met/metar",
      "status": "UNREAD"
    }
  ],
  "latestTimestamp": "2024-01-15T10:00:00",
  "totalElements": 250
}
```

---

## 13. Archive Management

### 13.1 List Archived Messages
```
GET /api/archive
```

**Query Parameters:**
- `direction` (string, default="ALL") - ALL, AMHS_TO_SWIM, SWIM_TO_AMHS
- `searchType` (string, default="AMQP_ID") - AMQP_ID, MTS_ID, IPM_ID, MSG_ID
- `search` (string) - Search term
- `page` (integer, default=0)
- `size` (integer, default=50)

**Example:**
```
GET /api/archive?direction=AMHS_TO_SWIM&searchType=MSG_ID&search=20240115&page=0
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "uuid": "archive-uuid-001",
      "msgId": "msg-20240115-001",
      "amqpMessageId": "amqp-20240115-001",
      "recipients": "VVHHZTZX VVTSZDYX",
      "direction": "AMHS_TO_SWIM",
      "timestamp": "2024-01-15T10:00:00Z",
      "processingStatus": "SENT"
    }
  ],
  "totalElements": 5000,
  "totalPages": 250
}
```

---

### 13.2 Get Archive by UUID
```
GET /api/archive/{uuid}
```

**Response (200 OK):**
```json
{
  "uuid": "archive-uuid-001",
  "msgId": "msg-20240115-001",
  "amqpMessageId": "amqp-20240115-001",
  "rawContent": "METAR VVNB 151000Z 27005KT 9999 FEW020 28/24 Q1012",
  "processingStatus": "SENT"
}
```

---

## 14. Admin Operations

### 14.1 Delete Old Data
```
DELETE /api/admin/data/old
```

**Request:**
```json
{
  "retentionDays": 30
}
```

**Response (200 OK):**
```json
{
  "deletedCount": 0,
  "message": "Log cleanup completed"
}
```

---

### 14.2 Delete All Data
```
DELETE /api/admin/data/all
```

**Warning:** Destructive operation

**Response (200 OK):**
```json
{
  "deletedCount": 15000,
  "message": "All logging records cleared"
}
```

---

### 14.3 Run Maintenance
```
POST /api/admin/maintenance
```

**Response (200 OK):**
```json
{
  "result": "success",
  "message": "Database cleanup completed"
}
```

---

### 14.4 Convert Address
```
POST /api/admin/address/convert
```

**Request:**
```json
{
  "address": "METAR"
}
```

**Response (200 OK):**
```json
{
  "input": "METAR",
  "output": "ats/met/metar",
  "method": "DB_ROUTING_TABLE_LIVE"
}
```

---

### 14.5 System Diagnostic
```
POST /api/admin/diagnostic
```

**Response (200 OK):**
```json
{
  "dbConnection": "OK",
  "amqpConnections": [
    {
      "account": "LOCAL_BROKER",
      "status": "CONNECTED"
    }
  ],
  "diskSpace": {
    "freeGb": 125.5,
    "totalGb": 500.0
  }
}
```

---

## 15. Metadata

### 15.1 Get Message Types
```
GET /api/metadata/message-types
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "messageType": "METAR",
    "detectPattern": "METAR ",
    "active": true,
    "note": "Meteorological Aerodrome Report"
  },
  {
    "id": 2,
    "messageType": "FPL",
    "detectPattern": "(FPL-",
    "active": true,
    "note": "Flight Plan"
  }
]
```

---

### 15.2 Get User Roles
```
GET /api/metadata/roles
```

**Response (200 OK):**
```json
[
  {
    "code": "ADMIN",
    "name": "Quản trị viên"
  },
  {
    "code": "OPERATOR",
    "name": "Nhân viên vận hành"
  },
  {
    "code": "USER",
    "name": "Người dùng xem tin"
  }
]
```

---

## 16. Addressing Statistics

### 16.1 Get Source Distribution
```
GET /api/addressing/stats/distribution
```

**Query Parameters:**
- `period` (string, default="last_24h") - last_hour, last_24h, last_7d, last_30d

**Response (200 OK):**
```json
{
  "period": "last_7d",
  "totalMessages": 50000,
  "distribution": [
    {
      "source": "ROUTING_RULE",
      "count": 45000,
      "percentage": 90.0
    },
    {
      "source": "AMQP_PROPERTY",
      "count": 4500,
      "percentage": 9.0
    },
    {
      "source": "MANUAL_ROUTE",
      "count": 450,
      "percentage": 0.9
    },
    {
      "source": "UNRESOLVED",
      "count": 50,
      "percentage": 0.1
    }
  ]
}
```

---

### 16.2 Get Success Rate
```
GET /api/addressing/stats/success-rate
```

**Query Parameters:**
- `period` (string, default="last_7d") - last_7d, last_30d

**Response (200 OK):**
```json
{
  "period": "last_30d",
  "totalMessages": 200000,
  "resolved": 199500,
  "unrouted": 500,
  "successRate": 99.75,
  "unresolvedRate": 0.25
}
```

---

## 17. Entity Schemas

### Message Status Codes

**Gwin/Gwout Status (Integer):**
- `0` - PENDING
- `1` - PROCESSING
- `2` - SENT
- `3` - FAILED (Gwout only)
- `3` - DEAD (Gwin DEAD = 4)
- `4` - DEAD (Gwin)
- `5` - UNROUTED (Gwin only)

**Dispatch Status (String):**
- `PENDING` - Waiting to process
- `PROCESSING` - Currently processing
- `SENT` - Successfully sent
- `FAILED` - Failed, will retry
- `DEAD` - Failed permanently

### Addressing Sources

- `AMQP_PROPERTY` - From AMQP message headers
- `ROUTING_RULE` - From routing table lookup
- `MANUAL_ROUTE` - Manually routed by operator
- `UNRESOLVED` - No routing found

### Alert Types

- `CONNECTION_LOST` - Connection lost to broker/MTA
- `MESSAGE_DEAD` - Message permanently failed
- `QUEUE_BACKLOG` - Message queue backlog warning
- `CONVERT_ERROR` - Message conversion error
- `ROUTING_ERROR` - Routing resolution error
- `VALIDATION_ERROR` - Message validation error

### Alert Severity

- `INFO` - Informational
- `WARNING` - Warning
- `ERROR` - Error
- `CRITICAL` - Critical

---

## 18. Error Handling

### Standard Error Response

All error responses follow this format:

```json
{
  "error": "Error description message"
}
```

### HTTP Status Codes

- `200 OK` - Successful GET, PUT requests
- `201 Created` - Successful POST creating a resource
- `204 No Content` - Successful DELETE
- `400 Bad Request` - Validation error, missing fields
- `401 Unauthorized` - Authentication required/failed
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error

---

## Usage Examples

### Example 1: Create Routing Rule for METAR

```javascript
// JavaScript/TypeScript Example
const createMetarRouting = async () => {
  const response = await fetch('http://localhost:8180/api/routing', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify({
      direction: 'OUT',
      messageType: 'METAR',
      sendTopic: 'ats/met/metar',
      priority: 100,
      active: true,
      note: 'METAR routing to SWIM'
    })
  });

  const data = await response.json();
  console.log('Created routing rule:', data);
};
```

---

### Example 2: Monitor System Status

```javascript
// Poll system stats every 5 seconds
const monitorSystem = () => {
  setInterval(async () => {
    const response = await fetch('http://localhost:8180/api/monitor/stats');
    const stats = await response.json();

    console.log('Server uptime:', stats.server.uptime);
    console.log('Inbound messages:', stats.trafficCumulative.inbound);
    console.log('Active AMQP:', stats.connections.activeAmqp);
  }, 5000);
};
```

---

### Example 3: Get Unrouted Messages and Route Manually

```javascript
// Get unrouted messages
const response = await fetch('http://localhost:8180/api/addressing/unrouted?page=0&size=20');
const data = await response.json();

// Manually route first unrouted message
if (data.content.length > 0) {
  const msgid = data.content[0].msgid;

  await fetch(`http://localhost:8180/api/addressing/unrouted/${msgid}/route`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify({
      originator: 'VVHHZQZX',
      recipients: 'VVHHZTZX VVTSZDYX',
      note: 'Manually routed'
    })
  });
}
```

---

## Notes for Frontend Development

1. **Authentication**: All endpoints (except `/api/auth/login`) require JWT token in Authorization header
2. **CORS**: Backend configured to accept requests from `http://localhost:5173` and `http://localhost:3000`
3. **Pagination**: Most list endpoints support pagination with `page` and `size` parameters
4. **Filtering**: Use query parameters for filtering (status, time range, etc.)
5. **Error Handling**: Always check HTTP status code and parse error response
6. **Real-time Updates**: Consider using polling (5-10 seconds) for dashboard stats
7. **Date Format**: All timestamps use ISO 8601 format (`YYYY-MM-DDTHH:mm:ss`)
8. **Topic Format**: Topics use slash (`/`) separator, not dot (`.`) - backend auto-converts

---

**End of Documentation**
