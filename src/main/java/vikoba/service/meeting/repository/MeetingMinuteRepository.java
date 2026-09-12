package vikoba.service.meeting.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.meeting.entity.MeetingMinute;

public interface MeetingMinuteRepository extends JpaRepository<MeetingMinute, Long> {
    Optional<MeetingMinute> findByMeetingId(Long meetingId);
}
