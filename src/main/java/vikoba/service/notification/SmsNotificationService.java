package vikoba.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {
    private final RestClient.Builder restClient;

    @Value("${sms.secret.key:}")
    private String secretKey;

    @Value("${sms.sender.id:Pago}")
    private String senderId;

    @Value("${sms.dispatch.url:https://api.pago.co.tz/api/v1/sms/dispatch}")
    private String dispatchUrl;

    public boolean send(String phone, String message) {
        if (phone == null || phone.isBlank() || message == null || message.isBlank() || secretKey.isBlank())
            return false;
        try {
            restClient.build().post().uri(dispatchUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("senderId", senderId, "phone", phone, "message", message))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.warn("SMS notification failed for recipient {}: {}", phone, ex.getMessage());
            return false;
        }
    }
}
