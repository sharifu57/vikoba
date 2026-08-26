package vikoba.service.meeting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.meeting.entity.Meeting;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    @Query("""
                    SELECT m FROM Meeting m
                    WHERE m.group.id = :groupId
                        AND m.meetingDate >= CURRENT_DATE
                    ORDER BY m.meetingDate ASC, m.startTime ASC
            """)
    List<Meeting> findUpcomingByGroupId(@Param("groupId") Long groupId);

    @Query("""
                SELECT m FROM Meeting m
                WHERE m.group.id = :groupId
                ORDER BY m.meetingDate DESC, m.startTime DESC
            """)
    List<Meeting> findByGroupIdOrderByMeetingDateDesc(@Param("groupId") Long groupId);
}
