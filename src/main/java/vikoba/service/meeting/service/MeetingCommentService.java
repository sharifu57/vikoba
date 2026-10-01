package vikoba.service.meeting.service;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import vikoba.service.common.enums.AttendanceStatus;
import vikoba.service.common.enums.MeetingStatus;
import vikoba.service.meeting.dto.*;
import vikoba.service.meeting.entity.*;
import vikoba.service.meeting.repository.*;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.service.GroupAuthorizationService;
@Service @RequiredArgsConstructor
public class MeetingCommentService {
    private final MeetingRepository meetings;
    private final MeetingAttendanceRepository attendance;
    private final MeetingCommentRepository comments;
    private final GroupAuthorizationService authorization;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MeetingCommentsResponse list(Long meetingId) {
        Meeting meeting = requireMeeting(meetingId);
        GroupMember member = authorization.requireCurrentMembership(meeting.getGroup().getId());
        String reason = restriction(meeting, member);
        return new MeetingCommentsResponse(reason == null, reason,
            comments.findByMeetingIdOrderByCreatedAtAscIdAsc(meetingId).stream().map(this::response).toList());
    }

    @Transactional
    public MeetingCommentsResponse.Comment add(Long meetingId, MeetingCommentRequest request) {
        Meeting meeting = requireMeeting(meetingId);
        GroupMember member = authorization.requireCurrentMembership(meeting.getGroup().getId());
        String reason = restriction(meeting, member);
        if (reason != null) throw new AccessDeniedException(reason);
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isEmpty() || content.length() > 2000)
            throw new IllegalArgumentException("Enter a comment between 1 and 2,000 characters.");
        MeetingComment comment = new MeetingComment();
        comment.setMeeting(meeting);
        comment.setGroupMember(member);
        comment.setContent(content);
        return response(comments.saveAndFlush(comment));
    }

    private Meeting requireMeeting(Long id) {
        return meetings.findById(id).orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
    }

    private String restriction(Meeting meeting, GroupMember member) {
        if (meeting.getStatus() == MeetingStatus.CANCELLED) return "Comments are closed for this cancelled meeting.";
        if (!LocalDate.now(clock.withZone(ZoneId.of("Africa/Dar_es_Salaam"))).equals(meeting.getMeetingDate()))
            return "You can comment only on the meeting day (Tanzania time).";
        boolean present = attendance.findByMeetingIdAndGroupMemberId(meeting.getId(), member.getId())
            .map(row -> row.getStatus() == AttendanceStatus.PRESENT).orElse(false);
        return present ? null : "You must be marked PRESENT to comment on this meeting.";
    }

    private MeetingCommentsResponse.Comment response(MeetingComment comment) {
        var person = comment.getGroupMember().getMember();
        return new MeetingCommentsResponse.Comment(comment.getId(), comment.getGroupMember().getId(),
            person.getFirstName() + " " + person.getLastName(), comment.getContent(), comment.getCreatedAt());
    }
}
