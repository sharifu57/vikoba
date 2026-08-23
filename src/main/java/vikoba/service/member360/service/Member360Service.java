package vikoba.service.member360.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.contribution.dto.MemberContributionResponse;
import vikoba.service.contribution.entity.MemberContribution;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.fine.dto.FineResponse;
import vikoba.service.fine.entity.Fine;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.loan.dto.LoanResponse;
import vikoba.service.loan.entity.Loan;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.meeting.dto.MeetingAttendanceResponse;
import vikoba.service.meeting.entity.Meeting;
import vikoba.service.meeting.entity.MeetingAttendance;
import vikoba.service.meeting.repository.MeetingAttendanceRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.Member;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.social.dto.SocialFundContributionResponse;
import vikoba.service.social.entity.SocialFundContribution;
import vikoba.service.social.repository.SocialFundContributionRepository;
import vikoba.service.member360.dto.Member360Response;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.organization.dto.MemberResponse;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class Member360Service {
    private final MemberRepository memberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberContributionRepository memberContributionRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final MeetingAttendanceRepository meetingAttendanceRepository;
    private final SocialFundContributionRepository socialFundContributionRepository;
    private final vikoba.service.organization.service.MemberService memberService;

    @Transactional(readOnly = true)
    public Member360Response getMember360(Long groupMemberId) {

        if (groupMemberId == null) {
            throw new IllegalArgumentException(
                    "groupMemberId is required"
            );
        }


        // ============================================================
        // 1. FIND GROUP MEMBER
        // ============================================================

        GroupMember groupMember =
                groupMemberRepository
                        .findById(groupMemberId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Member not found."
                                )
                        );


        // ============================================================
        // 2. GET GROUP
        // ============================================================

        VikobaGroup group =
                groupMember.getGroup();

        if (group == null) {

            throw new IllegalArgumentException(
                    "Group membership is not attached to a group."
            );
        }


        // ============================================================
        // 3. GET MEMBER
        // ============================================================

        Member member =
                groupMember.getMember();

        if (member == null) {

            throw new IllegalArgumentException(
                    "Group membership is not attached to a member."
            );
        }


        // ============================================================
        // 4. MEMBER INFORMATION
        // ============================================================

        /*
         * IMPORTANT:
         *
         * MemberResponse.id should be MEMBER ID.
         *
         * groupMemberId is GroupMember ID.
         *
         * They are not necessarily the same.
         */

        MemberResponse memberResponse =
                memberService
                        .getMembersByGroup(group.getId())
                        .stream()
                        .filter(m ->
                                m.getId() != null
                                        && m.getId().equals(member.getId())
                        )
                        .findFirst()
                        .orElse(null);


        // ============================================================
        // 5. CONTRIBUTIONS
        // ============================================================

        List<MemberContributionResponse> contributions =
                memberContributionRepository
                        .findByGroupMemberId(groupMemberId)
                        .stream()
                        .map(this::mapContribution)
                        .toList();


        // ============================================================
        // 6. LOANS
        // ============================================================

        List<LoanResponse> loans =
                loanRepository
                        .findByGroupMemberId(groupMemberId)
                        .stream()
                        .map(this::mapLoan)
                        .toList();


        // ============================================================
        // 7. FINES
        // ============================================================

        List<FineResponse> fines =
                fineRepository
                        .findByGroupMemberId(groupMemberId)
                        .stream()
                        .map(this::mapFine)
                        .toList();


        // ============================================================
        // 8. MEETING ATTENDANCE
        // ============================================================

        List<MeetingAttendanceResponse> attendance =
                meetingAttendanceRepository
                        .findByGroupMemberId(groupMemberId)
                        .stream()
                        .map(this::mapAttendance)
                        .toList();


        // ============================================================
        // 9. SOCIAL FUND
        // ============================================================

        List<SocialFundContributionResponse> socialFundContributions =
                socialFundContributionRepository
                        .findByGroupMemberId(groupMemberId)
                        .stream()
                        .map(this::mapSocialFundContribution)
                        .toList();


        // ============================================================
        // 10. BUILD RESPONSE
        // ============================================================

        Member360Response response =
                new Member360Response();

        response.setMember(memberResponse);

        response.setContributions(
                contributions
        );

        response.setLoans(
                loans
        );

        response.setFines(
                fines
        );

        response.setMeetingAttendance(
                attendance
        );

        response.setSocialFundContributions(
                socialFundContributions
        );


        return response;
    }

    private MemberContributionResponse mapContribution(
            MemberContribution contribution) {

        return MemberContributionResponse.builder()
                .id(contribution.getId())

                .groupMemberId(
                        contribution.getGroupMember() != null
                                ? contribution.getGroupMember().getId()
                                : null
                )

                .contributionPeriodId(
                        contribution.getContributionPeriod() != null
                                ? contribution.getContributionPeriod().getId()
                                : null
                )

                .expectedAmount(
                        contribution.getExpectedAmount()
                )

                .paidAmount(
                        contribution.getPaidAmount()
                )

                .balance(
                        contribution.getBalance()
                )

                .status(
                        contribution.getStatus() != null
                                ? contribution.getStatus().name()
                                : null
                )

                .paidAt(
                        contribution.getPaidAt()
                )

                .build();
    }

    private LoanResponse mapLoan(Loan loan) {

        if (loan == null) {
            return null;
        }

        return LoanResponse.builder()

                .id(loan.getId())

                .groupMemberId(
                        loan.getGroupMember() != null
                                ? loan.getGroupMember().getId()
                                : null
                )

                .loanNumber(
                        loan.getLoanNumber()
                )

                .principalAmount(
                        loan.getPrincipalAmount()
                )

                .interestAmount(
                        loan.getInterestAmount()
                )

                .totalAmount(
                        loan.getTotalAmount()
                )

                .durationMonths(
                        loan.getDurationMonths()
                )

                .applicationDate(
                        loan.getApplicationDate()
                )

                .approvalDate(
                        loan.getApprovalDate()
                )

                .disbursementDate(
                        loan.getDisbursementDate()
                )

                .maturityDate(
                        loan.getMaturityDate()
                )

                .status(
                        loan.getStatus() != null
                                ? loan.getStatus().name()
                                : null
                )

                .purpose(
                        loan.getPurpose()
                )

                .rejectionReason(
                        loan.getRejectionReason()
                )

                .build();
    }


    // ================================================================
    // FINE MAPPER
    // ================================================================

    private FineResponse mapFine(Fine fine) {

        FineResponse response =
                new FineResponse();

        response.setId(
                fine.getId()
        );

        response.setAmount(
                fine.getAmount()
        );

        response.setReason(
                fine.getReason()
        );

        response.setStatus(
                fine.getStatus() != null
                        ? fine.getStatus().toString()
                        : null
        );

        return response;
    }


    // ================================================================
    // MEETING ATTENDANCE MAPPER
    // ================================================================

    private MeetingAttendanceResponse mapAttendance(
            MeetingAttendance attendance) {

        Meeting meeting =
                attendance.getMeeting();

        if (meeting == null) {

            return MeetingAttendanceResponse
                    .builder()
                    .id(attendance.getId())
                    .attendanceStatus(
                            attendance.getStatus()
                    )
                    .arrivalTime(
                            attendance.getArrivalTime()
                    )
                    .reason(
                            attendance.getReason()
                    )
                    .build();
        }


        return MeetingAttendanceResponse
                .builder()
                .id(attendance.getId())

                .meetingId(
                        meeting.getId()
                )

                .meetingTitle(
                        meeting.getTitle()
                )

                .meetingDate(
                        meeting.getMeetingDate()
                )

                .startTime(
                        meeting.getStartTime()
                )

                .endTime(
                        meeting.getEndTime()
                )

                .location(
                        meeting.getLocation()
                )

                .meetingStatus(
                        meeting.getStatus()
                )

                .attendanceStatus(
                        attendance.getStatus()
                )

                .arrivalTime(
                        attendance.getArrivalTime()
                )

                .reason(
                        attendance.getReason()
                )

                .build();
    }


    // ================================================================
    // SOCIAL FUND MAPPER
    // ================================================================

    private SocialFundContributionResponse mapSocialFundContribution(
            SocialFundContribution contribution) {

        if (contribution == null) {
            return null;
        }

        return SocialFundContributionResponse.builder()

                .id(
                        contribution.getId()
                )

                .groupMemberId(
                        contribution.getGroupMember() != null
                                ? contribution.getGroupMember().getId()
                                : null
                )

                .fundTypeId(
                        contribution.getFundType() != null
                                ? contribution.getFundType().getId()
                                : null
                )

                .amount(
                        contribution.getAmount()
                )

                .contributionDate(
                        contribution.getContributionDate()
                )

                .reference(
                        contribution.getReference()
                )

                .build();
    }
}
