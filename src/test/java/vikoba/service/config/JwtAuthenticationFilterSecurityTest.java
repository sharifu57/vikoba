package vikoba.service.config;

import java.util.List;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterSecurityTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void disabledAccountIsNotAuthenticatedByAnExistingToken() throws Exception {
        assertAccountRejected(false, true);
    }

    @Test void lockedAccountIsNotAuthenticatedByAnExistingToken() throws Exception {
        assertAccountRejected(true, false);
    }

    private void assertAccountRejected(boolean enabled, boolean unlocked) throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService users = mock(UserDetailsService.class);
        when(jwt.extractUsername("token")).thenReturn("255700000001");
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(users.loadUserByUsername("255700000001")).thenReturn(
                new User("255700000001", "hash", enabled, true, true, unlocked, List.of()));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        new JwtAuthenticationFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(AuditContext.getCurrentUser());
    }

    @Test void deletedAccountReturns401InsteadOfServerError() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService users = mock(UserDetailsService.class);
        when(jwt.extractUsername("token")).thenReturn("deleted");
        when(users.loadUserByUsername("deleted")).thenThrow(new UsernameNotFoundException("missing"));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new JwtAuthenticationFilter(jwt, users).doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }
}
