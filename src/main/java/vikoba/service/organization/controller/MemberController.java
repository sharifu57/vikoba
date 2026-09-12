package vikoba.service.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.AddMemberRequest;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.organization.dto.MemberRoleOptionResponse;
import vikoba.service.organization.dto.MemberAccessRequest;
import vikoba.service.organization.dto.UpdateMemberRequest;
import vikoba.service.organization.dto.UpdateMembershipStatusRequest;
import vikoba.service.organization.service.MemberService;
import vikoba.service.member360.service.Member360Service;
import vikoba.service.member360.dto.Member360Response;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberController {
    private final MemberService memberService;
    private final Member360Service member360Service;

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<MemberResponse>> addMember(@RequestBody AddMemberRequest request) {
        try {
            return ResponseEntity.ok(memberService.addMemberToGroup(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/members/roles")
    public ResponseEntity<ApiResponse<List<MemberRoleOptionResponse>>> getMemberRoles() {
        return ResponseEntity
                .ok(ApiResponse.success("Member roles retrieved successfully.", memberService.getMemberRoles()));
    }

    @GetMapping("/members/permissions")
    public ResponseEntity<ApiResponse<List<String>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully.", memberService.getPermissions()));
    }

    @PutMapping("/members/group/{groupId}/{groupMemberId}/access")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMemberAccess(@PathVariable Long groupId,
            @PathVariable Long groupMemberId, @RequestBody MemberAccessRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Member access updated successfully.",
                    memberService.updateMemberAccess(groupId, groupMemberId, request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/members/group/{groupId}/{groupMemberId}")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(@PathVariable Long groupId,
            @PathVariable Long groupMemberId, @RequestBody UpdateMemberRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Member updated successfully.",
                    memberService.updateMemberProfile(groupId, groupMemberId, request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/members/group/{groupId}/{groupMemberId}/status")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMembershipStatus(@PathVariable Long groupId,
            @PathVariable Long groupMemberId, @RequestBody UpdateMembershipStatusRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Membership status updated successfully.",
                    memberService.updateMembershipStatus(groupId, groupMemberId, request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/members/group/{groupId}")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembersByGroup(@PathVariable Long groupId) {
        try {
            return ResponseEntity.ok(
                    ApiResponse.success("Members retrieved successfully.", memberService.getMembersByGroup(groupId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/members/{id}/360")
    public ResponseEntity<ApiResponse<Member360Response>> getMember360(@PathVariable("id") Long groupMemberId) {
        try {
            Member360Response resp = member360Service.getMember360(groupMemberId);
            return ResponseEntity.ok(ApiResponse.success("Member details retrieved successfully.", resp));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
