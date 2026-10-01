package vikoba.service.meeting.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import vikoba.service.meeting.entity.MeetingComment;
public interface MeetingCommentRepository extends JpaRepository<MeetingComment, Long> {
    @EntityGraph(attributePaths = {"groupMember", "groupMember.member"})
    List<MeetingComment> findByMeetingIdOrderByCreatedAtAscIdAsc(Long meetingId);
}
