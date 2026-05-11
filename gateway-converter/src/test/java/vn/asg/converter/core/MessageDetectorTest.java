package vn.asg.converter.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MessageDetectorTest {

    @Test
    public void testDetectMetarSingle() {
        String msg = "METAR VVNB 291200Z 09008KT 9999 FEW020 28/24 Q1010=";
        MessageDetector.DetectionResult result = MessageDetector.detect(msg);
        assertEquals(MessageDetector.Category.METAR, result.category());
        assertEquals(MessageDetector.Form.SINGLE, result.form());
    }

    @Test
    public void testDetectTafBulletin() {
        String msg = "FTVN31 VVGL 291100\nTAF VVNB 291100Z 2912/3018 10008KT 9999=";
        MessageDetector.DetectionResult result = MessageDetector.detect(msg);
        assertEquals(MessageDetector.Category.TAF, result.category());
        assertEquals(MessageDetector.Form.BULLETIN, result.form());
    }

    @Test
    public void testDetectNotam() {
        String msg = "(A0123/24 NOTAMN\nQ) VVVV/QXXXX/IV/NBO/A/000/999/2101N10548E005\nA) VVNB B) 2401010000 C) PERM\nE) TEST NOTAM)";
        MessageDetector.DetectionResult result = MessageDetector.detect(msg);
        assertEquals(MessageDetector.Category.NOTAM, result.category());
    }

    @Test
    public void testDetectFpl() {
        String msg = "(FPL-HVN123-IS\n-A321/M-SDFG/C\n-VVNB0100\n-N0450F330 DCT\n-VVTS0200\n-0)";
        MessageDetector.DetectionResult result = MessageDetector.detect(msg);
        assertEquals(MessageDetector.Category.FPL, result.category());
    }

    @Test
    public void testDetectUnknown() {
        String msg = "HELLO WORLD THIS IS A TEST";
        MessageDetector.DetectionResult result = MessageDetector.detect(msg);
        assertEquals(MessageDetector.Category.UNKNOWN, result.category());
    }
}
