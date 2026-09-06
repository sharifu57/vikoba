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
import vikoba.service.auth.entity.Permission;
import vikoba.service.auth.repository.PermissionRepository;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.common.enums.MembershipType;
import vikoba.service.common.enums.UserStatus;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.AddMemberRequest;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.organization.dto.MemberRoleOptionResponse;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.MemberPermissionRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.notification.SmsNotificationService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import vikoba.service.organization.dto.MemberAccessRequest;

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
        private final PermissionRepository permissionRepository;
        private final MemberPermissionRepository memberPermissionRepository;
        private final GroupAuthorizationService authorizationService;
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
                authorizationService.requirePermission(groupId, "USER_ROLE_MANAGE");

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

                Set<GroupRole> initialRoles = new LinkedHashSet<>();
                initialRoles.add(GroupRole.MEMBER);
                initialRoles.add(selectedRole);
                List<MemberRole> assignedRoles = assignRoles(groupMember, user, initialRoles, joinedDate);
                MemberRole memberRole = assignedRoles.stream().filter(role -> role.getRole() == selectedRole).findFirst()
                                .orElse(assignedRoles.getFirst());

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
                                .filter(role -> isGroupRole(role.getName()))
                                .sorted(Comparator.comparing(Role::getName))
                                .map(role -> MemberRoleOptionResponse.builder()
                                                .value(GroupRole.valueOf(role.getName()))
                                                .label(formatRoleLabel(GroupRole.valueOf(role.getName())))
                                                .description(role.getDescription())
                                                .build())
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<String> getPermissions() {
                return permissionRepository.findAll().stream().map(Permission::getName).sorted().toList();
        }

        @Transactional
        public MemberResponse updateMemberAccess(Long groupId, Long groupMemberId, MemberAccessRequest request) {
                authorizationService.requirePermission(groupId, "USER_ROLE_MANAGE");
                GroupMember membership = groupMemberRepository.findById(groupMemberId)
                                .orElseThrow(() -> new IllegalArgumentException("Group member not found."));
                if (!membership.getGroup().getId().equals(groupId)) throw new IllegalArgumentException("Member does not belong to this group.");
                Set<GroupRole> requestedRoles = request == null || request.getRoles() == null
                                ? new LinkedHashSet<>() : new LinkedHashSet<>(request.getRoles());
                requestedRoles.add(GroupRole.MEMBER);
                if (requestedRoles.contains(GroupRole.GROUP_ADMIN)
                                && !authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN)) {
                        throw new org.springframework.security.access.AccessDeniedException("Only a Group Admin can grant Group Admin access");
                }
                User user = userRepository.findByPhone(membership.getMember().getPhone()).orElse(null);
                assignRoles(membership, user, requestedRoles, LocalDate.now());
                memberPermissionRepository.deleteByGroupMemberId(groupMemberId);
                for (String permissionName : request == null || request.getPermissions() == null ? List.<String>of() : request.getPermissions()) {
                        Permission permission = permissionRepository.findByName(permissionName.trim().toUpperCase())
                                        .orElseThrow(() -> new IllegalArgumentException("Permission " + permissionName + " is not configured."));
                        memberPermissionRepository.save(MemberPermission.builder().groupMember(membership).permission(permission).build());
                }
                return toResponse(membership);
        }

        @Transactional(readOnly = true)
        public List<MemberResponse> getMembersByGroup(Long groupId) {

                if (groupId == null) {
                        throw new IllegalArgumentException("groupId is required.");
                }
                authorizationService.requireMembership(groupId);

                return groupMemberRepository
                                .findByGroupIdAndStatus(
                                                groupId,
                                                MembershipStatus.ACTIVE)
                                .stream()
                                .map(this::toResponse)
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
                                .roles(memberRoleRepository.findByGroupMemberIdAndActiveTrue(groupMember.getId()).stream()
                                                .map(MemberRole::getRole).distinct().toList())
                                .permissions(effectivePermissions(groupMember))
                                .joinedDate(groupMember.getJoinedDate())
                                .createdAt(groupMember.getCreatedAt() != null ? groupMember.getCreatedAt().toLocalDate()
                                                : null)
                                .build();
        }

        private MemberResponse toResponse(GroupMember groupMember) {
                List<MemberRole> roles = memberRoleRepository.findByGroupMemberIdAndActiveTrue(groupMember.getId());
                GroupRole primary = primaryRole(roles);
                return mapToResponse(groupMember.getGroup(), groupMember.getMember(), groupMember,
                                roles.isEmpty() ? null : roles.getFirst(), primary);
        }

        private List<MemberRole> assignRoles(GroupMember membership, User user, Set<GroupRole> requested, LocalDate startDate) {
                List<MemberRole> current = memberRoleRepository.findByGroupMemberIdAndActiveTrue(membership.getId());
                current.stream().filter(existing -> !requested.contains(existing.getRole())).forEach(existing -> {
                        existing.setActive(false); existing.setEndDate(startDate); memberRoleRepository.save(existing);
                });
                for (GroupRole role : requested) {
                        if (current.stream().noneMatch(existing -> existing.getRole() == role)) {
                                memberRoleRepository.save(MemberRole.builder().groupMember(membership).role(role)
                                                .startDate(startDate).active(true).build());
                        }
                        if (user != null) roleRepository.findByName(role.name()).ifPresent(systemRole -> {
                                if (userRoleRepository.findByUserPhoneWithPermissions(user.getPhone()).stream()
                                                .noneMatch(existing -> existing.getRole().getId().equals(systemRole.getId()))) {
                                        userRoleRepository.save(UserRole.builder().user(user).role(systemRole).build());
                                }
                        });
                }
                return memberRoleRepository.findByGroupMemberIdAndActiveTrue(membership.getId());
        }

        private List<String> effectivePermissions(GroupMember membership) {
                Set<String> permissions = new LinkedHashSet<>();
                List<MemberRole> roles = memberRoleRepository.findByGroupMemberIdAndActiveTrue(membership.getId());
                if (roles.stream().anyMatch(role -> role.getRole() == GroupRole.GROUP_ADMIN)) {
                        return permissionRepository.findAll().stream().map(Permission::getName).sorted().toList();
                }
                roles.forEach(role -> roleRepository.findByNameWithPermissions(role.getRole().name())
                                .ifPresent(systemRole -> systemRole.getPermissions().forEach(permission -> permissions.add(permission.getName()))));
                memberPermissionRepository.findByGroupMemberId(membership.getId())
                                .forEach(grant -> permissions.add(grant.getPermission().getName()));
                return permissions.stream().sorted().toList();
        }

        private GroupRole primaryRole(List<MemberRole> roles) {
                List<GroupRole> order = List.of(GroupRole.GROUP_ADMIN, GroupRole.GROUP_CHAIRMAN, GroupRole.ACCOUNTANT, GroupRole.TREASURER, GroupRole.SECRETARY, GroupRole.LOAN_OFFICER, GroupRole.AUDITOR, GroupRole.MEMBER);
                return order.stream().filter(candidate -> roles.stream().anyMatch(role -> role.getRole() == candidate)).findFirst().orElse(GroupRole.MEMBER);
        }

        private boolean isGroupRole(String value) { try { GroupRole.valueOf(value); return true; } catch (IllegalArgumentException ex) { return false; } }

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
                        case GROUP_CHAIRMAN -> "Group Chairman (Mwenyekiti)";
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
                        case GROUP_ADMIN -> "Full administration access for this group";
                        case GROUP_CHAIRMAN -> "Leads the group and coordinates approvals";
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
