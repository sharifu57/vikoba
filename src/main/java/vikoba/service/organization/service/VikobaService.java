
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
import vikoba.service.organization.dto.VikobaGroupCreateRequest;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;
import vikoba.service.organization.dto.GroupProfileSettingsRequest;
import vikoba.service.organization.dto.GroupSettingsRequest;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.entity.Organization;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.OrganizationRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VikobaService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final VikobaGroupRepository groupRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final MemberRoleRepository memberRoleRepository;

    @Transactional
    public VikobaGroupCreateResponse createGroup(VikobaGroupCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Group details are required.");
        }

        String groupName = required(request.getName(), "name");
        User user = currentUser();

        Organization organization = resolveOrganizationForUser(user);

        String currency = request.getCurrency() == null || request.getCurrency().isBlank()
                ? "TZS"
                : request.getCurrency().trim().toUpperCase(Locale.ROOT);

        VikobaGroup group = groupRepository.save(VikobaGroup.builder()
                .organization(organization)
                .name(groupName)
                .code(uniqueCode(groupName))
                .phone(blankToNull(request.getPhone()))
                .email(blankToNull(request.getEmail()))
                .description(blankToNull(request.getDescription()))
                .meetingFrequency(request.getMeetingFrequency())
                .meetingDay(blankToNull(request.getMeetingDay()))
                .formationDate(LocalDate.now())
                .currency(currency)
                .build());

        createGroupSettings(group, request.getSettings());

        return new VikobaGroupCreateResponse(
                organization.getId(), group.getId(), organization.getName(),
                group.getName(), group.getCode(), group.getCurrency());
    }

    @Transactional
    public VikobaGroupCreateResponse createGroupWithSettings(GroupProfileSettingsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Group details are required.");
        }

        VikobaGroupCreateRequest createRequest = new VikobaGroupCreateRequest();
        createRequest.setName(request.getName());
        createRequest.setPhone(request.getPhone());
        createRequest.setEmail(request.getEmail());
        createRequest.setCurrency(request.getCurrency());
        createRequest.setSettings(request.getSettings());

        return createGroup(createRequest);
    }

    @Transactional
    public VikobaGroupCreateResponse saveGroupDetailsAndSettings(Long groupId, GroupProfileSettingsRequest request) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Group details are required.");
        }

        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group was not found."));
        assertGroupAdmin(group.getOrganization().getId());

        if (request.getName() != null && !request.getName().isBlank()) {
            group.setName(request.getName().trim());
        }
        if (request.getPhone() != null) {
            group.setPhone(blankToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            group.setEmail(blankToNull(request.getEmail()));
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            group.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        }
        groupRepository.save(group);

        GroupSettings settings = groupSettingsRepository.findByGroupId(groupId)
                .orElseGet(() -> GroupSettings.builder().group(group).build());
        if (request.getSettings() != null) {
            applySettings(settings, request.getSettings());
            groupSettingsRepository.save(settings);
        }

        return new VikobaGroupCreateResponse(
                group.getOrganization().getId(),
                group.getId(),
                group.getOrganization().getName(),
                group.getName(),
                group.getCode(),
                group.getCurrency());
    }

    @Transactional
    public VikobaGroupCreateResponse updateGroupAndSettings(Long groupId, GroupProfileSettingsRequest request) {
        return saveGroupDetailsAndSettings(groupId, request);
    }

    @Transactional
    public GroupSettings createGroupSettings(Long groupId, GroupSettingsRequest request) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Settings are required.");
        }

        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group was not found."));
        assertGroupAdmin(group.getOrganization().getId());

        GroupSettings settings = groupSettingsRepository.findByGroupId(groupId)
                .orElseGet(() -> GroupSettings.builder().group(group).build());
        applySettings(settings, request);
        return groupSettingsRepository.save(settings);
    }

    private void createGroupSettings(VikobaGroup group, GroupSettingsRequest request) {
        GroupSettings settings = GroupSettings.builder().group(group).build();
        if (request != null) {
            applySettings(settings, request);
        }
        groupSettingsRepository.save(settings);
    }

    private void applySettings(GroupSettings settings, GroupSettingsRequest request) {
        if (request.getMinimumContribution() != null) {
            settings.setMinimumContribution(nonNegative(request.getMinimumContribution(), "minimumContribution"));
        }
        if (request.getMaximumContribution() != null) {
            settings.setMaximumContribution(nonNegative(request.getMaximumContribution(), "maximumContribution"));
        }
        if (request.getSharePrice() != null) {
            settings.setSharePrice(nonNegative(request.getSharePrice(), "sharePrice"));
        }
        if (request.getMaximumSharesPerMember() != null) {
            settings.setMaximumSharesPerMember(positive(request.getMaximumSharesPerMember(), "maximumSharesPerMember"));
        }
        if (request.getLoanMultiplier() != null) {
            settings.setLoanMultiplier(nonNegative(request.getLoanMultiplier(), "loanMultiplier"));
        }
        if (request.getDefaultInterestRate() != null) {
            settings.setDefaultInterestRate(nonNegative(request.getDefaultInterestRate(), "defaultInterestRate"));
        }
        if (request.getDefaultLoanDurationMonths() != null) {
            settings.setDefaultLoanDurationMonths(
                    positive(request.getDefaultLoanDurationMonths(), "defaultLoanDurationMonths"));
        }
        if (request.getLatePaymentFine() != null) {
            settings.setLatePaymentFine(nonNegative(request.getLatePaymentFine(), "latePaymentFine"));
        }
        if (settings.getMaximumContribution() != null
                && settings.getMaximumContribution().compareTo(settings.getMinimumContribution()) < 0) {
            throw new IllegalArgumentException("maximumContribution cannot be less than minimumContribution.");
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative.");
        }
        return value;
    }

    private Integer positive(Integer value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be greater than zero.");
        }
        return value;
    }

    private void assertGroupAdmin(Long organizationId) {
        User user = currentUser();
        if (user.getMember() == null || !memberRoleRepository
                .existsByGroupMemberMemberIdAndGroupMemberGroupOrganizationIdAndGroupMemberStatusAndRoleAndActiveTrue(
                        user.getMember().getId(), organizationId, MembershipStatus.ACTIVE,
                        GroupRole.GROUP_ADMIN)) {
            throw new IllegalStateException("Only an organization group administrator can manage group settings.");
        }
    }

    private Organization resolveOrganizationForUser(User user) {
        if (user == null) {
            throw new IllegalStateException("Authenticated user was not found.");
        }

        if (user.getMember() != null) {
            return groupRepository.findAll().stream()
                    .filter(group -> group.getMembers() != null)
                    .filter(group -> group.getMembers().stream()
                            .anyMatch(member -> member.getMember() != null
                                    && user.getMember().getId() != null
                                    && user.getMember().getId().equals(member.getMember().getId())))
                    .map(VikobaGroup::getOrganization)
                    .findFirst()
                    .orElseGet(() -> createDefaultOrganizationForUser(user));
        }

        return createDefaultOrganizationForUser(user);
    }

    private Organization createDefaultOrganizationForUser(User user) {
        String baseName = (user.getUsername() == null || user.getUsername().isBlank())
                ? "My Organization"
                : user.getUsername().trim();

        String organizationName = baseName + " Group";
        return organizationRepository.save(Organization.builder()
                .name(organizationName)
                .code(uniqueOrganizationCode(organizationName))
                .phone(blankToNull(user.getPhone()))
                .email(blankToNull(user.getEmail()))
                .build());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authentication is required.");
        }
        return userRepository.findByPhone(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found."));
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

    private String uniqueCode(String groupName) {
        String normalized = groupName.replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase(Locale.ROOT);
        String base = normalized.length() > 12 ? normalized.substring(0, 12) : normalized;
        return "GRP-" + base + "-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String uniqueOrganizationCode(String organizationName) {
        String normalized = organizationName.replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase(Locale.ROOT);
        String base = normalized.length() > 12 ? normalized.substring(0, 12) : normalized;
        return "ORG-" + base + "-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
