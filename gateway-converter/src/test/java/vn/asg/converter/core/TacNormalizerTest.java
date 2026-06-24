package vn.asg.converter.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TacNormalizerTest {

    @Test
    public void testCleanControlCharacters() {
        String inputWithBel = "METAR VVNB\u0007 291200Z=";
        String expected = "METAR VVNB 291200Z=";
        assertEquals(expected, TacNormalizer.normalize(inputWithBel));
    }

    @Test
    public void testCleanMultipleSpacesAndLines() {
        String input = "METAR   VVNB\n\n\n291200Z=";
        String expected = "METAR VVNB\n\n291200Z=";
        assertEquals(expected, TacNormalizer.normalize(input));
    }

    @Test
    public void testCleanEmptyString() {
        assertEquals("", TacNormalizer.normalize("   "));
        assertEquals("", TacNormalizer.normalize(null));
    }
}
