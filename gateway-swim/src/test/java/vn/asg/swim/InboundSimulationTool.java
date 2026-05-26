package vn.asg.swim;

import jakarta.jms.*;
import org.springframework.boot.test.context.TestComponent;
import vn.asg.swim.service.ConnectionManagerService;

import java.util.UUID;

/**
 * A professional simulation tool to send ICAO-compliant SWIM messages to the Gateway via AMQP.
 * Located in src/test/java to ensure isolation from production code.
 */
@TestComponent
public class InboundSimulationTool {

    private final ConnectionManagerService connectionManager;

    public InboundSimulationTool(ConnectionManagerService connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void simulateJson(String queue, String json, String subject) throws Exception {
        System.out.println(">>> SIMULATING INBOUND JSON [" + subject + "] -> " + queue + " <<<");
        Session session = connectionManager.createSession();
        try {
            MessageProducer producer = connectionManager.createProducer(session, queue);
            TextMessage message = session.createTextMessage(json);

            // Default simulation headers
            message.setStringProperty("amhs_originator", "VVTSZTZX");
            message.setStringProperty("amhs_subject", subject);
            message.setStringProperty("ats_priority", "GG");
            message.setStringProperty("JMS_AMQP_CONTENT_TYPE", "application/json");
            message.setJMSMessageID("SIM-" + UUID.randomUUID().toString());

            producer.send(message);
            System.out.println(">>> SUCCESS: Sent simulated JSON payload for " + subject);
            producer.close();
        } finally {
            session.close();
        }
    }
}
