package vikoba.service.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.common.enums.MembershipType;
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

    @Transactional
    public ApiResponse<MemberResponse> addMemberToGroup(AddMemberRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Member details are required.");
        }

        Long groupId = request.getGroupId();
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }

        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));

        String firstName = required(request.getFirstName(), "firstName");
        String lastName = required(request.getLastName(), "lastName");
        String phone = required(request.getPhone(), "phone");

        Member existingMember = memberRepository.findByPhone(phone).orElse(null);
        if (existingMember != null
                && groupMemberRepository.existsByGroupIdAndMemberId(groupId, existingMember.getId())) {
            throw new IllegalArgumentException("This member already exists in this group.");
        }

        GroupRole selectedRole = request.getRole() == null ? GroupRole.MEMBER : request.getRole();

        Member member = existingMember;
        if (member == null) {
            member = memberRepository.save(Member.builder()
                    .memberNumber(uniqueMemberNumber())
                    .firstName(firstName)
                    .middleName(blankToNull(request.getMiddleName()))
                    .lastName(lastName)
                    .phone(phone)
                    .email(blankToNull(request.getEmail()))
                    .nationalId(blankToNull(request.getNationalId()))
                    .address(blankToNull(request.getAddress()))
                    .occupation(blankToNull(request.getOccupation()))
                    .nextOfKinName(blankToNull(request.getNextOfKinName()))
                    .nextOfKinPhone(blankToNull(request.getNextOfKinPhone()))
                    .nextOfKinRelationship(blankToNull(request.getNextOfKinRelationship()))
                    .build());
        } else {
            member.setFirstName(firstName);
            member.setMiddleName(blankToNull(request.getMiddleName()));
            member.setLastName(lastName);
            member.setPhone(phone);
            member.setEmail(blankToNull(request.getEmail()));
            member.setNationalId(blankToNull(request.getNationalId()));
            member.setAddress(blankToNull(request.getAddress()));
            member.setOccupation(blankToNull(request.getOccupation()));
            member.setNextOfKinName(blankToNull(request.getNextOfKinName()));
            member.setNextOfKinPhone(blankToNull(request.getNextOfKinPhone()));
            member.setNextOfKinRelationship(blankToNull(request.getNextOfKinRelationship()));
            member = memberRepository.save(member);
        }

        LocalDate joinedDate = request.getJoinedDate() == null ? LocalDate.now() : request.getJoinedDate();

        GroupMember groupMember = groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .member(member)
                .membershipNumber(uniqueMembershipNumber(groupId))
                .joinedDate(joinedDate)
                .membershipType(
                        request.getMembershipType() == null ? MembershipType.ORDINARY : request.getMembershipType())
                .status(MembershipStatus.ACTIVE)
                .build());

        MemberRole memberRole = memberRoleRepository.save(MemberRole.builder()
                .groupMember(groupMember)
                .role(selectedRole)
                .startDate(joinedDate)
                .endDate(null)
                .active(true)
                .build());

        return ApiResponse.success("Member added successfully to the group.",
                mapToResponse(group, member, groupMember, memberRole));
    }

    public List<MemberRoleOptionResponse> getMemberRoles() {
        List<MemberRoleOptionResponse> roles = new ArrayList<>();
        for (GroupRole role : GroupRole.values()) {
            roles.add(MemberRoleOptionResponse.builder()
                    .value(role)
                    .label(formatRoleLabel(role))
                    .description(roleDescription(role))
                    .build());
        }
        roles.sort(Comparator.comparing(MemberRoleOptionResponse::getLabel));
        return roles;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembersByGroup(Long groupId) {

        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }

        return groupMemberRepository
                .findByGroupIdAndStatus(
                        groupId,
                        MembershipStatus.ACTIVE
                )
                .stream()
                .map(groupMember -> {

                    Member member = groupMember.getMember();

                    List<MemberRole> roles =
                            memberRoleRepository
                                    .findByGroupMemberIdAndActiveTrue(
                                            groupMember.getId()
                                    );

                    GroupRole role =
                            roles.isEmpty()
                                    ? GroupRole.MEMBER
                                    : roles.get(0).getRole();

                    return mapToResponse(
                            groupMember.getGroup(),
                            member,
                            groupMember,
                            roles.isEmpty() ? null : roles.get(0),
                            role
                    );
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
                .createdAt(groupMember.getCreatedAt() != null ? groupMember.getCreatedAt().toLocalDate() : null)
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
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
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
