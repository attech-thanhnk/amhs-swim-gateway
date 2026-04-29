package vn.asg.swim.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AtsmhsServiceLevelResolverTest {

    private AtsmhsServiceLevelResolver resolver;

    @Mock
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resolver = new AtsmhsServiceLevelResolver(configService);
    }

    @Test
    void testResolve_FixedExtended() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("EXTENDED");

        String result = resolver.resolve("application/xml", "VVHHZTZX");
        assertEquals(AtsmhsServiceLevelResolver.EXTENDED, result);
    }

    @Test
    void testResolve_FixedBasic() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("BASIC");

        String result = resolver.resolve("application/octet-stream", "VVHHZTZX");
        assertEquals(AtsmhsServiceLevelResolver.BASIC, result);
    }

    @Test
    void testResolve_ContentBased_Binary() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("CONTENT_BASED");

        String result = resolver.resolve("application/octet-stream", "VVHHZTZX");
        assertEquals(AtsmhsServiceLevelResolver.EXTENDED, result);
    }

    @Test
    void testResolve_ContentBased_Text() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("CONTENT_BASED");

        String result = resolver.resolve("text/plain", "VVHHZTZX");
        assertEquals(AtsmhsServiceLevelResolver.BASIC, result);
    }

    @Test
    void testResolve_RecipientsBased_AllExtended() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("RECIPIENTS_BASED");
        when(configService.get("ATSMHS_EXTENDED_CAPABLE_ADDRESSES")).thenReturn("VVHHZTZX VVTSZDYX");

        String result = resolver.resolve("text/plain", "VVHHZTZX VVTSZDYX");
        assertEquals(AtsmhsServiceLevelResolver.EXTENDED, result);
    }

    @Test
    void testResolve_RecipientsBased_Mixed() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("RECIPIENTS_BASED");
        when(configService.get("ATSMHS_EXTENDED_CAPABLE_ADDRESSES")).thenReturn("VVHHZTZX");

        String result = resolver.resolve("text/plain", "VVHHZTZX VVXXXXXX");
        assertEquals(AtsmhsServiceLevelResolver.BASIC, result);
    }

    @Test
    void testValidateContent_BasicWithBinary() {
        boolean isValid = resolver.validateContent(AtsmhsServiceLevelResolver.BASIC, "application/octet-stream", true);
        assertFalse(isValid, "BASIC service level should NOT allow binary content (C-10)");
    }

    @Test
    void testValidateContent_ExtendedWithBinary() {
        boolean isValid = resolver.validateContent(AtsmhsServiceLevelResolver.EXTENDED, "application/octet-stream",
                true);
        assertTrue(isValid, "EXTENDED service level SHOULD allow binary content");
    }
}
