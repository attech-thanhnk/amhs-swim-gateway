package vn.asg.swim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.service.ConnectionManagerService;

import java.time.LocalDateTime;

/**
 * Manual integration test for AMHS -> SWIM (Outbound) flow.
 * It inserts a TAC message into 'gwout' and waits for the scheduler to process it.
 */
@SpringBootTest
@ActiveProfiles("default")
public class ManualOutboundSimulationTest {

    @Autowired
    private GwoutRepository gwoutRepository;

    @Autowired
    private ConnectionManagerService connectionManager;

    @Test
    public void runOutboundSimulation() throws InterruptedException {
        System.out.println("=== STARTING AMHS -> SWIM OUTBOUND SIMULATION ===");

        // 1. Wait for connection
        int waitCount = 0;
        while (!connectionManager.getConnected().get() && waitCount < 10) {
            System.out.println("Waiting for AMQP connection...");
            Thread.sleep(1000);
            waitCount++;
        }

        if (!connectionManager.getConnected().get()) {
            System.err.println("FAILED: Could not connect to AMQP broker.");
            return;
        }

        // 2. Prepare a FULL AFTN FPL message (Standard format)
        String fullAftnFpl = """
                ZCZC ABC123
                GG VVNBZTZX VVHHZPZX
                141200 VVTSZPZX
                (FPL-HVN456-IS-A321/M-S/C-VVNB1200-N0450F330 DCT NOB DCT PANTO-VVTS0000-PBN/A1B1C1D1 REG/VNA888 DOF/240514)
                NNNN
                """;
        
        Gwout gwout = new Gwout();
        gwout.setMsgid(null); // Auto-increment
        gwout.setOrigin("VVTSZPZX");
        gwout.setAddress("VVNBZTZX VVHHZPZX"); 
        gwout.setPriority(3); // GG
        gwout.setText(fullAftnFpl);
        gwout.setStatus(Gwout.STATUS_PENDING);
        gwout.setTime(LocalDateTime.now());
        gwout.setBodyType("text");
        gwout.setContentType("application/json");

        System.out.println("Inserting FULL AFTN FPL into gwout table...");
        gwout = gwoutRepository.save(gwout);
        System.out.println(">>> SUCCESS: Inserted gwout#" + gwout.getMsgid());

        // 3. Prepare a FULL AFTN METAR message
        String fullAftnMetar = """
                ZCZC XYZ456
                GG VVTSZTZX
                140230 VVNBZPZX
                METAR VVNB 140230Z 12004KT 9999 FEW025 28/22 Q1010=
                NNNN
                """;
        Gwout metarOut = new Gwout();
        metarOut.setOrigin("VVNBZPZX");
        metarOut.setAddress("VVTSZTZX");
        metarOut.setPriority(3);
        metarOut.setText(fullAftnMetar);
        metarOut.setStatus(Gwout.STATUS_PENDING);
        metarOut.setTime(LocalDateTime.now());
        metarOut.setBodyType("text");
        metarOut.setContentType("application/json");

        System.out.println("Inserting TAC METAR into gwout table...");
        metarOut = gwoutRepository.save(metarOut);
        System.out.println(">>> SUCCESS: Inserted gwout#" + metarOut.getMsgid());

        // 4. Wait for scheduler (polling interval is usually 5-10s)
        System.out.println("Waiting 15 seconds for Poller and Dispatcher to process...");
        Thread.sleep(15000);

        System.out.println("=== OUTBOUND SIMULATION FINISHED ===");
        System.out.println("Please check logs for 'dispatch#... SENT' and 'Conversion SUCCESS'");
    }
}
