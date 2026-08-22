
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
        if (request == null || request.getOrganizationId() == null) {
            throw new IllegalArgumentException("organizationId is required.");
        }

        String groupName = required(request.getName(), "name");
        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization was not found."));
        User user = currentUser();

        if (user.getMember() == null || !memberRoleRepository
                .existsByGroupMemberMemberIdAndGroupMemberGroupOrganizationIdAndGroupMemberStatusAndRoleAndActiveTrue(
                        user.getMember().getId(), organization.getId(), MembershipStatus.ACTIVE,
                        GroupRole.GROUP_ADMIN)) {
            throw new IllegalStateException("Only an organization group administrator can create a group.");
        }

        String currency = request.getCurrency() == null || request.getCurrency().isBlank()
                ? "TZS"
                : request.getCurrency().trim().toUpperCase(Locale.ROOT);

        VikobaGroup group = groupRepository.save(VikobaGroup.builder()
                .organization(organization)
                .name(groupName)
                .code(uniqueCode(groupName))
                .phone(blankToNull(request.getPhone()))
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
}
