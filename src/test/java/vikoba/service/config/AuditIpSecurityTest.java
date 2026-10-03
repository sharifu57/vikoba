package vikoba.service.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vikoba.service.common.service.AuditLogService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuditIpSecurityTest {
    @Test void clientCannotForgeAuditAddressUsingForwardedHeader() {
        var audit = mock(AuditLogService.class);
        var request = new MockHttpServletRequest("GET", "/api/fines/group/7");
        request.setRemoteAddr("203.0.113.4");
        request.addHeader("X-Forwarded-For", "198.51.100.9");
        AuditContext.setCurrentUser("test-user");
        try {
            new AuditRequestInterceptor(audit).afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
            verify(audit).record(eq("test-user"), eq(7L), any(), eq("FINES"), isNull(),
                    eq("203.0.113.4"), anyString());
        } finally {
            AuditContext.clear();
        }
    }
}
