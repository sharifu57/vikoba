package vikoba.service.systemadmin;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vikoba.service.auth.entity.*;
import vikoba.service.auth.repository.*;
import vikoba.service.common.enums.*;
import vikoba.service.common.repository.AuditLogRepository;
import vikoba.service.organization.dto.MemberAccessRequest;
import vikoba.service.organization.dto.UpdateMembershipStatusRequest;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.*;
import vikoba.service.organization.service.MemberService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SystemAdminIntegrationTest {
    @Autowired SystemAdminService service;
    @Autowired SystemAdminAccess access;
    @Autowired MemberService memberService;
    @Autowired UserRepository users;
    @Autowired UserRoleRepository userRoles;
    @Autowired RoleRepository systemRoles;
    @Autowired MemberRepository members;
    @Autowired VikobaGroupRepository groups;
    @Autowired OrganizationRepository organizations;
    @Autowired GroupMemberRepository memberships;
    @Autowired MemberRoleRepository roles;
    @Autowired AuditLogRepository audits;
    @Autowired PlatformTransactionManager tx;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired org.springframework.security.web.FilterChainProxy securityFilter;
    @Autowired vikoba.service.config.JwtService jwt;
    record Fixture(String phone, Long group, Long oldChair, Long next, Long other) {}

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void login(String phone) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(phone, "unused", List.of()));
    }
    private Fixture fixture(boolean admin) {
        return new TransactionTemplate(tx).execute(status -> {
            String key = UUID.randomUUID().toString().substring(0, 8);
            Organization org = organizations.save(Organization.builder().name("Organization " + key).code(key).build());
            VikobaGroup group = groups.save(VikobaGroup.builder().organization(org).name("Group " + key).code(key).build());
            var gm = new java.util.ArrayList<GroupMember>();
            for (int i = 0; i < 3; i++) {
                Member member = members.save(Member.builder().memberNumber(key + i).firstName("Member" + i)
                        .lastName(key).phone(key + i).build());
                gm.add(memberships.save(GroupMember.builder().group(group).member(member)
                        .membershipNumber(key + i).joinedDate(LocalDate.now()).build()));
                roles.save(MemberRole.builder().groupMember(gm.get(i)).role(GroupRole.MEMBER)
                        .startDate(LocalDate.now()).build());
            }
            roles.save(MemberRole.builder().groupMember(gm.getFirst()).role(GroupRole.CHAIRPERSON)
                    .startDate(LocalDate.now()).build());
            roles.save(MemberRole.builder().groupMember(gm.getFirst()).role(GroupRole.ACCOUNTANT)
                    .startDate(LocalDate.now()).build());
            roles.save(MemberRole.builder().groupMember(gm.getFirst()).role(GroupRole.GROUP_ADMIN)
                    .startDate(LocalDate.now()).build());
            User user = users.save(User.builder().phone(key).username(key).member(gm.getFirst().getMember())
                    .passwordHash("test-unused").build());
            if (admin) userRoles.save(UserRole.builder().user(user)
                    .role(systemRoles.findByName("SUPER_ADMIN").orElseThrow()).build());
            return new Fixture(key, group.getId(), gm.get(0).getId(), gm.get(1).getId(), gm.get(2).getId());
        });
    }
    private List<MemberRole> active(Long groupId) {
        return new TransactionTemplate(tx).execute(status -> roles.findByGroupMemberGroupIdAndActiveTrue(groupId));
    }

    @Test void groupAdministratorDoesNotGainSystemAccess() {
        var f = fixture(false); login(f.phone());
        assertFalse(access.isSuperAdmin());
        assertThrows(AccessDeniedException.class, service::overview);
        assertThrows(AccessDeniedException.class, () -> service.groups("", 0, 20));
        assertThrows(AccessDeniedException.class, () -> service.members(null, "", 0, 20));
        assertThrows(AccessDeniedException.class, () -> service.audit(0, 20));
        assertThrows(AccessDeniedException.class, () -> service.changeChair(f.group(), f.next(), f.oldChair(), "New election"));
    }
    @Test void httpEndpointsRequireAuthenticationAndSystemRole() throws Exception {
        var f = fixture(false);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilter).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system-admin/overview"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        String token = jwt.generateAccessToken(f.phone());
        for (String path : List.of("overview", "groups", "members", "audit")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system-admin/" + path)
                    .header("Authorization", "Bearer " + token))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/system-admin/groups/" + f.group() + "/chair").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"membershipId\":" + f.next()
                        + ",\"expectedChairId\":" + f.oldChair() + ",\"reason\":\"New election\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void superAdminHttpReadsWorkWithoutGroupScopeAndInvalidReasonReturns400() throws Exception {
        var f = fixture(true);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilter).build();
        String token = jwt.generateAccessToken(f.phone());
        for (String path : List.of("overview", "groups", "members", "audit")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system-admin/" + path)
                    .header("Authorization", "Bearer " + token))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/system-admin/groups/" + f.group() + "/chair").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"membershipId\":" + f.next()
                        + ",\"expectedChairId\":" + f.oldChair() + ",\"reason\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void appointmentReplacesAliasPreservesOtherOfficesAndRecordsActorAndReason() {
        var f = fixture(true); login(f.phone());
        var result = service.changeChair(f.group(), f.next(), f.oldChair(), "New election result");
        assertEquals(1, result.chairs().size());
        assertEquals(f.next(), result.chairs().getFirst().membershipId());
        assertTrue(active(f.group()).stream().anyMatch(r -> r.getRole() == GroupRole.ACCOUNTANT));
        assertTrue(service.audit(0, 100).getContent().stream().anyMatch(a ->
                a.actor().equals(f.phone()) && a.description().contains("New election result")));
        assertEquals(3, service.members(f.group(), "", 0, 20).getTotalElements());
    }
    @Test void crossGroupAndSuspendedCandidatesAreRejectedWithoutRemovingChair() {
        var f = fixture(true); var foreign = fixture(false); login(f.phone());
        assertThrows(IllegalArgumentException.class, () -> service.changeChair(f.group(), foreign.next(), f.oldChair(), "New election"));
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            var candidate = memberships.findById(f.next()).orElseThrow();
            candidate.setStatus(MembershipStatus.SUSPENDED); memberships.save(candidate);
        });
        assertThrows(IllegalArgumentException.class, () -> service.changeChair(f.group(), f.next(), f.oldChair(), "New election"));
        assertEquals(1, active(f.group()).stream().filter(r -> SystemAdminService.isChair(r.getRole())).count());
    }
    @Test void staleAppointmentCannotOverwriteNewChair() {
        var f = fixture(true); login(f.phone());
        service.changeChair(f.group(), f.next(), f.oldChair(), "New election");
        var error = assertThrows(ResponseStatusException.class, () ->
                service.changeChair(f.group(), f.other(), f.oldChair(), "Stale election"));
        assertEquals(409, error.getStatusCode().value());
    }
    @Test void systemRoleDoesNotRequireAnyGroupMembership() {
        var f = fixture(true); login(f.phone());
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            var user = users.findByPhone(f.phone()).orElseThrow();
            user.setMember(null); users.save(user);
        });
        assertTrue(access.isSuperAdmin());
        assertTrue(service.overview().groups() > 0);
        assertEquals(3, service.members(f.group(), "", 0, 20).getTotalElements());
        assertEquals(1, service.changeChair(f.group(), f.next(), f.oldChair(), "New election").chairs().size());
    }
    @Test void ordinaryRoleEditorCannotAssignOrRemoveChair() {
        var f = fixture(false); login(f.phone());
        MemberAccessRequest request = new MemberAccessRequest();
        request.setRoles(List.of(GroupRole.MEMBER, GroupRole.GROUP_CHAIRMAN));
        assertThrows(AccessDeniedException.class, () -> memberService.updateMemberAccess(f.group(), f.next(), request));
        request.setRoles(List.of(GroupRole.MEMBER));
        assertThrows(AccessDeniedException.class, () -> memberService.updateMemberAccess(f.group(), f.oldChair(), request));
    }
    @Test void chairCannotBeSuspendedUntilReplacementAppointed() {
        var f = fixture(false); login(f.phone());
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest();
        request.setStatus(MembershipStatus.SUSPENDED);
        assertThrows(IllegalArgumentException.class, () -> memberService.updateMembershipStatus(f.group(), f.oldChair(), request));
    }
    @Test void simultaneousAppointmentsLeaveExactlyOneChair() throws Exception {
        var f = fixture(true);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(f.next(), f.other()).stream().map(candidate -> executor.submit(() -> {
                login(f.phone()); start.await();
                try { service.changeChair(f.group(), candidate, f.oldChair(), "Concurrent election"); return true; }
                catch (ResponseStatusException error) { assertEquals(409, error.getStatusCode().value()); return false; }
                finally { SecurityContextHolder.clearContext(); }
            })).toList();
            start.countDown();
            int success = 0;
            for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
        }
        assertEquals(1, active(f.group()).stream().filter(r -> SystemAdminService.isChair(r.getRole())).count());
    }
    @Test void legacyDuplicateChairsCanBeRepairedToOne() {
        var f = fixture(true); login(f.phone());
        new TransactionTemplate(tx).executeWithoutResult(status -> roles.save(MemberRole.builder()
                .groupMember(memberships.findById(f.next()).orElseThrow()).role(GroupRole.GROUP_CHAIRMAN)
                .startDate(LocalDate.now()).build()));
        var result = service.changeChair(f.group(), f.other(), null, "Resolve duplicate leadership");
        assertEquals(1, result.chairs().size());
        assertEquals(f.other(), result.chairs().getFirst().membershipId());
    }
}
