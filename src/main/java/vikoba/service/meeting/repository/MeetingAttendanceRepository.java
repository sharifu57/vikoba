package vikoba.service.meeting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.meeting.entity.MeetingAttendance;

import java.util.List;

public interface MeetingAttendanceRepository extends JpaRepository<MeetingAttendance, Long> {
//    @Query("""
//                SELECT ma
//                FROM MeetingAttendance ma
//                WHERE ma.groupMember.id = :groupMemberId
//                ORDER BY ma.meeting.meetingDate DESC
//            """)
//    List<MeetingAttendance> findByGroupMemberId(
//            @Param("groupMemberId") Long groupMemberId);

    @Query("""
                SELECT ma
                FROM MeetingAttendance ma
                WHERE ma.meeting.id = :meetingId
                ORDER BY ma.id ASC
            """)
    List<MeetingAttendance> findByMeetingId(@Param("meetingId") Long meetingId);


    @Query("""
        SELECT ma
        FROM MeetingAttendance ma
        JOIN FETCH ma.meeting m
        WHERE ma.groupMember.id = :groupMemberId
        ORDER BY m.meetingDate DESC
    """)
    List<MeetingAttendance> findByGroupMemberId(
            @Param("groupMemberId") Long groupMemberId
    );
}
