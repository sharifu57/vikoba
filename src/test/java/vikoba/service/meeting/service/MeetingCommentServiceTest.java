package vikoba.service.meeting.service;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vikoba.service.common.enums.*;
import vikoba.service.meeting.dto.*;
import vikoba.service.meeting.entity.*;
import vikoba.service.meeting.repository.*;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.service.GroupAuthorizationService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingCommentServiceTest {
    @Mock MeetingRepository meetings;
    @Mock MeetingAttendanceRepository attendance;
    @Mock MeetingCommentRepository comments;
    @Mock GroupAuthorizationService authorization;
    MeetingCommentService service;
    Meeting meeting;
    GroupMember member;
    @BeforeEach void setup() {
        // UTC is still September 30, but in Tanzania it is October 1.
        service = new MeetingCommentService(meetings, attendance, comments, authorization,
            Clock.fixed(Instant.parse("2026-09-30T22:00:00Z"), ZoneOffset.UTC));
        var group = new VikobaGroup(); group.setId(7L);
        var person = new Member(); person.setFirstName("Asha"); person.setLastName("Member");
        member = new GroupMember(); member.setId(42L); member.setMember(person);
        meeting = new Meeting(); meeting.setId(9L); meeting.setGroup(group);
        meeting.setMeetingDate(LocalDate.of(2026, 10, 1)); meeting.setStatus(MeetingStatus.COMPLETED);
        when(meetings.findById(9L)).thenReturn(Optional.of(meeting));
        when(authorization.requireCurrentMembership(7L)).thenReturn(member);
    }
    private void present(AttendanceStatus status) {
        var row = new MeetingAttendance(); row.setStatus(status);
        when(attendance.findByMeetingIdAndGroupMemberId(9L, 42L)).thenReturn(Optional.of(row));
    }
    @Test void presentMemberCanSaveOnMeetingDayUsingServerTimezone() {
        present(AttendanceStatus.PRESENT);
        when(comments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.add(9L, new MeetingCommentRequest("  Good discussion  "));
        assertEquals("Good discussion", result.content());
        assertEquals(42L, result.groupMemberId());
        verify(comments).saveAndFlush(argThat(row -> row.getMeeting() == meeting && row.getGroupMember() == member));
    }
    @Test void absentLateAndUnrecordedMembersCannotPost() {
        for (AttendanceStatus status : List.of(AttendanceStatus.ABSENT, AttendanceStatus.LATE, AttendanceStatus.EXCUSED)) {
            present(status);
            assertThrows(AccessDeniedException.class, () -> service.add(9L, new MeetingCommentRequest("Idea")));
        }
        when(attendance.findByMeetingIdAndGroupMemberId(9L, 42L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.add(9L, new MeetingCommentRequest("Idea")));
        verifyNoInteractions(comments);
    }
    @Test void pastAndFutureMeetingsAreReadOnly() {
        for (LocalDate date : List.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2))) {
            meeting.setMeetingDate(date);
            assertThrows(AccessDeniedException.class, () -> service.add(9L, new MeetingCommentRequest("Idea")));
            assertFalse(service.list(9L).canComment());
        }
        verify(comments, never()).saveAndFlush(any());
    }
    @Test void cancelledMeetingAndWrongGroupAreDenied() {
        meeting.setStatus(MeetingStatus.CANCELLED);
        assertThrows(AccessDeniedException.class, () -> service.add(9L, new MeetingCommentRequest("Idea")));
        when(authorization.requireCurrentMembership(7L)).thenThrow(new AccessDeniedException("Not your group"));
        assertThrows(AccessDeniedException.class, () -> service.list(9L));
        verifyNoInteractions(comments);
    }
    @Test void blankAndOversizedCommentsAreRejected() {
        present(AttendanceStatus.PRESENT);
        for (String text : List.of("  ", "x".repeat(2001)))
            assertThrows(IllegalArgumentException.class, () -> service.add(9L, new MeetingCommentRequest(text)));
        verifyNoInteractions(comments);
    }
}
