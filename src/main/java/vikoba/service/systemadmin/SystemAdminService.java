package vikoba.service.systemadmin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import vikoba.service.common.enums.*;
import vikoba.service.common.repository.AuditLogRepository;
import vikoba.service.common.service.AuditLogService;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.*;
import vikoba.service.auth.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class SystemAdminService {
    private final SystemAdminAccess access;
    private final VikobaGroupRepository groups;
    private final GroupMemberRepository memberships;
    private final MemberRepository members;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final MemberRoleRepository roles;
    private final AuditLogRepository audits;
    private final AuditLogService auditService;

    public record Person(Long membershipId, Long memberId, String name, String phone, String email,
                         String membershipNumber, String status, Long groupId, String groupName,
                         List<String> roles, boolean eligibleForChair) {}
    public record GroupRow(Long id, String name, String code, String organization, String status,
                           String currency, long members, long activeMembers, List<Person> chairs) {}
    public record Overview(long groups, long activeGroups, long organizations, long members,
                           long memberships, long activeMemberships, long users, long leadershipIssues) {}
    public record AuditRow(Long id, String actor, String group, String action, String description,
                           java.time.LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public Overview overview() {
        access.requireSuperAdmin();
        return new Overview(groups.count(), groups.countActiveGroups(), organizations.count(), members.count(),
                memberships.count(), memberships.countActiveMembers(), users.count(), groups.countLeadershipIssues());
    }
    @Transactional(readOnly = true)
    public Page<GroupRow> groups(String search, int page, int size) {
        access.requireSuperAdmin();
        return groups.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
                search.trim(), search.trim(), page(page, size)).map(this::groupRow);
    }
    @Transactional(readOnly = true)
    public Page<Person> members(Long groupId, String search, int page, int size) {
        access.requireSuperAdmin();
        return memberships.searchSystemMembers(groupId, "%" + search.trim().toLowerCase(Locale.ROOT) + "%",
                page(page, size)).map(this::person);
    }
    @Transactional(readOnly = true)
    public Page<AuditRow> audit(int page, int size) {
        access.requireSuperAdmin();
        return audits.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))).map(a -> new AuditRow(a.getId(),
                a.getUser() == null ? "System" : a.getUser().getPhone(),
                a.getGroup() == null ? "System" : a.getGroup().getName(), a.getAction().name(),
                a.getDescription(), a.getCreatedAt()));
    }
    @Transactional
    public GroupRow changeChair(Long groupId, Long membershipId, Long expectedChairId, String reason) {
        access.requireSuperAdmin();
        if (membershipId == null || reason == null || reason.trim().length() < 5 || reason.length() > 500)
            throw new IllegalArgumentException("Choose a member and provide a reason of 5–500 characters.");
        VikobaGroup group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));
        GroupMember next = memberships.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found."));
        if (!next.getGroup().getId().equals(groupId) || next.getStatus() != MembershipStatus.ACTIVE
                || next.getMember().getStatus() != MemberStatus.ACTIVE)
            throw new IllegalArgumentException("The chair must be an active member of this group.");
        var current = chairRoles(groupId);
        Long actual = current.size() == 1 ? current.getFirst().getGroupMember().getId() : null;
        if (!Objects.equals(actual, expectedChairId))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Leadership changed. Refresh before trying again.");
        if (current.size() == 1 && Objects.equals(actual, membershipId)) return groupRow(group);
        String previous = current.isEmpty() ? "Unassigned" : current.stream()
                .map(r -> name(r.getGroupMember().getMember()) + " (#" + r.getGroupMember().getId() + ")")
                .distinct().collect(java.util.stream.Collectors.joining(", "));
        for (MemberRole role : current) {
            role.setActive(false);
            role.setEndDate(LocalDate.now());
        }
        roles.saveAll(current);
        roles.flush();
        roles.saveAndFlush(MemberRole.builder().groupMember(next).role(GroupRole.GROUP_CHAIRMAN)
                .startDate(LocalDate.now()).active(true).build());
        auditService.record(SecurityContextHolder.getContext().getAuthentication().getName(), groupId,
                AuditAction.UPDATE, "GROUP_CHAIR", groupId, null,
                "Chair changed from " + previous + " to " + name(next.getMember()) + " (#" + membershipId
                        + "). Reason: " + reason.trim());
        return groupRow(group);
    }
    private List<MemberRole> chairRoles(Long groupId) {
        return roles.findByGroupMemberGroupIdAndActiveTrue(groupId).stream()
                .filter(r -> isChair(r.getRole())).toList();
    }
    public static boolean isChair(GroupRole role) {
        return role == GroupRole.GROUP_CHAIRMAN || role == GroupRole.CHAIRPERSON;
    }
    private GroupRow groupRow(VikobaGroup g) {
        return new GroupRow(g.getId(), g.getName(), g.getCode(), g.getOrganization().getName(),
                g.getStatus().name(), g.getCurrency(), memberships.countByGroupId(g.getId()),
                memberships.countActiveMembersByGroupId(g.getId()), chairRoles(g.getId()).stream()
                .map(r -> person(r.getGroupMember())).toList());
    }
    private Person person(GroupMember gm) {
        Member m = gm.getMember();
        return new Person(gm.getId(), m.getId(), name(m), m.getPhone(), m.getEmail(), gm.getMembershipNumber(),
                gm.getStatus().name(), gm.getGroup().getId(), gm.getGroup().getName(),
                roles.findByGroupMemberIdAndActiveTrue(gm.getId()).stream().map(r -> r.getRole().name()).distinct().toList(),
                gm.getStatus() == MembershipStatus.ACTIVE && m.getStatus() == MemberStatus.ACTIVE);
    }
    private static String name(Member m) { return m.getFirstName() + " " + m.getLastName(); }
    private PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id"));
    }
}
