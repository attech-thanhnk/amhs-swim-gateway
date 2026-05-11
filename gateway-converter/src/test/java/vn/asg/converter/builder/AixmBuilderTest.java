package vn.asg.converter.builder;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import vn.asg.converter.model.NotamMessage;

import static org.junit.jupiter.api.Assertions.*;

public class AixmBuilderTest {

    @Test
    public void testBuildNotam() {
        NotamMessage msg = new NotamMessage();
        msg.setNotamId("A0123/24");
        msg.setType("NOTAMN");
        msg.setValidFrom(new DateTime(2024, 1, 1, 0, 0));
        msg.setValidUntil(new DateTime(2024, 1, 31, 23, 59));
        msg.setPurpose("NBO");
        msg.setText("RWY 11R/29L CLSD DUE TO MAINT.");

        AixmBuilder builder = new AixmBuilder();
        String xml = builder.buildNotam(msg);

        assertNotNull(xml);
        assertTrue(xml.contains("aixm:AIXMBasicMessage"));
        assertTrue(xml.contains("aixm:Event"));
        assertTrue(xml.contains("RWY 11R/29L CLSD DUE TO MAINT."));
        assertTrue(xml.contains("http://www.aixm.aero/schema/5.1.1"));
    }
}
