package vikoba.service.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.auth.repository.RoleRepository;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.organization.entity.WorkflowNode;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.MemberPermissionRepository;
import vikoba.service.organization.repository.WorkflowNodeRepository;

import java.util.List;

/**
 * Evaluates access in the context of a single group. System roles alone are not
 * sufficient: the caller must also be an active member of that group.
 */
@Service
@RequiredArgsConstructor
public class GroupAuthorizationService {
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RoleRepository roleRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final MemberPermissionRepository memberPermissionRepository;

    @Transactional(readOnly = true)
    public void requireMembership(Long groupId) {
        currentRoles(groupId);
    }

    @Transactional(readOnly = true)
    public void requirePermission(Long groupId, String permission) {
        if (!hasPermission(groupId, permission)) {
            throw new AccessDeniedException("You do not have " + permission + " permission for this group");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long groupId, String permission) {
        if (currentRoles(groupId).stream().anyMatch(memberRole -> memberRole.getRole() == GroupRole.GROUP_ADMIN)) {
            return true;
        }
        GroupMember membership = currentMembership(groupId);
        return currentRoles(groupId).stream().anyMatch(memberRole -> roleHasPermission(memberRole, permission))
                || memberPermissionRepository.findByGroupMemberId(membership.getId()).stream()
                        .anyMatch(grant -> grant.getPermission().getName().equalsIgnoreCase(permission));
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Long groupId, GroupRole role) {
        return currentRoles(groupId).stream().anyMatch(memberRole -> memberRole.getRole() == role);
    }

    /** Members may act on their own membership; delegated staff need the supplied permission. */
    @Transactional(readOnly = true)
    public void requireSelfOrPermission(Long groupId, Long groupMemberId, String permission) {
        GroupMember currentMembership = currentMembership(groupId);
        if (currentMembership.getId().equals(groupMemberId) || hasPermission(groupId, permission)) {
            return;
        }
        throw new AccessDeniedException("You may only perform this action for your own membership");
    }

    /** A member may always see their own dashboard; group-wide data needs elevated dashboard access. */
    @Transactional(readOnly = true)
    public void requireSelfOrGroupDashboardAccess(Long groupId, Long groupMemberId) {
        GroupMember currentMembership = currentMembership(groupId);
        if (currentMembership.getId().equals(groupMemberId)) {
            return;
        }
        requireGroupDashboardAccess(groupId);
    }

    /**
     * Group admins have full access. Other office holders must also retain a
     * dashboard/report permission; an explicitly granted DASHBOARD_GROUP_VIEW
     * permission supports custom role configurations.
     */
    @Transactional(readOnly = true)
    public void requireGroupDashboardAccess(Long groupId) {
        List<MemberRole> roles = currentRoles(groupId);
        if (roles.stream().anyMatch(role -> role.getRole() == GroupRole.GROUP_ADMIN)) {
            return;
        }

        boolean hasOfficeRole = roles.stream().anyMatch(role -> role.getRole() != GroupRole.MEMBER);
        if ((hasOfficeRole && hasPermission(groupId, "REPORT_VIEW"))
                || hasPermission(groupId, "DASHBOARD_GROUP_VIEW")) {
            return;
        }

        throw new AccessDeniedException("You do not have access to the group dashboard");
    }

    /**
     * Evaluates a dynamically configured workflow action. An admin may always
     * act; otherwise the member must match one active node for the action and
     * hold that node's permission. Actions without nodes fall back to the
     * supplied permission, which keeps existing flows working during rollout.
     */
    @Transactional(readOnly = true)
    public void requireWorkflowAction(Long groupId, String actionKey, String fallbackPermission) {
        List<MemberRole> roles = currentRoles(groupId);
        if (roles.stream().anyMatch(role -> role.getRole() == GroupRole.GROUP_ADMIN)) {
            return;
        }

        List<WorkflowNode> nodes = workflowNodeRepository
                .findByGroupIdAndActionKeyAndActiveTrueOrderByStepOrderAsc(groupId, actionKey.trim().toUpperCase());
        boolean allowed = nodes.isEmpty()
                ? roles.stream().anyMatch(role -> roleHasPermission(role, fallbackPermission))
                : nodes.stream().anyMatch(node -> roles.stream().anyMatch(role -> matchesNode(groupId, role, node)));
        if (!allowed) {
            throw new AccessDeniedException("You are not assigned to this workflow action for this group");
        }
    }

    private boolean matchesNode(Long groupId, MemberRole memberRole, WorkflowNode node) {
        return (node.getRequiredRole() == null || node.getRequiredRole() == memberRole.getRole())
                && hasPermission(groupId, node.getRequiredPermission());
    }

    private boolean roleHasPermission(MemberRole memberRole, String permission) {
        return roleRepository.findByNameWithPermissions(memberRole.getRole().name())
                .map(role -> role.getPermissions().stream()
                        .anyMatch(item -> item.getName().equalsIgnoreCase(permission)))
                .orElse(false);
    }

    private List<MemberRole> currentRoles(Long groupId) {
        return memberRoleRepository.findByGroupMemberIdAndActiveTrue(currentMembership(groupId).getId());
    }

    private GroupMember currentMembership(Long groupId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication is required");
        }
        var user = userRepository.findByPhone(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
        if (user.getMember() == null) {
            throw new AccessDeniedException("User is not a group member");
        }
        GroupMember membership = groupMemberRepository.findByGroupIdAndMemberId(groupId, user.getMember().getId())
                .orElseThrow(() -> new AccessDeniedException("User is not a member of this group"));
        if (membership.getStatus() != vikoba.service.common.enums.MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("Your group membership is not active");
        }
        return membership;
    }
}
