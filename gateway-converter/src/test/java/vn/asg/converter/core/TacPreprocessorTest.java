package vn.asg.converter.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TacPreprocessorTest {

    private final TacPreprocessor preprocessor = new TacPreprocessor();

    @Test
    public void testUnwrapAftnEnvelope() {
        String raw = "ZCZC 123\n" +
                     "GG VVNBYOYX\n" +
                     "291200 VVTSYNYX\n" +
                     "METAR VVNB 291200Z 09008KT 9999 FEW020 28/24 Q1010=\n" +
                     "NNNN";
                     
        TacPreprocessor.AftnEnvelope envelope = preprocessor.unwrap(raw);
        
        assertTrue(envelope.hasAftnWrapper);
        assertEquals("METAR VVNB 291200Z 09008KT 9999 FEW020 28/24 Q1010=", envelope.body);
    }

    @Test
    public void testUnwrapRawBodyWithoutAftn() {
        String raw = "METAR VVNB 291200Z 09008KT 9999 FEW020 28/24 Q1010=";
        TacPreprocessor.AftnEnvelope envelope = preprocessor.unwrap(raw);
        
        assertFalse(envelope.hasAftnWrapper);
        assertEquals(raw, envelope.body);
    }
}
