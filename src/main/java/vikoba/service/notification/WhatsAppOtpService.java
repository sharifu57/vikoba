package vikoba.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Sends OTPs through the WhatsApp Business Cloud API when configured. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppOtpService {
    private final RestTemplate restTemplate;

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.otp-template:}")
    private String templateName;

    @Value("${whatsapp.otp-template-language:en}")
    private String templateLanguage;

    @Value("${whatsapp.graph-api-version:v27.0}")
    private String graphApiVersion;

    public boolean send(String phone, String code) {
        String recipientPhone = phone == null ? "" : phone.replaceAll("\\D", "");
        if (recipientPhone.isBlank()) {
            log.warn("WhatsApp OTP API call skipped: recipient phone number is invalid");
            return false;
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(accessToken)) missing.add("WHATSAPP_ACCESS_TOKEN");
        if (isBlank(phoneNumberId)) missing.add("WHATSAPP_PHONE_NUMBER_ID");
        if (isBlank(templateName)) missing.add("WHATSAPP_OTP_TEMPLATE");
        if (isBlank(templateLanguage)) missing.add("WHATSAPP_OTP_TEMPLATE_LANGUAGE");
        if (isBlank(graphApiVersion)) missing.add("WHATSAPP_GRAPH_API_VERSION");
        if (!missing.isEmpty()) {
            log.warn("WhatsApp OTP API call skipped; missing configuration: {}", String.join(", ", missing));
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", recipientPhone,
                    "type", "template",
                    "template", Map.of(
                            "name", templateName,
                            "language", Map.of("code", templateLanguage),
                            "components", List.of(
                                    Map.of(
                                            "type", "body",
                                            "parameters", List.of(Map.of("type", "text", "text", code))),
                                    Map.of(
                                            "type", "button",
                                            "sub_type", "url",
                                            "index", "0",
                                            "parameters", List.of(Map.of("type", "text", "text", code))))));
            String endpoint = "https://graph.facebook.com/" + graphApiVersion + "/" + phoneNumberId + "/messages";
            restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
            log.info("WhatsApp OTP API accepted the message");
            return true;
        } catch (org.springframework.web.client.RestClientResponseException exception) {
            // Keep Meta's response body private; the status is enough to diagnose most configuration errors.
            log.warn("WhatsApp OTP API returned HTTP {}; SMS fallback will be used",
                    exception.getStatusCode().value());
            return false;
        } catch (Exception exception) {
            // Do not log the code, phone, token, or provider response for authentication messages.
            log.warn("WhatsApp OTP API call failed; SMS fallback will be used ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
