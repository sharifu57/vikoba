package vikoba.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.entity.Notification;
import vikoba.service.common.enums.NotificationType;
import vikoba.service.common.repository.NotificationRepository;
import vikoba.service.config.SystemEnv;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {
    private static final String TOPIC = "vikoba.sms";
    private static final String RETRY_TOPIC = "vikoba.sms.retry";
    private static final String DLQ_TOPIC = "vikoba.sms.dlq";
    private static final int MAX_RETRIES = 3;

    private final RestTemplate restTemplate;
    private final SystemEnv dbEnv;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

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

        String normalizedPhone = normalizePhone(customerPhone);
        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .title("Vikoba notification")
                .message(message)
                .type(NotificationType.INFO)
                .channel("SMS")
                .deliveryStatus("PENDING")
                .recipientPhone(normalizedPhone)
                .build());

        try {
            SmsJob job = new SmsJob(notification.getId(), normalizedPhone, recipientName(user), message, 0);
            if (!dbEnv.smsKafkaEnabled) {
                return deliver(job, false);
            }
            String payload = objectMapper.writeValueAsString(job);

            kafkaTemplate.send(TOPIC, notification.getId().toString(), payload)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            notification.setDeliveryStatus("FAILED");
                            notification.setProviderResponse("Unable to enqueue SMS: " + throwable.getMessage());
                            notificationRepository.save(notification);
                            log.warn("Unable to enqueue SMS for {}: {}", customerPhone, throwable.getMessage(),
                                    throwable);
                        } else {
                            log.info("Queued SMS notification {} for {}", notification.getId(), customerPhone);
                        }
                    });

            return true;
        } catch (Exception e) {
            notification.setDeliveryStatus("FAILED");
            notification.setProviderResponse("Unable to enqueue SMS: " + e.getMessage());
            notificationRepository.save(notification);
            log.warn("Unable to enqueue SMS for {}: {}", customerPhone, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends an SMS to a group member even when that member does not yet have a
     * login account. Meeting invitations use this path because membership, not
     * application registration, determines who must be notified.
     */
    public boolean sendToPhone(String customerPhone, String recipientName, String message) {
        if (customerPhone == null || customerPhone.trim().isEmpty()) {
            log.warn("SMS not sent: phone number is empty");
            return false;
        }
        if (message == null || message.trim().isEmpty()) {
            log.warn("SMS not sent: message is empty");
            return false;
        }
        if (dbEnv.smsApiKey == null || dbEnv.smsApiKey.isBlank()) {
            log.warn("SMS not sent to {}: provider secret is not configured", customerPhone);
            return false;
        }

        String normalizedPhone = normalizePhone(customerPhone);
        String name = recipientName == null || recipientName.isBlank() ? "Member" : recipientName.trim();
        try {
            ResponseEntity<Map> response = dispatch(normalizedPhone, name, message);
            boolean accepted = response.getStatusCode().is2xxSuccessful();
            if (accepted) {
                log.info("SMS provider accepted direct message to {} with status {}", normalizedPhone,
                        response.getStatusCode());
            } else {
                log.warn("SMS provider rejected direct message to {} with status {}", normalizedPhone,
                        response.getStatusCode());
            }
            return accepted;
        } catch (Exception e) {
            log.warn("Direct SMS delivery failed to {}: {}", normalizedPhone, providerError(e));
            return false;
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms}",
            autoStartup = "${sms.kafka.enabled:false}")
    public void consume(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            deliver(job, true);
        } catch (Exception e) {
            log.error("Invalid SMS queue message", e);
        }
    }

    @KafkaListener(topics = RETRY_TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms-retry}",
            autoStartup = "${sms.kafka.enabled:false}")
    public void consumeRetry(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            deliver(job, true);
        } catch (Exception e) {
            log.error("Invalid SMS retry message", e);
        }
    }

    @KafkaListener(topics = DLQ_TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms-dlq}",
            autoStartup = "${sms.kafka.enabled:false}")
    public void consumeDlq(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            Notification notification = notificationRepository.findById(job.notificationId()).orElse(null);
            if (notification != null) {
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse("SMS delivery exhausted after " + MAX_RETRIES + " retries");
                notificationRepository.save(notification);
            }
            log.warn("SMS delivery moved to DLQ after retries for notification {} to {}", job.notificationId(),
                    job.phone());
        } catch (Exception e) {
            log.error("Invalid SMS DLQ message", e);
        }
    }

    private boolean deliver(SmsJob job, boolean queueRetries) {
        Notification notification = notificationRepository.findById(job.notificationId()).orElse(null);
        if (notification == null) {
            return false;
        }

        String senderIdentity = dbEnv.senderId;
        if (dbEnv.smsApiKey == null || dbEnv.smsApiKey.isBlank()) {
            notification.setDeliveryStatus("FAILED");
            notification.setProviderResponse("SMS provider secret is not configured");
            notificationRepository.save(notification);
            return false;
        }

        try {
            ResponseEntity<Map> response = dispatch(job.phone(), job.name(), job.message());

            log.info("SMS provider responded with status {} for notification {}", response.getStatusCode(),
                    notification.getId());

            if (response.getStatusCode().is2xxSuccessful()) {
                notification.setDeliveryStatus("SENT");
                notification.setProviderResponse(String.valueOf(response.getBody()));
                notification.setSentAt(LocalDateTime.now());
                notificationRepository.save(notification);
                return true;
            }

            throw new IllegalStateException(
                    "Provider returned status " + response.getStatusCode() + ": " + response.getBody());
        } catch (Exception e) {
            if (queueRetries) {
                handleDeliveryFailure(job, notification, e);
            } else {
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse(providerError(e));
                notificationRepository.save(notification);
                log.warn("Direct SMS delivery failed for notification {} to {}: {}", notification.getId(),
                        job.phone(), providerError(e));
            }
            return false;
        }
    }

    private ResponseEntity<Map> dispatch(String phone, String name, String message) {
        String senderIdentity = dbEnv.senderId;
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("senderIdentity", senderIdentity);
        payload.put("callbackUrl", dbEnv.smsCallbackUrl == null ? "" : dbEnv.smsCallbackUrl);
        payload.put("recipients", List.of(Map.of(
                "phoneNumber", phone,
                "name", name == null || name.isBlank() ? "Member" : name)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apiKey", dbEnv.smsApiKey);
        headers.set("senderIdentity", senderIdentity);

        return restTemplate.exchange(
                dbEnv.smsUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class);
    }

    private void handleDeliveryFailure(SmsJob job, Notification notification, Exception e) {
        int nextAttempt = job.attempt() + 1;

        if (job.attempt() < MAX_RETRIES) {
            notification.setDeliveryStatus("RETRYING");
            notification.setProviderResponse(
                    "Retrying SMS delivery (attempt " + nextAttempt + "/" + MAX_RETRIES + "): " + e.getMessage());
            notificationRepository.save(notification);

            try {
                kafkaTemplate.send(RETRY_TOPIC, job.notificationId().toString(), objectMapper.writeValueAsString(
                        new SmsJob(job.notificationId(), job.phone(), job.name(), job.message(), nextAttempt)))
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                log.warn("Failed to enqueue SMS retry for notification {} to {}", job.notificationId(),
                                        job.phone(), throwable);
                            }
                        });
            } catch (Exception queueException) {
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse("Retry queue failed: " + queueException.getMessage());
                notificationRepository.save(notification);
                log.warn("Retry queue failure for notification {} to {}: {}", job.notificationId(), job.phone(),
                        queueException.getMessage(), queueException);
            }
            return;
        }

        notification.setDeliveryStatus("FAILED");
        notification.setProviderResponse("SMS delivery failed after " + MAX_RETRIES + " retries: " + e.getMessage());
        notificationRepository.save(notification);

        try {
            kafkaTemplate.send(DLQ_TOPIC, job.notificationId().toString(), objectMapper.writeValueAsString(job))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("Failed to send SMS job {} to DLQ", job.notificationId(), throwable);
                        }
                    });
        } catch (Exception queueException) {
            log.warn("Unable to enqueue DLQ SMS for notification {}: {}", job.notificationId(),
                    queueException.getMessage(), queueException);
        }

        log.warn("Failed to send SMS to {} after {} retries: {}", job.phone(), MAX_RETRIES, e.getMessage(), e);
    }

    private String normalizePhone(String phone) {
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("0") && normalized.length() == 10) {
            return "255" + normalized.substring(1);
        }
        return normalized;
    }

    private String recipientName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "Member";
    }

    private String providerError(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "SMS provider returned " + responseException.getStatusCode() + ": "
                    + responseException.getResponseBodyAsString();
        }
        return "SMS provider request failed: " + exception.getMessage();
    }

    private record SmsJob(Long notificationId, String phone, String name, String message, int attempt) {
    }
}
