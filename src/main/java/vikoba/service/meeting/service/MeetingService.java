package vikoba.service.meeting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vikoba.service.meeting.repository.MeetingAttendanceRepository;
import vikoba.service.meeting.repository.MeetingRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private final FineTypeRepository fineTypeRepository;
    private final FineRepository fineRepository;
    private final GroupSettingsRepository groupSettingsRepository;

    @Transactional
    public vikoba.service.meeting.dto.MeetingResponse createMeeting(Long groupId, CreateMeetingRequest request) {
        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));

        Meeting m = Meeting.builder()
                .group(group)
                .title(request.getTitle())
                .meetingDate(request.getMeetingDate() == null ? LocalDate.now() : request.getMeetingDate())
                .startTime(request.getStartTime() == null ? LocalTime.of(0, 0) : request.getStartTime())
                .endTime(request.getEndTime())
                .location(request.getLocation())
                .agenda(request.getAgenda())
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
                .status(saved.getStatus() != null ? saved.getStatus().name() : null)
                .agenda(saved.getAgenda())
                .build();
    }

    @Transactional
    public void recordAttendance(Long meetingId, List<AttendanceRecord> records) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));

        // Prevent recording attendance more than once for the same meeting
        java.util.List<MeetingAttendance> existing = meetingAttendanceRepository.findByMeetingId(meetingId);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalArgumentException("Attendance has already been recorded for this meeting.");
        }

        // Save attendance records
        for (AttendanceRecord r : records) {
            GroupMember gm = groupMemberRepository.findById(r.getGroupMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("Group member not found."));

            MeetingAttendance ma = MeetingAttendance.builder()
                    .meeting(meeting)
                    .groupMember(gm)
                    .status(vikoba.service.common.enums.AttendanceStatus.valueOf(r.getStatus()))
                    .arrivalTime(r.getArrivalTime())
                    .reason(r.getReason())
                    .build();

            meetingAttendanceRepository.save(ma);

            // If absent, create fine according to group settings / fine type
            if (r.getStatus() != null && r.getStatus().equalsIgnoreCase("ABSENT")) {
                GroupSettings settings = groupSettingsRepository.findByGroupId(meeting.getGroup().getId()).orElse(null);
                BigDecimal amount;
                if (settings != null && settings.getLatePaymentFine() != null) {
                    // fallback: use latePaymentFine if meeting absence fine not configured
                    amount = settings.getLatePaymentFine();
                } else {
                    amount = null;
                }

                // Look up fine type MEETING_ABSENCE or create default
                FineType fineType = fineTypeRepository
                        .findByGroupIdAndCode(meeting.getGroup().getId(), "MEETING_ABSENCE").orElseGet(() -> {
                            FineType ft = FineType.builder()
                                    .group(meeting.getGroup())
                                    .code("MEETING_ABSENCE")
                                    .name("Meeting absence")
                                    .defaultAmount(amount == null ? BigDecimal.ZERO : amount)
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
                        .reason("Absent from meeting: " + meeting.getTitle())
                        .status(vikoba.service.common.enums.FineStatus.UNPAID)
                        .build();

                fineRepository.save(fine);
            }
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<vikoba.service.meeting.dto.MeetingResponse> listMeetingsForGroup(Long groupId) {
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

        return vikoba.service.meeting.dto.MeetingResponse.builder()
                .id(m.getId())
                .groupId(m.getGroup() != null ? m.getGroup().getId() : null)
                .title(m.getTitle())
                .meetingDate(m.getMeetingDate())
                .startTime(m.getStartTime())
                .endTime(m.getEndTime())
                .location(m.getLocation())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .agenda(m.getAgenda())
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.List<vikoba.service.meeting.dto.AttendanceRecord> listAttendanceForMeeting(Long meetingId) {
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
}
