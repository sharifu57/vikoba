package vikoba.service.meeting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import vikoba.service.common.entity.Notification;
import vikoba.service.common.enums.NotificationType;
import vikoba.service.fine.entity.Fine;
import vikoba.service.fine.entity.FineType;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.fine.repository.FineTypeRepository;
import vikoba.service.meeting.dto.AttendanceRecord;
import vikoba.service.meeting.dto.CreateMeetingRequest;
import vikoba.service.meeting.entity.Meeting;
import vikoba.service.meeting.entity.MeetingAttendance;
import vikoba.service.meeting.entity.MeetingMinute;
import vikoba.service.meeting.repository.MeetingAttendanceRepository;
import vikoba.service.meeting.repository.MeetingMinuteRepository;
import vikoba.service.meeting.repository.MeetingRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final VikobaGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MeetingAttendanceRepository meetingAttendanceRepository;
    private final MeetingMinuteRepository meetingMinuteRepository;
    private final FineTypeRepository fineTypeRepository;
    private final FineRepository fineRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final GroupAuthorizationService authorizationService;

    @Transactional
    public vikoba.service.meeting.dto.MeetingResponse createMeeting(Long groupId, CreateMeetingRequest request) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }
        authorizationService.requireMeetingManagementAccess(groupId);
        if (request == null) {
            throw new IllegalArgumentException("Meeting details are required.");
        }

        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));

        String title = required(request.getTitle(), "title");
        String mode = normalisedMode(request.getMeetingMode());
        LocalDate meetingDate = request.getMeetingDate();
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        if (meetingDate == null || startTime == null) {
            throw new IllegalArgumentException("Meeting date and start time are required.");
        }
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after the start time.");
        }

        String location = blankToNull(request.getLocation());
        String meetingLink = blankToNull(request.getMeetingLink());
        if ("ONLINE".equals(mode)) {
            validateMeetingLink(meetingLink);
            location = null;
        } else {
            if (location == null) {
                throw new IllegalArgumentException("A physical meeting location is required.");
            }
            meetingLink = null;
        }

        Meeting m = Meeting.builder()
                .group(group)
                .title(title)
                .meetingDate(meetingDate)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .meetingMode(mode)
                .meetingLink(meetingLink)
                .agenda(blankToNull(request.getAgenda()))
                .status(vikoba.service.common.enums.MeetingStatus.SCHEDULED)
                .build();

        Meeting saved = meetingRepository.save(m);

        // Notify all group members with linked user accounts
        var members = groupMemberRepository.findByGroupIdAndStatus(group.getId(),
                vikoba.service.common.enums.MembershipStatus.ACTIVE);
        for (GroupMember gm : members) {
            var maybeUser = userRepository.findByMemberId(gm.getMember().getId());
            if (maybeUser.isPresent()) {
                User user = maybeUser.get();
                Notification note = Notification.builder()
                        .user(user)
                        .title("New meeting: " + saved.getTitle())
                        .message("Meeting scheduled on " + saved.getMeetingDate() + " at "
                                + (saved.getStartTime() != null ? saved.getStartTime() : ""))
                        .type(NotificationType.MEETING)
                        .referenceType("MEETING")
                        .referenceId(saved.getId())
                        .build();
                notificationRepository.save(note);
            }
        }

        // build response DTO while still in transaction so lazy properties are
        // accessible
        return vikoba.service.meeting.dto.MeetingResponse.builder()
                .id(saved.getId())
                .groupId(saved.getGroup() != null ? saved.getGroup().getId() : null)
                .title(saved.getTitle())
                .meetingDate(saved.getMeetingDate())
                .startTime(saved.getStartTime())
                .endTime(saved.getEndTime())
                .location(saved.getLocation())
                .meetingMode(saved.getMeetingMode())
                .meetingLink(saved.getMeetingLink())
                .status(saved.getStatus() != null ? saved.getStatus().name() : null)
                .agenda(saved.getAgenda())
                .build();
    }

    @Transactional
    public void recordAttendance(Long meetingId, List<AttendanceRecord> records) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
        authorizationService.requireMeetingManagementAccess(meeting.getGroup().getId());

        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("At least one attendance record is required.");
        }

        // Prevent recording attendance more than once for the same meeting
        java.util.List<MeetingAttendance> existing = meetingAttendanceRepository.findByMeetingId(meetingId);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalArgumentException("Attendance has already been recorded for this meeting.");
        }

        // Save attendance records
        for (AttendanceRecord r : records) {
            GroupMember gm = groupMemberRepository.findById(r.getGroupMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("Group member not found."));
            if (!gm.getGroup().getId().equals(meeting.getGroup().getId())) {
                throw new IllegalArgumentException("Attendance member does not belong to this meeting's group.");
            }

            MeetingAttendance ma = MeetingAttendance.builder()
                    .meeting(meeting)
                    .groupMember(gm)
                    .status(vikoba.service.common.enums.AttendanceStatus.valueOf(r.getStatus()))
                    .arrivalTime(r.getArrivalTime())
                    .reason(r.getReason())
                    .build();

            meetingAttendanceRepository.save(ma);

            // Attendance fines always use the group's configured fine type amount.
            if (r.getStatus() != null && (r.getStatus().equalsIgnoreCase("ABSENT") || r.getStatus().equalsIgnoreCase("LATE"))) {
                boolean absent = r.getStatus().equalsIgnoreCase("ABSENT");
                String fineCode = absent ? "MEETING_ABSENCE" : "MEETING_LATE";
                String fineName = absent ? "Meeting absence" : "Late arrival";
                FineType fineType = fineTypeRepository
                        .findByGroupIdAndCode(meeting.getGroup().getId(), fineCode).orElseGet(() -> {
                            FineType ft = FineType.builder()
                                    .group(meeting.getGroup())
                                    .code(fineCode)
                                    .name(fineName)
                                    .defaultAmount(BigDecimal.ZERO)
                                    .active(true)
                                    .build();
                            return fineTypeRepository.save(ft);
                        });

                BigDecimal fineAmount = fineType.getDefaultAmount();
                if (fineAmount == null)
                    fineAmount = BigDecimal.ZERO;

                Fine fine = Fine.builder()
                        .groupMember(gm)
                        .fineType(fineType)
                        .reference("FINE-MEET-" + meeting.getId() + "-" + gm.getId())
                        .amount(fineAmount)
                        .issuedDate(java.time.LocalDate.now())
                        .reason((absent ? "Absent from" : "Late for") + " meeting: " + meeting.getTitle())
                        .status(vikoba.service.common.enums.FineStatus.UNPAID)
                        .build();

                fineRepository.save(fine);
            }
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<vikoba.service.meeting.dto.MeetingResponse> listMeetingsForGroup(Long groupId) {
        authorizationService.requireMembership(groupId);
        java.util.List<Meeting> meetings = meetingRepository.findByGroupIdOrderByMeetingDateDesc(groupId);
        java.util.List<vikoba.service.meeting.dto.MeetingResponse> resp = new java.util.ArrayList<>();
        for (Meeting m : meetings) {
            resp.add(vikoba.service.meeting.dto.MeetingResponse.builder()
                    .id(m.getId())
                    .groupId(m.getGroup() != null ? m.getGroup().getId() : null)
                    .title(m.getTitle())
                    .meetingDate(m.getMeetingDate())
                    .startTime(m.getStartTime())
                    .endTime(m.getEndTime())
                    .location(m.getLocation())
                    .meetingMode(m.getMeetingMode())
                    .meetingLink(m.getMeetingLink())
                    .status(m.getStatus() != null ? m.getStatus().name() : null)
                    .agenda(m.getAgenda())
                    .build());
        }
        return resp;
    }

    @Transactional(readOnly = true)
    public vikoba.service.meeting.dto.MeetingResponse getMeetingById(Long meetingId) {
        Meeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
        authorizationService.requireMembership(m.getGroup().getId());

        return vikoba.service.meeting.dto.MeetingResponse.builder()
                .id(m.getId())
                .groupId(m.getGroup() != null ? m.getGroup().getId() : null)
                .title(m.getTitle())
                .meetingDate(m.getMeetingDate())
                .startTime(m.getStartTime())
                .endTime(m.getEndTime())
                .location(m.getLocation())
                .meetingMode(m.getMeetingMode())
                .meetingLink(m.getMeetingLink())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .agenda(m.getAgenda())
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.List<vikoba.service.meeting.dto.AttendanceRecord> listAttendanceForMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
        authorizationService.requireMembership(meeting.getGroup().getId());
        java.util.List<MeetingAttendance> attendance = meetingAttendanceRepository.findByMeetingId(meetingId);
        java.util.List<vikoba.service.meeting.dto.AttendanceRecord> resp = new java.util.ArrayList<>();
        for (MeetingAttendance ma : attendance) {
            vikoba.service.meeting.dto.AttendanceRecord r = new vikoba.service.meeting.dto.AttendanceRecord();
            r.setGroupMemberId(ma.getGroupMember().getId());
            r.setStatus(ma.getStatus() != null ? ma.getStatus().name() : null);
            r.setArrivalTime(ma.getArrivalTime());
            r.setReason(ma.getReason());
            resp.add(r);
        }
        return resp;
    }

    @Transactional
    public vikoba.service.meeting.dto.MeetingMinutesResponse saveMinutes(Long meetingId,
            vikoba.service.meeting.dto.MeetingMinutesRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
        authorizationService.requirePermission(meeting.getGroup().getId(), "MEETING_MINUTES_MANAGE");
        if (request == null || blankToNull(request.getContent()) == null) {
            throw new IllegalArgumentException("Meeting minutes content is required.");
        }

        MeetingMinute minutes = meetingMinuteRepository.findByMeetingId(meetingId)
                .orElseGet(() -> MeetingMinute.builder().meeting(meeting).build());
        minutes.setContent(request.getContent().trim());
        minutes.setApprovedAt(request.isApproved() ? java.time.LocalDateTime.now() : null);
        return toMinutesResponse(meetingMinuteRepository.save(minutes));
    }

    @Transactional(readOnly = true)
    public vikoba.service.meeting.dto.MeetingMinutesResponse getMinutes(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
        authorizationService.requireMembership(meeting.getGroup().getId());
        return meetingMinuteRepository.findByMeetingId(meetingId).map(this::toMinutesResponse).orElse(null);
    }

    private vikoba.service.meeting.dto.MeetingMinutesResponse toMinutesResponse(MeetingMinute minutes) {
        return vikoba.service.meeting.dto.MeetingMinutesResponse.builder()
                .id(minutes.getId())
                .meetingId(minutes.getMeeting().getId())
                .content(minutes.getContent())
                .approvedAt(minutes.getApprovedAt())
                .updatedAt(minutes.getUpdatedAt())
                .build();
    }

    private String required(String value, String field) {
        String result = blankToNull(value);
        if (result == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return result;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalisedMode(String value) {
        String mode = value == null ? "PHYSICAL" : value.trim().toUpperCase();
        if (!"ONLINE".equals(mode) && !"PHYSICAL".equals(mode)) {
            throw new IllegalArgumentException("Meeting mode must be ONLINE or PHYSICAL.");
        }
        return mode;
    }

    private void validateMeetingLink(String meetingLink) {
        if (meetingLink == null) {
            throw new IllegalArgumentException("An online meeting link is required.");
        }
        try {
            URI uri = new URI(meetingLink);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Enter a valid online meeting link.");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Enter a valid online meeting link.");
        }
    }
}
