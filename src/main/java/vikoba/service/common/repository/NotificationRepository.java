package vikoba.service.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.common.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

}
