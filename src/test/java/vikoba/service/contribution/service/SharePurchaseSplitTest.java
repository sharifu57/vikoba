package vikoba.service.contribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vikoba.service.contribution.entity.*;
import vikoba.service.contribution.repository.*;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.*;
import vikoba.service.organization.service.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharePurchaseSplitTest {
    @Mock SharePurchaseRequestRepository requestRepository;
    @Mock ShareProductRepository shareProductRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock GroupSettingsRepository groupSettingsRepository;
    @Mock VikobaGroupRepository vikobaGroupRepository;
    @Mock GroupAuthorizationService authorizationService;
    @Mock ShareService shareService;
    @Mock ShareApprovalWorkflowService workflowService;
    @Mock MemberRoleRepository memberRoleRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks SharePurchaseRequestService service;

    @Test void totalPaymentIsSplitAndSavedForApprovalWithoutCreditingShares() {
        VikobaGroup group = new VikobaGroup(); group.setId(7L);
        Member person = new Member(); person.setFirstName("Asha"); person.setLastName("Member");
        GroupMember member = new GroupMember(); member.setId(42L); member.setGroup(group); member.setMember(person);
        GroupSettings settings = new GroupSettings();
        settings.setSharePrice(new BigDecimal("1000"));
        settings.setJamiiContributionPerSharePayment(new BigDecimal("2000"));
        settings.setMinimumSharePurchaseAmount(new BigDecimal("1000"));
        when(authorizationService.requireCurrentMembership(7L)).thenReturn(member);
        when(groupSettingsRepository.findByGroupId(7L)).thenReturn(Optional.of(settings));
        when(shareProductRepository.findByGroupIdAndCode(7L, "STANDARD")).thenReturn(Optional.of(new ShareProduct()));
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var proof = new MockMultipartFile("proofFile", "receipt.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0});
        var result = service.submit(7L, new BigDecimal("10000"), null, "Mobile Money", "REF", null, proof);
        assertEquals(0, new BigDecimal("8000").compareTo(result.getAmount()));
        assertEquals(0, new BigDecimal("2000").compareTo(result.getJamiiAmount()));
        assertEquals(0, new BigDecimal("8").compareTo(result.getQuantity()));
        assertEquals("PENDING", result.getStatus());
        assertTrue(result.isHasProofFile());
        verifyNoInteractions(shareService);
    }
}
