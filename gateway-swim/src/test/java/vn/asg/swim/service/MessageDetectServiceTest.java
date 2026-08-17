package vn.asg.swim.service;

import org.junit.jupiter.api.Test;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageDetectServiceTest {

    @Test
    void detectTacMessageAfterOperationalHeaders() {
        RoutingRepository repository = mock(RoutingRepository.class);
        when(repository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT")).thenReturn(List.of(
                rule("NOTAM_TEXT", "("),
                rule("ARR_TEXT", "(ARR-"),
                rule("FPL_TEXT", "(FPL-")));

        MessageDetectService service = new MessageDetectService(repository);
        service.reloadCache();

        String body = """
                ZCZC ABC001
                FF VVHHZTZX
                240430 VVVVZPZX
                FLW REC
                231557 VVDNZPZX
                SAVS31 VVPQ 240430
                PART 1
                == MESSAGE HEADER ==
                (ARR-HVN7622-VVPC0220-VVNB0406)
                """;

        assertEquals("ARR_TEXT", service.detect(body));
    }

    @Test
    void detectFplAfterFlwRecHeaderFromDatabase() {
        RoutingRepository repository = mock(RoutingRepository.class);
        when(repository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT")).thenReturn(List.of(
                rule("NOTAM_TEXT", "("),
                rule("FPL_TEXT", "(FPL-")));

        MessageDetectService service = new MessageDetectService(repository);
        service.reloadCache();

        String body = """
                FLW REC
                231557 VVDNZPZX
                (FPL-VJC8912-IS
                -A333/H-SDGHIJ3J5P2RWXYZ/B1D1L
                -VVDN0220
                -N0476F370 VIDEN2G VIDEN
                -UNKL0612 UNNT
                -PBN/A1B2C1D1L1O2S1S2 DOF/260624)
                """;

        assertEquals("FPL_TEXT", service.detect(body));
    }

    @Test
    void detectSigmetAfterWmoBulletinAndFirPrefix() {
        RoutingRepository repository = mock(RoutingRepository.class);
        when(repository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT")).thenReturn(List.of(
                rule("SIGMET_TEXT", "SIGMET ")));

        MessageDetectService service = new MessageDetectService(repository);
        service.reloadCache();

        String body = """
                WSVS31 VVGL 240435
                VVHM SIGMET 1 VALID 240435/240835 VVGL-
                VVHM HO CHI MINH FIR EMBD TS OBS WI N1030 E11400 TOP FL510
                """;

        assertEquals("SIGMET_TEXT", service.detect(body));
    }

    @Test
    void detectMetarAfterInlineAftnHeader() {
        RoutingRepository repository = mock(RoutingRepository.class);
        when(repository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT")).thenReturn(List.of(
                rule("METAR_TEXT", "METAR ")));

        MessageDetectService service = new MessageDetectService(repository);
        service.reloadCache();

        String body = "ZCZC ABC001FF VVHHZTZX131530 VVNBZTZXMETAR VVNB 131530Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";

        assertEquals("METAR_TEXT", service.detect(body));
    }

    @Test
    void detectJsonPatternAcrossWhitespaceAndQuotes() {
        RoutingRepository repository = mock(RoutingRepository.class);
        when(repository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT")).thenReturn(List.of(
                rule("ARR", "\"messageType\":\"ARR\"")));

        MessageDetectService service = new MessageDetectService(repository);
        service.reloadCache();

        String body = """
                {
                  "messageType" : "ARR",
                  "aircraftId" : "HVN7622"
                }
                """;

        assertEquals("ARR", service.detect(body));
    }

    private static Routing rule(String messageType, String detectPattern) {
        Routing rule = new Routing();
        rule.setDirection("OUT");
        rule.setMessageType(messageType);
        rule.setDetectPattern(detectPattern);
        rule.setActive(true);
        return rule;
    }
}
