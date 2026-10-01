package vikoba.service.meeting.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.meeting.dto.*;
import vikoba.service.meeting.service.MeetingCommentService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/meetings/{meetingId}/comments")
public class MeetingCommentController {
    private final MeetingCommentService service;
    @GetMapping public ResponseEntity<ApiResponse<MeetingCommentsResponse>> list(@PathVariable Long meetingId) {
        return ResponseEntity.ok(ApiResponse.success("Meeting comments retrieved.", service.list(meetingId)));
    }
    @PostMapping public ResponseEntity<ApiResponse<MeetingCommentsResponse.Comment>> add(
        @PathVariable Long meetingId, @RequestBody MeetingCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Your comment was saved.", service.add(meetingId, request)));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiResponse.error(error.getMessage()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException error) {
        return ResponseEntity.status(403).body(ApiResponse.error(error.getMessage()));
    }
}
