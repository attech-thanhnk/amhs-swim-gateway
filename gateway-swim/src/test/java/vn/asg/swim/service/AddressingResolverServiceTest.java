package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.model.ResolvedAddressing;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressingResolverServiceTest {

    private AddressingResolverService service;

    @Mock
    private RoutingService routingService;
    @Mock
    private ConfigService configService;
    @Mock
    private MessageValidationService validationService;
    @Mock
    private MessageDetectService detectService;
    @Mock
    private Message jmsMessage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AddressingResolverService(routingService, configService, validationService, detectService);

        when(configService.get(anyString())).thenAnswer(invocation -> "MOCK_VALUE");

        MessageValidationService.ValidationResult validResult = new MessageValidationService.ValidationResult(true,
                null);
        when(validationService.validateAftnAddress(anyString(), anyString())).thenReturn(validResult);
        when(validationService.validateAftnRecipients(anyString())).thenReturn(validResult);
        when(detectService.detect(anyString())).thenReturn("METAR");
    }

    @Test
    void testResolve_Step1_AMQPProperties() throws JMSException {
        when(jmsMessage.getStringProperty("amhs_originator")).thenReturn("VVHHZPZX");
        when(jmsMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX VVTSZDYX");

        ResolvedAddressing result = service.resolve(jmsMessage, "swim.fixm.queue", "xml");

        assertEquals("VVHHZPZX", result.originator());
        assertTrue(result.recipients().contains("VVHHZTZX"));
        assertTrue(result.recipients().contains("VVTSZDYX"));
        assertEquals(ResolvedAddressing.SOURCE_AMQP_PROPERTY, result.source());
    }

    @Test
    void testResolve_Step2_RoutingRule() throws JMSException {
        Routing rule = new Routing();
        rule.setRecipients("VVHHZTZX");
        rule.setOriginator("VVHHZPZX");

        when(routingService.findBestMatchIn("swim.test.q", "METAR")).thenReturn(Optional.of(rule));

        ResolvedAddressing result = service.resolve(jmsMessage, "swim.test.q", "METAR content");

        assertEquals("VVHHZPZX", result.originator());
        assertTrue(result.recipients().contains("VVHHZTZX"));
        assertEquals(ResolvedAddressing.SOURCE_ROUTING_RULE, result.source());
    }
}
