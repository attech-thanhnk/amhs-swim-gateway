package vn.asg.swim;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Manual Simulation Runner for IntelliJ.
 * 
 * NOTE: This test is @Disabled so it won't break 'mvn install'.
 * To run: Open in IntelliJ and click the Green Play button next to the method.
 */
@SpringBootTest
@Import(InboundSimulationTool.class)
@Disabled("Manual execution only - requires a live AMQP broker")
public class ManualSimulationTest {

    @Autowired
    private InboundSimulationTool simulationTool;

    @Test
    public void runSimulation() throws Exception {
        System.out.println("=== WAITING FOR GATEWAY TO INITIALIZE ===");
        Thread.sleep(8000); // Wait for subscriber to connect and subscribe
        
        System.out.println("=== STARTING AMHS-SWIM GATEWAY SIMULATION ===");

        // Case 1: FPL with "dirty" route and split Field 18 metadata
        // This tests: Route cleaning + Field 18 reconstruction + DB Persistence
        String fplJson = """
            {
              "messageType": "FPL",
              "aircraftId": "HVN123",
              "departureIcao": "VVTS",
              "destinationIcao": "VVNB",
              "eobt": "1600",
              "aircraftType": "A321",
              "wakeTurbulence": "M",
              "route": "DCT PANTO DCT NOB DCT VVNB0100",
              "pbn": "A1B1C1D1",
              "registration": "VNA678",
              "dof": "240513"
            }
            """;
        System.out.println("Simulating FPL (Route Cleaning & F18 Reconstruction)...");
        simulationTool.simulateJson("ats/fpl/flightplan", fplJson, "FPL");

        // Case 2: METAR with weather array
        // This tests: METAR rendering + Mandatory '=' suffix
        String metarJson = """
            {
              "messageType": "METAR",
              "stationIcao": "VVTS",
              "observationTime": "131030Z",
              "wind": "24005KT",
              "visibility": "9999",
              "weather": ["-RA", "TS"],
              "cloud": "FEW020",
              "temperature": "32/25",
              "qnh": "Q1008"
            }
            """;
        System.out.println("Simulating METAR (Standard format with '=')...");
        simulationTool.simulateJson("ats/met/metar", metarJson, "METAR");

        // Case 3: NOTAM (AIS)
        String notamJson = """
            {
              "messageType": "NOTAMN",
              "notamId": "A0123/24",
              "fir": "VVHM",
              "notamCode": "QFAAH",
              "location": "VVNB",
              "validFrom": "2024-05-13T08:00:00Z",
              "validUntil": "2024-05-13T10:00:00Z",
              "text": "RWY 11R/29L CLSD DUE TO MAINT"
            }
            """;
        System.out.println("Simulating NOTAM (Multi-line structure)...");
        simulationTool.simulateJson("ats/notam", notamJson, "NOTAMN");

        System.out.println("=== SIMULATION TASKS SENT TO BROKER ===");
        System.out.println("Waiting 5 seconds for asynchronous processing...");
        Thread.sleep(5000); // Wait for subscriber to process and save to DB
        System.out.println("Simulation finished. Check 'gwin' database now.");
    }
}
