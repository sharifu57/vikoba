package vikoba.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MemberRoleSeeder implements CommandLineRunner {
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRoleRepository memberRoleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<GroupMember> activeMembers = groupMemberRepository.findAll().stream()
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .toList();

        for (GroupMember groupMember : activeMembers) {
            boolean hasActiveRole = !memberRoleRepository.findByGroupMemberIdAndActiveTrue(groupMember.getId())
                    .isEmpty();

            if (!hasActiveRole) {
                memberRoleRepository.save(MemberRole.builder()
                        .groupMember(groupMember)
                        .role(GroupRole.MEMBER)
                        .startDate(groupMember.getJoinedDate() != null ? groupMember.getJoinedDate() : LocalDate.now())
                        .active(true)
                        .build());
            }
        }
    }
}
