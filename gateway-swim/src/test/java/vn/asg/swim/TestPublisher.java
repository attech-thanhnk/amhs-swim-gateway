package vn.asg.swim;

import jakarta.jms.*;
import org.apache.qpid.jms.JmsConnectionFactory;

/**
 * Test publisher — Gửi message thử lên Solace PubSub+ để test
 * AMQPSubscriberService.
 * Chạy file này như một standalone Java main, KHÔNG phải Spring Boot.
 *
 * Cách chạy trong IntelliJ:
 * Right-click → Run 'TestPublisher.main()'
 *
 * Solace AMQP 1.0:
 * - Port: 5672 (AMQP), 5671 (AMQPS/TLS)
 * - Username format: <user>@<vpn-name> Ví dụ: admin@default
 * - Destination phải là Solace Queue (đã tạo sẵn trên broker)
 */
public class TestPublisher {

    // ─── Cấu hình Solace — SỬA THEO MÔI TRƯỜNG ──────────────────────────────
    private static final String BROKER_URL = "amqp://localhost:5672"; // Solace AMQP port
    private static final String USERNAME = "admin@default"; // user@vpn
    private static final String PASSWORD = "admin";

    // Queue đã subscribe trong gateway (xem log startup: "Subscribed to 2 queues:
    // [nm/b2b/flights, vnhh/test]")
    private static final String QUEUE = "vnhh/test";
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        JmsConnectionFactory factory = new JmsConnectionFactory(BROKER_URL);
        try (Connection connection = factory.createConnection(USERNAME, PASSWORD);
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            connection.start();
            Destination dest = session.createTopic(QUEUE);
            MessageProducer producer = session.createProducer(dest);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // ─── Test 1: IWXXM METAR (Strategy 2: XML_CONTENT) ───────────────
            System.out.println("\n=== Test 1: IWXXM METAR VVHH → expect XML_CONTENT:VVHH ===");
            TextMessage msg1 = session.createTextMessage(IWXXM_METAR_VVHH);
            msg1.setStringProperty("JMS_AMQP_CONTENT_TYPE", "application/xml");
            // KHÔNG set amhs_recipients → resolver phải tự tìm
            producer.send(msg1);
            System.out.println("Sent → " + msg1.getJMSMessageID());

            Thread.sleep(500);

            // ─── Test 2: FIXM FPL (Strategy 2: FIR_BASED) ────────────────────
            System.out.println("\n=== Test 2: FIXM FPL ADEP=VVHH → expect FIR_BASED:VVHF ===");
            TextMessage msg2 = session.createTextMessage(FIXM_FPL_VVHH);
            msg2.setStringProperty("JMS_AMQP_CONTENT_TYPE", "application/xml");
            producer.send(msg2);
            System.out.println("Sent → " + msg2.getJMSMessageID());

            Thread.sleep(500);

            // ─── Test 3: AMHS-aware producer (Strategy 1: AMQP_PROPERTY) ─────
            System.out.println("\n=== Test 3: AMHS-aware → expect AMQP_PROPERTY ===");
            TextMessage msg3 = session.createTextMessage("Plain text ATS message");
            msg3.setStringProperty("amhs_recipients", "VVHHZTZX VVTSZDYX");
            msg3.setStringProperty("amhs_originator", "VVHHZPZX");
            msg3.setStringProperty("amhs_subject", "METAR");
            producer.send(msg3);
            System.out.println("Sent → " + msg3.getJMSMessageID());

            Thread.sleep(500);

            // ─── Test 4: Unknown message (Strategy 5: UNRESOLVED) ────────────
            System.out.println("\n=== Test 4: Unknown text → expect UNRESOLVED + STATUS=5 ===");
            TextMessage msg4 = session.createTextMessage("Unknown content, no XML, no properties");
            producer.send(msg4);
            System.out.println("Sent → " + msg4.getJMSMessageID());

            System.out.println("""

                    ✅ Done. Verify bằng SQL:
                    SELECT msgid, addressing_source, origin, address, status
                    FROM gwin ORDER BY msgid DESC LIMIT 10;
                    """);
        }
    }

    // ─── XML payloads ─────────────────────────────────────────────────────────

    private static final String IWXXM_METAR_VVHH = """
            <?xml version="1.0" encoding="UTF-8"?>
            <iwxxm:METAR xmlns:iwxxm="http://icao.int/iwxxm/3.0"
                         xmlns:aixm="http://www.aixm.aero/schema/5.1.1"
                         xmlns:gml="http://www.opengis.net/gml/3.2">
                <iwxxm:aerodrome>
                    <aixm:AirportHeliport gml:id="ah-vvhh">
                        <aixm:timeSlice>
                            <aixm:AirportHeliportTimeSlice gml:id="ts-vvhh">
                                <gml:validTime/>
                                <aixm:interpretation>SNAPSHOT</aixm:interpretation>
                                <aixm:locationIndicatorICAO>VVHH</aixm:locationIndicatorICAO>
                            </aixm:AirportHeliportTimeSlice>
                        </aixm:timeSlice>
                    </aixm:AirportHeliport>
                </iwxxm:aerodrome>
            </iwxxm:METAR>
            """;

    private static final String FIXM_FPL_VVHH = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fx:FlightPlan xmlns:fx="http://www.fixm.aero/flight/4.3"
                           xmlns:fb="http://www.fixm.aero/base/4.3">
                <fx:departure>
                    <fx:departureAerodrome>
                        <fb:locationIndicator>VVHH</fb:locationIndicator>
                    </fx:departureAerodrome>
                </fx:departure>
                <fx:arrival>
                    <fx:destinationAerodrome>
                        <fb:locationIndicator>VTBS</fb:locationIndicator>
                    </fx:destinationAerodrome>
                </fx:arrival>
            </fx:FlightPlan>
            """;
}
