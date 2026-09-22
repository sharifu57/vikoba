package vikoba.service.notification;

public record SmsNotificationRequestedEvent(String phone, String recipientName, String message) {
}
