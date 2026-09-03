package vikoba.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.entity.Notification;
import vikoba.service.common.enums.NotificationType;
import vikoba.service.common.repository.NotificationRepository;
import vikoba.service.config.SystemEnv;
import vikoba.service.common.service.SystemSettingService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {
    private final RestTemplate restTemplate;
    private final SystemEnv dbEnv;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;


    public boolean send(String customerPhone, String message) {
        if (customerPhone == null || customerPhone.trim().isEmpty()) {
            log.warn("SMS not sent: phone number is empty");
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            log.warn("SMS not sent: message is empty");
            return false;
        }

        User user = userRepository.findByPhone(customerPhone).orElse(null);
        if (user == null) {
            log.warn("SMS not persisted/sent because no user exists for phone {}", customerPhone);
            return false;
        }

        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .title("Vikoba notification")
                .message(message)
                .type(NotificationType.INFO)
                .channel("SMS")
                .deliveryStatus("PENDING")
                .recipientPhone(customerPhone)
                .build());

        if (dbEnv.smsApiKey == null || dbEnv.smsApiKey.isBlank()) {
            notification.setDeliveryStatus("FAILED");
            notification.setProviderResponse("SMS provider secret is not configured");
            notificationRepository.save(notification);
            log.warn("SMS not sent because sms.secret.key is not configured");
            return false;
        }

        try {

            Map<String, Object> payload = new HashMap<>();

            payload.put("message", message);

            // recipients must be an array of objects
            Map<String, String> recipient = new HashMap<>();
            recipient.put("phoneNumber", customerPhone);
            recipient.put("name", "");

            payload.put(
                    "recipients",
                    Collections.singletonList(recipient));
            payload.put("senderIdentity", systemSettingService.get("sms.sender.id", dbEnv.senderId));
            payload.put("callbackUrl", "");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            headers.set("apiKey", dbEnv.smsApiKey);

            // Keep if the API requires it as a header too
            headers.set("senderIdentity", systemSettingService.get("sms.sender.id", dbEnv.senderId));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    systemSettingService.get("sms.dispatch.url", dbEnv.smsUrl),
                    HttpMethod.POST,
                    request,
                    Map.class);

            notification.setDeliveryStatus(response.getStatusCode().is2xxSuccessful() ? "SENT" : "FAILED");
            notification.setProviderResponse(String.valueOf(response.getBody()));
            notification.setSentAt(java.time.LocalDateTime.now());
            notificationRepository.save(notification);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            notification.setDeliveryStatus("FAILED");
            notification.setProviderResponse(e.getMessage());
            notificationRepository.save(notification);
            log.warn("Failed to send SMS to {}: {}", customerPhone, e.getMessage());

            return false;
        }
    }
}
