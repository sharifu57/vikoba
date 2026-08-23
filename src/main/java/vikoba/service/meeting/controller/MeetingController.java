package vikoba.service.meeting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.meeting.dto.AttendanceRecord;
import vikoba.service.meeting.dto.CreateMeetingRequest;
import vikoba.service.meeting.dto.MeetingResponse;
import vikoba.service.meeting.service.MeetingService;
import vikoba.service.organization.dto.GroupWithSettingsResponse;
import vikoba.service.organization.service.VikobaService;

import java.util.Collections;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MeetingController {
    private final MeetingService meetingService;
    private final VikobaService vikobaService;

    @PostMapping("/groups/{groupId}/meetings")
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(@PathVariable("groupId") Long groupId,
            @RequestBody CreateMeetingRequest request) {
        try {
            MeetingResponse m = meetingService.createMeeting(groupId, request);
            return ResponseEntity.ok(ApiResponse.success("Meeting created successfully.", m));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/groups/{groupId}/meetings")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> listMeetings(@PathVariable("groupId") Long groupId) {
        try {
            return ResponseEntity
                    .ok(ApiResponse.success("Meetings retrieved.", meetingService.listMeetingsForGroup(groupId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/meetings")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> listMeetingsOptional(
            @RequestParam(value = "groupId", required = false) Long groupId) {
        try {
            if (groupId != null) {
                return ResponseEntity
                        .ok(ApiResponse.success("Meetings retrieved.", meetingService.listMeetingsForGroup(groupId)));
            }

            List<GroupWithSettingsResponse> groups = vikobaService.listGroupsForCurrentUser();
            if (groups == null || groups.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success("No meetings found.", Collections.emptyList()));
            }

            GroupWithSettingsResponse primary = groups.stream()
                    .filter(GroupWithSettingsResponse::isSettingsConfigured)
                    .findFirst()
                    .orElse(groups.get(0));

            Long primaryGroupId = primary.getGroup().getGroupId();

            return ResponseEntity.ok(
                    ApiResponse.success("Meetings retrieved.", meetingService.listMeetingsForGroup(primaryGroupId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/meetings/{id}/attendance")
    public ResponseEntity<ApiResponse<Void>> recordAttendance(@PathVariable("id") Long meetingId,
            @RequestBody List<AttendanceRecord> records) {
        try {
            meetingService.recordAttendance(meetingId, records);
            return ResponseEntity.ok(ApiResponse.success("Attendance recorded.", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/meetings/{id}")
    public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(@PathVariable("id") Long meetingId) {
        try {
            MeetingResponse m = meetingService.getMeetingById(meetingId);
            return ResponseEntity.ok(ApiResponse.success("Meeting retrieved.", m));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/meetings/{id}/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceRecord>>> getAttendance(@PathVariable("id") Long meetingId) {
        try {
            return ResponseEntity.ok(
                    ApiResponse.success("Attendance retrieved.", meetingService.listAttendanceForMeeting(meetingId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
