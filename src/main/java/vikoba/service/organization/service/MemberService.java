package vikoba.service.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.auth.repository.RoleRepository;
import vikoba.service.auth.repository.UserRoleRepository;
import vikoba.service.auth.entity.UserRole;
import vikoba.service.auth.entity.Role;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.common.enums.MembershipType;
import vikoba.service.common.enums.UserStatus;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.AddMemberRequest;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.organization.dto.MemberRoleOptionResponse;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.Member;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.notification.SmsNotificationService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {
        private final MemberRepository memberRepository;
        private final GroupMemberRepository groupMemberRepository;
        private final MemberRoleRepository memberRoleRepository;
        private final VikobaGroupRepository groupRepository;
        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final UserRoleRepository userRoleRepository;
        private final PasswordEncoder passwordEncoder;
        private final SmsNotificationService smsNotificationService;

        @Transactional
        public ApiResponse<MemberResponse> addMemberToGroup(
                        AddMemberRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "Member details are required.");
                }

                Long groupId = request.getGroupId();

                if (groupId == null) {
                        throw new IllegalArgumentException(
                                        "groupId is required.");
                }

                // ============================================================
                // 2. FIND GROUP
                // ============================================================

                VikobaGroup group = groupRepository.findById(groupId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Group not found."));

                // ============================================================
                // 3. VALIDATE MEMBER DETAILS
                // ============================================================

                String firstName = required(request.getFirstName(), "firstName");

                String lastName = required(request.getLastName(), "lastName");

                String phone = required(request.getPhone(), "phone");

                phone = phone.trim();

                String email = blankToNull(request.getEmail());

                // ============================================================
                // 4. CHECK IF MEMBER ALREADY EXISTS
                // ============================================================

                Member member = memberRepository.findByPhone(phone)
                                .orElse(null);

                if (member != null) {

                        // --------------------------------------------------------
                        // Member already belongs to this group?
                        // --------------------------------------------------------

                        if (groupMemberRepository
                                        .existsByGroupIdAndMemberId(
                                                        groupId,
                                                        member.getId())) {

                                throw new IllegalArgumentException(
                                                "This member already exists in this group.");
                        }

                        // --------------------------------------------------------
                        // Update member information
                        // --------------------------------------------------------

                        member.setFirstName(firstName);
                        member.setMiddleName(
                                        blankToNull(request.getMiddleName()));
                        member.setLastName(lastName);
                        member.setPhone(phone);
                        member.setEmail(email);
                        member.setNationalId(
                                        blankToNull(request.getNationalId()));
                        member.setAddress(
                                        blankToNull(request.getAddress()));
                        member.setOccupation(
                                        blankToNull(request.getOccupation()));
                        member.setNextOfKinName(
                                        blankToNull(request.getNextOfKinName()));
                        member.setNextOfKinPhone(
                                        blankToNull(request.getNextOfKinPhone()));
                        member.setNextOfKinRelationship(
                                        blankToNull(request.getNextOfKinRelationship()));

                        member = memberRepository.save(member);

                } else {

                        // --------------------------------------------------------
                        // Create NEW member
                        // --------------------------------------------------------

                        member = Member.builder()
                                        .memberNumber(uniqueMemberNumber())
                                        .firstName(firstName)
                                        .middleName(
                                                        blankToNull(request.getMiddleName()))
                                        .lastName(lastName)
                                        .phone(phone)
                                        .email(email)
                                        .nationalId(
                                                        blankToNull(request.getNationalId()))
                                        .address(
                                                        blankToNull(request.getAddress()))
                                        .occupation(
                                                        blankToNull(request.getOccupation()))
                                        .nextOfKinName(
                                                        blankToNull(request.getNextOfKinName()))
                                        .nextOfKinPhone(
                                                        blankToNull(request.getNextOfKinPhone()))
                                        .nextOfKinRelationship(
                                                        blankToNull(request.getNextOfKinRelationship()))
                                        .build();

                        member = memberRepository.save(member);
                }

                // ============================================================
                // 5. CREATE OR LINK USER ACCOUNT
                // ============================================================

                User user = userRepository.findByPhone(phone)
                                .orElse(null);

                if (user == null) {

                        // --------------------------------------------------------
                        // Create login account for member
                        // --------------------------------------------------------

                        String username = (firstName + " " + lastName).trim();

                        user = User.builder()
                                        .member(member)
                                        .username(username)
                                        .email(email)
                                        .phone(phone)

                                        // Temporary random password.
                                        // Login is OTP based.
                                        .passwordHash(
                                                        passwordEncoder.encode(
                                                                        UUID.randomUUID().toString()))

                                        .status(UserStatus.ACTIVE)
                                        .failedLoginAttempts(0)
                                        .build();

                        user = userRepository.save(user);

                } else {

                        // --------------------------------------------------------
                        // User already exists
                        // --------------------------------------------------------

                        if (user.getMember() == null) {

                                user.setMember(member);

                                userRepository.save(user);

                        } else if (!user.getMember().getId()
                                        .equals(member.getId())) {

                                throw new IllegalArgumentException(
                                                "This phone number is already associated with another member account.");
                        }
                }

                // ============================================================
                // 6. CREATE GROUP MEMBERSHIP
                // ============================================================

                LocalDate joinedDate = request.getJoinedDate() == null
                                ? LocalDate.now()
                                : request.getJoinedDate();

                MembershipType membershipType = request.getMembershipType() == null
                                ? MembershipType.ORDINARY
                                : request.getMembershipType();

                GroupMember groupMember = groupMemberRepository.save(
                                GroupMember.builder()
                                                .group(group)
                                                .member(member)
                                                .membershipNumber(
                                                                uniqueMembershipNumber(groupId))
                                                .joinedDate(joinedDate)
                                                .membershipType(
                                                                membershipType)
                                                .status(
                                                                MembershipStatus.ACTIVE)
                                                .build());

                // ============================================================
                // 7. CREATE GROUP ROLE
                // ============================================================

                GroupRole selectedRole = request.getRole() == null
                                ? GroupRole.MEMBER
                                : request.getRole();

                MemberRole memberRole = memberRoleRepository.save(
                                MemberRole.builder()
                                                .groupMember(groupMember)
                                                .role(selectedRole)
                                                .startDate(joinedDate)
                                                .endDate(null)
                                                .active(true)
                                .build());

                Role systemRole = roleRepository.findByName(selectedRole.name())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Role " + selectedRole.name() + " is not configured in the database."));
                if (userRoleRepository.findByUserPhoneWithPermissions(phone).stream()
                                .noneMatch(existing -> existing.getRole().getId().equals(systemRole.getId()))) {
                        userRoleRepository.save(UserRole.builder().user(user).role(systemRole).build());
                }

                // Invitation delivery is best-effort: a Pago outage must not roll back
                // a valid member/group registration. The account is OTP-login ready.
                smsNotificationService.send(phone,
                                "Karibu VIKOBA360! Umealikwa kujiunga na kikundi " + group.getName()
                                                + " kama " + selectedRole.name().replace('_', ' ')
                                                + ". Tumia namba yako ya simu kuingia.");

                // ============================================================
                // 8. RETURN RESPONSE
                // ============================================================

                return ApiResponse.success(
                                "Member added successfully to the group.",
                                mapToResponse(
                                                group,
                                                member,
                                                groupMember,
                                                memberRole));
        }

        public List<MemberRoleOptionResponse> getMemberRoles() {
                return roleRepository.findAll().stream()
                                .sorted(Comparator.comparing(Role::getName))
                                .map(role -> MemberRoleOptionResponse.builder()
                                                .value(GroupRole.valueOf(role.getName()))
                                                .label(formatRoleLabel(GroupRole.valueOf(role.getName())))
                                                .description(role.getDescription())
                                                .build())
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<MemberResponse> getMembersByGroup(Long groupId) {

                if (groupId == null) {
                        throw new IllegalArgumentException("groupId is required.");
                }

                return groupMemberRepository
                                .findByGroupIdAndStatus(
                                                groupId,
                                                MembershipStatus.ACTIVE)
                                .stream()
                                .map(groupMember -> {

                                        Member member = groupMember.getMember();

                                        List<MemberRole> roles = memberRoleRepository
                                                        .findByGroupMemberIdAndActiveTrue(
                                                                        groupMember.getId());

                                        GroupRole role = roles.isEmpty()
                                                        ? GroupRole.MEMBER
                                                        : roles.get(0).getRole();

                                        return mapToResponse(
                                                        groupMember.getGroup(),
                                                        member,
                                                        groupMember,
                                                        roles.isEmpty() ? null : roles.get(0),
                                                        role);
                                })
                                .toList();
        }

        private MemberResponse mapToResponse(VikobaGroup group, Member member, GroupMember groupMember,
                        MemberRole memberRole) {
                return mapToResponse(group, member, groupMember, memberRole,
                                memberRole != null ? memberRole.getRole() : GroupRole.MEMBER);
        }

        private MemberResponse mapToResponse(VikobaGroup group, Member member, GroupMember groupMember,
                        MemberRole memberRole, GroupRole role) {
                String fullName = String.join(" ",
                                safeTrim(member.getFirstName()),
                                safeTrim(member.getMiddleName()),
                                safeTrim(member.getLastName()))
                                .trim();

                return MemberResponse.builder()
                                .id(groupMember.getId())
                                .groupId(group.getId())
                                .memberId(member.getId())
                                .membershipNumber(groupMember.getMembershipNumber())
                                .memberNumber(member.getMemberNumber())
                                .firstName(member.getFirstName())
                                .middleName(member.getMiddleName())
                                .lastName(member.getLastName())
                                .fullName(fullName)
                                .phone(member.getPhone())
                                .email(member.getEmail())
                                .nationalId(member.getNationalId())
                                .address(member.getAddress())
                                .occupation(member.getOccupation())
                                .nextOfKinName(member.getNextOfKinName())
                                .nextOfKinPhone(member.getNextOfKinPhone())
                                .nextOfKinRelationship(member.getNextOfKinRelationship())
                                .membershipType(groupMember.getMembershipType())
                                .membershipStatus(groupMember.getStatus())
                                .role(role)
                                .joinedDate(groupMember.getJoinedDate())
                                .createdAt(groupMember.getCreatedAt() != null ? groupMember.getCreatedAt().toLocalDate()
                                                : null)
                                .build();
        }

        private String required(String value, String field) {
                if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException(field + " is required.");
                }
                return value.trim();
        }

        private String blankToNull(String value) {
                return value == null || value.isBlank() ? null : value.trim();
        }

        private String safeTrim(String value) {
                return value == null ? "" : value.trim();
        }

        private String uniqueMemberNumber() {
                return "MBR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        }

        private String uniqueMembershipNumber(Long groupId) {
                return "MEM-" + groupId + "-"
                                + UUID.randomUUID().toString().replace("-", "").substring(0, 6)
                                                .toUpperCase(Locale.ROOT);
        }

        private String formatRoleLabel(GroupRole role) {
                return switch (role) {
                        case GROUP_ADMIN -> "Group Admin";
                        case CHAIRPERSON -> "Chairperson";
                        case VICE_CHAIRPERSON -> "Vice Chairperson";
                        case SECRETARY -> "Secretary";
                        case TREASURER -> "Treasurer";
                        case ACCOUNTANT -> "Accountant";
                        case LOAN_OFFICER -> "Loan Officer";
                        case AUDITOR -> "Auditor";
                        case MEMBER -> "Member";
                };
        }

        private String roleDescription(GroupRole role) {
                return switch (role) {
                        case GROUP_ADMIN -> "Full administrative access for the group";
                        case CHAIRPERSON -> "Leads group meetings and approvals";
                        case VICE_CHAIRPERSON -> "Supports chairperson responsibilities";
                        case SECRETARY -> "Handles records and meeting notes";
                        case TREASURER -> "Manages contributions and cash flow";
                        case ACCOUNTANT -> "Records financial operations and accounting";
                        case LOAN_OFFICER -> "Reviews and manages loan applications";
                        case AUDITOR -> "Checks compliance and internal controls";
                        case MEMBER -> "Ordinary group member";
                };
        }
}
