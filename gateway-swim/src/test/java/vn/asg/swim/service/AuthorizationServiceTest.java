package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationServiceTest {

    private AuthorizationService authService;

    @Mock
    private ConfigService configService;

    @Mock
    private Message jmsMessage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthorizationService(configService);
    }

    @Test
    void testIsAmhsUserAuthorized_ModeAll() {
        when(configService.get(ConfigService.KEY_AUTHORIZED_AMHS_USERS)).thenReturn("ALL");
        assertTrue(authService.isAmhsUserAuthorized("ANYBODY"));
    }

    @Test
    void testIsAmhsUserAuthorized_ModeByList_Success() {
        when(configService.get(ConfigService.KEY_AUTHORIZED_AMHS_USERS)).thenReturn("BY_LIST");
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("VVHHZPZX,VVTSZQZX");

        assertTrue(authService.isAmhsUserAuthorized("VVHHZPZX"));
    }

    @Test
    void testIsAmhsUserAuthorized_ModeByList_Failure() {
        when(configService.get(ConfigService.KEY_AUTHORIZED_AMHS_USERS)).thenReturn("BY_LIST");
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("VVHHZPZX");

        assertFalse(authService.isAmhsUserAuthorized("ZBBBZQZX"));
    }

    @Test
    void testIsAmhsUserAuthorized_ModeByPrmd_Success() {
        when(configService.get(ConfigService.KEY_AUTHORIZED_AMHS_USERS)).thenReturn("BY_PRMD");
        when(configService.get("AUTHORIZED_AMHS_PRMDS")).thenReturn("VVHH,VVTS");

        assertTrue(authService.isAmhsUserAuthorized("VVHHZPZX"));
    }

    @Test
    void testIsSwimUserAuthorized_ModeAll() {
        when(configService.get(ConfigService.KEY_AUTHORIZED_SWIM_USERS)).thenReturn("ALL");
        assertTrue(authService.isSwimUserAuthorized(jmsMessage));
    }

    @Test
    void testIsSwimUserAuthorized_ModeByList_Success() throws JMSException {
        when(configService.get(ConfigService.KEY_AUTHORIZED_SWIM_USERS)).thenReturn("BY_LIST");
        when(configService.get("AUTHORIZED_SWIM_USERS")).thenReturn("operator1,operator2");
        when(jmsMessage.getStringProperty("user_id")).thenReturn("operator1");

        assertTrue(authService.isSwimUserAuthorized(jmsMessage));
    }

    @Test
    void testIsSwimUserAuthorized_ModeByEnterprise_Success() throws JMSException {
        when(configService.get(ConfigService.KEY_AUTHORIZED_SWIM_USERS)).thenReturn("BY_ENTERPRISE");
        when(configService.get("AUTHORIZED_SWIM_ENTERPRISES")).thenReturn("VNA,VJC");
        when(jmsMessage.getStringProperty("swim_enterprise")).thenReturn("VNA");

        assertTrue(authService.isSwimUserAuthorized(jmsMessage));
    }
}
