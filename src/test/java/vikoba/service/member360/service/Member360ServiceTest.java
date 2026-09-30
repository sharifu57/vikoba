package vikoba.service.member360.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.contribution.service.ShareService;
import vikoba.service.fine.repository.FineRepository;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.meeting.repository.MeetingAttendanceRepository;
import vikoba.service.meeting.repository.MeetingRepository;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.organization.service.MemberService;
import vikoba.service.social.repository.SocialFundContributionRepository;
import java.util.Optional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Member360ServiceTest {
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock MemberContributionRepository memberContributionRepository;
    @Mock LoanRepository loanRepository;
    @Mock FineRepository fineRepository;
    @Mock MeetingAttendanceRepository meetingAttendanceRepository;
    @Mock SocialFundContributionRepository socialFundContributionRepository;
    @Mock MemberService memberService;
    @Mock GroupAuthorizationService authorizationService;
    @Mock ShareService shareService;
    @Mock MeetingRepository meetingRepository;
    @InjectMocks Member360Service service;

    private void membership() {
        VikobaGroup group = new VikobaGroup();
        group.setId(7L);
        GroupMember membership = new GroupMember();
        membership.setId(42L);
        membership.setGroup(group);
        membership.setMember(new Member());
        when(groupMemberRepository.findById(42L)).thenReturn(Optional.of(membership));
    }

    @Test void personalDashboardDoesNotRequireMemberRegisterPermission() {
        membership();
        MemberResponse profile = MemberResponse.builder().id(42L).build();
        when(memberService.getDashboardMember(42L)).thenReturn(profile);
        when(shareService.getMemberShareBalance(7L, 42L)).thenReturn(new BigDecimal("2.5"));
        var result = service.getMember360(42L);
        assertSame(profile, result.getMember());
        assertEquals(new BigDecimal("2.5"), result.getSharesOwned());
        verify(authorizationService).requireSelfOrGroupDashboardAccess(7L, 42L);
        verify(memberService, never()).getMembersByGroup(anyLong());
    }

    @Test void unauthorizedDashboardStillDeniedBeforeReadingFinancialRecords() {
        membership();
        doThrow(new AccessDeniedException("Denied"))
            .when(authorizationService).requireSelfOrGroupDashboardAccess(7L, 42L);
        assertThrows(AccessDeniedException.class, () -> service.getMember360(42L));
        verifyNoInteractions(memberService, shareService, loanRepository, fineRepository);
    }
}
