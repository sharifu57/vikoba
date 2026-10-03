package vikoba.service.auth.service;

import org.junit.jupiter.api.Test;
import vikoba.service.auth.controller.AuthController;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OtpCooldownHttpTest {
    @Test void resendReturnsRemainingSecondsInHeaderAndJson() throws Exception {
        AuthService service = mock(AuthService.class);
        when(service.resendOtp(any())).thenThrow(new AuthService.OtpCooldownException(37));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(service)).build();
        mvc.perform(post("/api/auth/resend-otp").contentType("application/json")
                .content("{\"phone\":\"255700000001\",\"purpose\":\"login\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(37))
                .andExpect(jsonPath("$.message").value("Please wait 37 seconds before requesting another code."));
    }
}
