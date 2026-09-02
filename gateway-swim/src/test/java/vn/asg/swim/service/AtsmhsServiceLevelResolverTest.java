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

    // ==================== CHẾ ĐỘ CHỈ ĐẾN TỪ CẤU HÌNH ====================

    @Test
    void testResolve_ModeAlwaysComesFromConfig_NeverFromMessage() {
        // §3.3.3 (C-08…C-12): mức dịch vụ là cấu hình của ITCU, không phải thuộc tính bản tin.
        // Table 2 (§4.5.2) không định nghĩa property nào cho việc này, nên resolver chỉ được có
        // MỘT nguồn duy nhất: gateway_config. Chữ ký một tham số mode đã bị bỏ - test này giữ
        // cho nó không quay lại.
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("BASIC");

        // Dù content-type khai binary (thứ duy nhất bên gửi điều khiển được), cấu hình BASIC vẫn
        // thắng -> bản tin sẽ bị validateContent từ chối ở bước sau (CTSW103 bản tin 2).
        assertEquals(AtsmhsServiceLevelResolver.BASIC,
                resolver.resolve("application/octet-stream", "VVHHZTZX"));
        assertFalse(resolver.validateContent(
                resolver.resolve("application/octet-stream", "VVHHZTZX"),
                "application/octet-stream", true));

        verify(configService, atLeastOnce()).get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL);
    }

    @Test
    void testResolve_BlankConfig_ShouldFallBackToContentBased() {
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("   ");

        assertEquals(AtsmhsServiceLevelResolver.EXTENDED,
                resolver.resolve("application/octet-stream", "VVHHZTZX"));
        assertEquals(AtsmhsServiceLevelResolver.BASIC,
                resolver.resolve("text/plain", "VVHHZTZX"));
    }
}
