USE asg_db;

-- 1. METAR (Meteorological Aerodrome Report)
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-METAR-001', 2, NOW(), '131530', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ABC001
FF VVHHZTZX
131530 VVNBZTZX
METAR VVNB 131530Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=');

-- 2. FPL (Flight Plan)
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-FPL-001', 1, NOW(), '131540', 'VVTSZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC FPL002
SS VVHHZTZX
131540 VVTSZTZX
(FPL-HVN123-IS
-A321/M-SDFGIRW/S
-VVTS1600
-N0450F350 DCT PANTO DCT NOB DCT VVNB0100
-VVNB0100 VVCI
-PBN/A1 B1 C1 D1 L1 O1 S1 REG/VN678 SEL/ABCD)');

-- 3. NOTAM (Notice to Airmen)
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-NOTAM-001', 2, NOW(), '131550', 'VNBBYOYX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC XYZ001
GG VVHHZTZX
131550 VNBBYOYX
(A1234/24 NOTAMN
Q) VVXX/QOBCE/IV/NBO/A/000/999/2101N10548E005
A) VVNB B) 2405131550 C) 2405141550
E) OBSTACLE CRANE ERECTED AT 2101N10548E HEIGHT 45M)');

-- 4. ALR (Alerting Message) - Khẩn nguy
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-ALR-001', 0, NOW(), '131600', 'VVDNZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC ALR001
SS VVHHZTZX
131600 VVDNZTZX
(ALR-INCERFA/VVDNZTZX/OVERDUE
-HVN456-IM
-B738/M-S/C
-VVDN1530
-N0430F330 B9 VVNB
-VVNB0030
-REG/VN999 OPR/HVN RMK/RADIO FAILURE)');

-- 5. CHG (Modification Message)
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-CHG-001', 2, NOW(), '131610', 'VVTSZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC CHG001
GG VVHHZTZX
131610 VVTSZTZX
(CHG-HVN123-VVTS1600-VVNB-18/PBN/B2)');

-- 6. SNOWTAM (Snow Report)
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-SNOW-001', 2, NOW(), '131620', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC SNO001
GG VVHHZTZX
131620 VVNBZTZX
(SNOWTAM 0001
A) VVNB B) 05131620
C) 11L D) 1/1/1 E) 10/10/10 F) 34/34/34 G) 05/05/05 H) 5/5/5)');

-- 7. SYNOP (Surface Observation)
INSERT INTO gwout (amhsid, priority, time, filing_time, origin, address, body_type, content_type, status, text) 
VALUES ('TEST-SYNOP-001', 3, NOW(), '131630', 'VVNBZTZX', 'VVHHZTZX', 'text', 'application/json', 0,
'ZCZC SYN001
GG VVHHZTZX
131630 VVNBZTZX
AAXX 13124 48820 11497 83605 10312 20254 30056 40102 52002 60001 70282 885// 333 88560=');
