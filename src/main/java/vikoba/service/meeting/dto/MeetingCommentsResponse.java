package vikoba.service.meeting.dto;
import java.time.LocalDateTime;
import java.util.List;
public record MeetingCommentsResponse(boolean canComment, String reason, List<Comment> comments) {
    public record Comment(Long id, Long groupMemberId, String memberName, String content, LocalDateTime createdAt) {}
}
