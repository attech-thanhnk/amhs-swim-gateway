package vn.asg.converter.parser.flight;

import org.junit.jupiter.api.Test;
import vn.asg.converter.model.flight.FplMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FplParserTest {

    private final FplParser parser = new FplParser();

    @Test
    void parseDepAcceptsSsrSuffixOnAlphanumericCallsign() throws Exception {
        FplMessage message = parser.parse("(DEP-HVN1825/A5770-VVTS0403-VVPQ-DOF/260624)");

        assertEquals("DEP", message.getMessageType());
        assertEquals("HVN1825/A5770", message.getAircraftId());
        assertEquals("VVTS", message.getDepartureIcao());
        assertEquals("0403", message.getActualDepartureTime());
        assertEquals("VVPQ", message.getDestinationIcao());
        assertEquals("260624", message.getDof());
        assertEquals("(DEP-HVN1825/A5770-VVTS0403-VVPQ-DOF/260624)", message.toString());
    }

    @Test
    void parseDepAcceptsNumericCallsignWithSsrSuffix() throws Exception {
        FplMessage message = parser.parse("(DEP-8602/A2241-VVNB0649-VVTS-DOF/260624)");

        assertEquals("8602/A2241", message.getAircraftId());
        assertEquals("VVNB", message.getDepartureIcao());
        assertEquals("0649", message.getActualDepartureTime());
        assertEquals("VVTS", message.getDestinationIcao());
    }

    @Test
    void parseArrReadsDepartureAndArrivalTimes() throws Exception {
        FplMessage message = parser.parse("(ARR-HVN7622-VVPC0220-VVNB0406)");

        assertEquals("ARR", message.getMessageType());
        assertEquals("HVN7622", message.getAircraftId());
        assertEquals("VVPC", message.getDepartureIcao());
        assertEquals("0220", message.getActualDepartureTime());
        assertEquals("VVNB", message.getDestinationIcao());
        assertEquals("0406", message.getActualArrivalTime());
        assertEquals("(ARR-HVN7622-VVPC0220-VVNB0406)", message.toString());
    }

    @Test
    void parseArrStillAcceptsArrivalOnlyFormat() throws Exception {
        FplMessage message = parser.parse("(ARR-HVN7622-VVPC-VVNB0406)");

        assertEquals("HVN7622", message.getAircraftId());
        assertEquals("VVPC", message.getDepartureIcao());
        assertNull(message.getActualDepartureTime());
        assertEquals("VVNB", message.getDestinationIcao());
        assertEquals("0406", message.getActualArrivalTime());
        assertEquals("(ARR-HVN7622-VVPC-VVNB0406)", message.toString());
    }

    @Test
    void parseArrAcceptsOtherInfoAfterArrivalTime() throws Exception {
        FplMessage message = parser.parse("(ARR-SJX761-RCTP2255-WIII0408-DOF/260623)");

        assertEquals("SJX761", message.getAircraftId());
        assertEquals("RCTP", message.getDepartureIcao());
        assertEquals("2255", message.getActualDepartureTime());
        assertEquals("WIII", message.getDestinationIcao());
        assertEquals("0408", message.getActualArrivalTime());
        assertEquals("DOF/260623", message.getOtherInfo());
        assertEquals("260623", message.getDof());
        assertEquals("(ARR-SJX761-RCTP2255-WIII0408-DOF/260623)", message.toString());
    }

    @Test
    void parseCnlAcceptsSsrSuffix() throws Exception {
        FplMessage message = parser.parse("(CNL-HVN593/A4636-VHHH0630-VVNB-DOF/260624)");

        assertEquals("CNL", message.getMessageType());
        assertEquals("HVN593/A4636", message.getAircraftId());
        assertEquals("VHHH", message.getDepartureIcao());
        assertEquals("0630", message.getEobt());
        assertEquals("VVNB", message.getDestinationIcao());
        assertEquals("DOF/260624", message.getOtherInfo());
        assertEquals("260624", message.getDof());
        assertEquals("(CNL-HVN593/A4636-VHHH0630-VVNB-DOF/260624)", message.toString());
    }

    @Test
    void parseDlaAcceptsSsrSuffix() throws Exception {
        FplMessage message = parser.parse("(DLA-VJC433/A6010-VVNB0755-VVPC-DOF/260624)");

        assertEquals("DLA", message.getMessageType());
        assertEquals("VJC433/A6010", message.getAircraftId());
        assertEquals("VVNB", message.getDepartureIcao());
        assertEquals("0755", message.getEobt());
        assertEquals("VVPC", message.getDestinationIcao());
        assertEquals("DOF/260624", message.getOtherInfo());
        assertEquals("260624", message.getDof());
        assertEquals("(DLA-VJC433/A6010-VVNB0755-VVPC-DOF/260624)", message.toString());
    }

    @Test
    void parseDlaAcceptsNumericCallsignFromDatabase() throws Exception {
        FplMessage message = parser.parse("(DLA-8903-ZZZZ0545-VVGL-DOF/260624)");

        assertEquals("DLA", message.getMessageType());
        assertEquals("8903", message.getAircraftId());
        assertEquals("ZZZZ", message.getDepartureIcao());
        assertEquals("0545", message.getEobt());
        assertEquals("VVGL", message.getDestinationIcao());
        assertEquals("260624", message.getDof());
    }
}
