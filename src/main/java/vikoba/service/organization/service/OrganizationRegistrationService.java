package vikoba.service.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.common.enums.MembershipType;
import vikoba.service.organization.dto.OrganizationRegistrationRequest;
import vikoba.service.organization.dto.OrganizationRegistrationResponse;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.entity.Member;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.organization.entity.Organization;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.MemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.OrganizationRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationRegistrationService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final VikobaGroupRepository groupRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final MemberRepository memberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRoleRepository memberRoleRepository;

    @Transactional
    public OrganizationRegistrationResponse register(OrganizationRegistrationRequest request) {
        String organizationName = required(request.getOrganizationName(), "organizationName");
        String groupName = required(request.getGroupName(), "groupName");

        String phone = authenticatedPhone();
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found."));

        Organization organization = organizationRepository.save(Organization.builder()
                .name(organizationName)
                .code(uniqueCode(organizationName, "ORG"))
                .email(user.getEmail())
                .phone(user.getPhone())
                .build());

        VikobaGroup group = groupRepository.save(VikobaGroup.builder()
                .organization(organization)
                .name(groupName)
                .code(uniqueCode(groupName, "GRP"))
                .formationDate(LocalDate.now())
                .currency("TZS")
                .build());

        groupSettingsRepository.save(GroupSettings.builder()
                .group(group)
                .build());

        Member member = user.getMember();
        if (member == null) {
            member = memberRepository.save(buildMember(user));
            user.setMember(member);
            userRepository.save(user);
        }

        GroupMember groupMember = groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .member(member)
                .membershipNumber(uniqueNumber("MEM"))
                .joinedDate(LocalDate.now())
                .membershipType(MembershipType.FOUNDING)
                .status(MembershipStatus.ACTIVE)
                .build());

        memberRoleRepository.save(MemberRole.builder()
                .groupMember(groupMember)
                .role(GroupRole.GROUP_ADMIN)
                .startDate(LocalDate.now())
                .active(true)
                .build());

        return new OrganizationRegistrationResponse(
                organization.getId(), group.getId(), member.getId(),
                organization.getCode(), group.getCode(), GroupRole.GROUP_ADMIN.name());
    }

    private Member buildMember(User user) {
        String fullName = user.getUsername() == null || user.getUsername().isBlank()
                ? "Group Owner"
                : user.getUsername().trim();
        String[] nameParts = fullName.split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : firstName;

        return Member.builder()
                .memberNumber(uniqueNumber("MBR"))
                .firstName(firstName)
                .lastName(lastName)
                .phone(user.getPhone())
                .email(user.getEmail())
                .build();
    }

    private String authenticatedPhone() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authentication is required to register an organization.");
        }
        return authentication.getName();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private String uniqueCode(String value, String prefix) {
        String normalized = value.replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase(Locale.ROOT);
        String base = normalized.length() > 12 ? normalized.substring(0, 12) : normalized;
        return prefix + "-" + base + "-" + randomSuffix();
    }

    private String uniqueNumber(String prefix) {
        return prefix + "-" + randomSuffix();
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }
}