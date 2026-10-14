package vikoba.service.organization.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.PermissionRepository;
import vikoba.service.auth.repository.RoleRepository;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.auth.repository.UserRoleRepository;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.notification.SmsNotificationService;
import vikoba.service.organization.dto.AddMemberRequest;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.Member;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberPermissionRepository;
import vikoba.service.organization.repository.MemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock MemberRoleRepository memberRoleRepository;
    @Mock VikobaGroupRepository groupRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock MemberPermissionRepository memberPermissionRepository;
    @Mock GroupAuthorizationService authorizationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SmsNotificationService smsNotificationService;

    @InjectMocks MemberService memberService;

    private VikobaGroup group;

    @BeforeEach
    void setUp() {
        group = VikobaGroup.builder()
                .name("Umoja Vikoba")
                .code("GRP-10")
                .build();
        group.setId(10L);
    }

    @Test
    void addMemberToGroup_sendsSmsWithGroupDetailsAndAppWebLinks() {
        AddMemberRequest request = AddMemberRequest.builder()
                .groupId(10L)
                .firstName("John")
                .lastName("Doe")
                .phone("255712345678")
                .role(GroupRole.MEMBER)
                .build();

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByPhone("255712345678")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(i -> {
            Member m = i.getArgument(0);
            m.setId(100L);
            return m;
        });
        when(userRepository.findByPhone("255712345678")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(200L);
            return u;
        });
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> {
            GroupMember gm = i.getArgument(0);
            gm.setId(300L);
            return gm;
        });

        MemberRole savedRole = MemberRole.builder()
                .role(GroupRole.MEMBER)
                .active(true)
                .build();
        savedRole.setId(400L);
        when(memberRoleRepository.findByGroupMemberIdAndActiveTrue(any())).thenReturn(List.of(savedRole));
        when(smsNotificationService.send(eq("255712345678"), anyString())).thenReturn(true);

        ApiResponse<MemberResponse> response = memberService.addMemberToGroup(request);

        assertNotNull(response);
        assertEquals("Member added successfully to the group.", response.getMessage());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsNotificationService).send(eq("255712345678"), messageCaptor.capture());

        String smsText = messageCaptor.getValue();
        assertTrue(smsText.contains("Umoja Vikoba"), "Should include group name");
        assertTrue(smsText.contains("www.vikoba360.com"), "Should include web link");
        assertTrue(smsText.contains("APK"), "Should include APK mention");
        assertTrue(smsText.contains("namba yako ya simu"), "Should inform how to sign in");
    }

    @Test
    void addMemberToGroup_handlesSmsDeliveryFailureGracefully() {
        AddMemberRequest request = AddMemberRequest.builder()
                .groupId(10L)
                .firstName("Amina")
                .lastName("Juma")
                .phone("255798765432")
                .role(GroupRole.TREASURER)
                .build();

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(group));
        when(memberRepository.findByPhone("255798765432")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(i -> {
            Member m = i.getArgument(0);
            m.setId(101L);
            return m;
        });
        when(userRepository.findByPhone("255798765432")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(i -> {
            GroupMember gm = i.getArgument(0);
            gm.setId(301L);
            return gm;
        });

        MemberRole savedRole = MemberRole.builder()
                .role(GroupRole.TREASURER)
                .active(true)
                .build();
        savedRole.setId(401L);
        when(memberRoleRepository.findByGroupMemberIdAndActiveTrue(any())).thenReturn(List.of(savedRole));
        when(smsNotificationService.send(eq("255798765432"), anyString())).thenReturn(false);

        ApiResponse<MemberResponse> response = memberService.addMemberToGroup(request);

        assertNotNull(response);
        assertEquals("Member added successfully to the group.", response.getMessage());
        verify(smsNotificationService).send(eq("255798765432"), anyString());
    }
}
