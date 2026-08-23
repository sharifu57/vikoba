package vikoba.service.member360.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.meeting.repository.MeetingAttendanceRepository;
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
        if (groupMemberId == null)
            throw new IllegalArgumentException("groupMemberId is required");

        var groupMemberOpt = groupMemberRepository.findById(groupMemberId);
        if (groupMemberOpt.isEmpty())
            throw new IllegalArgumentException("Member not found.");

        var gm = groupMemberOpt.get();

        // Reuse existing MemberService mapping to MemberResponse by fetching members of
        // group and filtering
        MemberResponse memberResp = memberService.getMembersByGroup(gm.getGroup().getId())
                .stream()
                .filter(m -> m.getId().equals(groupMemberId))
                .findFirst()
                .orElse(null);

        List<?> contributions = memberContributionRepository.findByGroupMemberId(groupMemberId);
        List<?> loans = loanRepository.findByGroupMemberId(groupMemberId);
        List<?> fines = fineRepository.findByGroupMemberId(groupMemberId);
        List<?> attendance = meetingAttendanceRepository.findByGroupMemberId(groupMemberId);
        List<?> social = socialFundContributionRepository.findByGroupMemberId(groupMemberId);

        Member360Response resp = new Member360Response();
        resp.setMember(memberResp);
        resp.setContributions(contributions);
        resp.setLoans(loans);
        resp.setFines(fines);
        resp.setMeetingAttendance(attendance);
        resp.setSocialFundContributions(social);
        return resp;
    }
}
