
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
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.VikobaGroupCreateRequest;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;
import vikoba.service.organization.dto.GroupProfileSettingsRequest;
import vikoba.service.organization.dto.GroupSettingsRequest;
import vikoba.service.organization.dto.GroupWithSettingsResponse;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.entity.Organization;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRepository;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.repository.OrganizationRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import vikoba.service.organization.entity.Member;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.common.enums.MembershipType;

@Service
@RequiredArgsConstructor
public class VikobaService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final VikobaGroupRepository groupRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberRepository memberRepository;
    private final GroupMemberRepository groupMemberRepository;

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

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Group start date and end date are required.");
        }

        validateGroupCycleDates(startDate, endDate);

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
                .startDate(startDate)
                .endDate(endDate)
                .currency(currency)
                .build());

        createGroupSettings(group, request.getSettings());

        // Ensure the creating user is a member of the new group and has an admin role
        try {
            Member member = user.getMember();
            if (member == null) {
                member = memberRepository.save(buildMemberFromUser(user));
                user.setMember(member);
                userRepository.save(user);
            }

            // create group member link if not exists
            boolean exists = groupMemberRepository.existsByGroupIdAndMemberId(group.getId(), member.getId());
            if (!exists) {
                var groupMember = groupMemberRepository.save(vikoba.service.organization.entity.GroupMember.builder()
                        .group(group)
                        .member(member)
                        .membershipNumber(uniqueMembershipNumber(group.getId()))
                        .joinedDate(java.time.LocalDate.now())
                        .membershipType(vikoba.service.common.enums.MembershipType.FOUNDING)
                        .status(vikoba.service.common.enums.MembershipStatus.ACTIVE)
                        .build());

                memberRoleRepository.save(vikoba.service.organization.entity.MemberRole.builder()
                        .groupMember(groupMember)
                        .role(vikoba.service.common.enums.GroupRole.GROUP_ADMIN)
                        .startDate(java.time.LocalDate.now())
                        .active(true)
                        .build());
            }
        } catch (Exception ex) {
            // fail-safe: log and continue - group creation succeeded even if member linking
            // failed
            System.err.println("Unable to attach creating user as group member: " + ex.getMessage());
        }

        return new VikobaGroupCreateResponse(
                organization.getId(), group.getId(), organization.getName(),
                group.getName(), group.getCode(), group.getCurrency(), group.getStartDate(), group.getEndDate());
    }

    @Transactional
    public ApiResponse<GroupWithSettingsResponse> createGroupWithSettings(
            GroupProfileSettingsRequest request) {

        // ============================================================
        // 1. VALIDATE REQUEST
        // ============================================================

        String groupName = required(request.getName(), "name");

        if (request.getStartDate() == null) {
            throw new IllegalArgumentException(
                    "Group start date is required.");
        }

        if (request.getEndDate() == null) {
            throw new IllegalArgumentException(
                    "Group end date is required.");
        }

        validateGroupCycleDates(
                request.getStartDate(),
                request.getEndDate());

        // ============================================================
        // 2. GET CURRENT USER
        // ============================================================

        User user = currentUser();

        if (user == null) {
            throw new IllegalStateException(
                    "Authenticated user not found.");
        }

        // ============================================================
        // 3. GET MEMBER
        // ============================================================

        Member member = user.getMember();

        if (member == null || member.getId() == null) {

            throw new IllegalStateException(
                    "Your account is not associated with a member profile. "
                            + "Please complete your member registration first.");
        }

        // ============================================================
        // 4. RESOLVE ORGANIZATION
        // ============================================================

        Organization organization = resolveOrganizationForUser(user);

        // ============================================================
        // 5. CURRENCY
        // ============================================================

        String currency = request.getCurrency() == null
                || request.getCurrency().isBlank()
                        ? "TZS"
                        : request.getCurrency()
                                .trim()
                                .toUpperCase(Locale.ROOT);

        // ============================================================
        // 6. CREATE GROUP
        // ============================================================

        VikobaGroup group = VikobaGroup.builder()
                .organization(organization)
                .name(groupName)
                .code(uniqueCode(groupName))
                .phone(blankToNull(request.getPhone()))
                .email(blankToNull(request.getEmail()))
                .description(null)
                .meetingFrequency(null)
                .meetingDay(null)
                .formationDate(LocalDate.now())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .currency(currency)
                .build();

        group = groupRepository.save(group);

        // ============================================================
        // 7. CREATE GROUP SETTINGS
        // ============================================================

        createGroupSettings(
                group,
                request.getSettings());

        // ============================================================
        // 8. CREATE GROUP MEMBERSHIP
        // ============================================================

        boolean alreadyMember = groupMemberRepository.existsByGroupIdAndMemberId(
                group.getId(),
                member.getId());

        if (!alreadyMember) {

            GroupMember groupMember = GroupMember.builder()
                    .group(group)
                    .member(member)
                    .membershipNumber(
                            generateMembershipNumber(group))
                    .joinedDate(LocalDate.now())
                    .membershipType(
                            MembershipType.ORDINARY)
                    .status(
                            MembershipStatus.ACTIVE)
                    .build();

            groupMemberRepository.save(groupMember);
        }

        // ============================================================
        // 9. BUILD GROUP RESPONSE
        // ============================================================

        VikobaGroupCreateResponse groupResponse = new VikobaGroupCreateResponse(
                organization.getId(),
                group.getId(),
                organization.getName(),
                group.getName(),
                group.getCode(),
                group.getCurrency(),
                group.getStartDate(),
                group.getEndDate());

        // ============================================================
        // 10. GET SETTINGS
        // ============================================================

        GroupSettingsRequest settingsRequest = null;

        Optional<GroupSettings> optionalSettings = groupSettingsRepository.findByGroupId(
                group.getId());

        if (optionalSettings.isPresent()) {

            GroupSettings settings = optionalSettings.get();

            settingsRequest = new GroupSettingsRequest();

            settingsRequest.setMinimumContribution(
                    settings.getMinimumContribution());

            settingsRequest.setMaximumContribution(
                    settings.getMaximumContribution());

            settingsRequest.setSharePrice(
                    settings.getSharePrice());

            settingsRequest.setMaximumSharesPerMember(
                    settings.getMaximumSharesPerMember());

            settingsRequest.setLoanMultiplier(
                    settings.getLoanMultiplier());

            settingsRequest.setDefaultInterestRate(
                    settings.getDefaultInterestRate());

            settingsRequest.setDefaultLoanDurationMonths(
                    settings.getDefaultLoanDurationMonths());

            settingsRequest.setLatePaymentFine(
                    settings.getLatePaymentFine());
        }

        // ============================================================
        // 11. FINAL RESPONSE
        // ============================================================

        GroupWithSettingsResponse result = new GroupWithSettingsResponse(
                groupResponse,
                settingsRequest);

        return new ApiResponse<>(
                true,
                "Group created successfully.",
                result);
    }

    @Transactional(readOnly = true)
    public java.util.List<GroupWithSettingsResponse> listGroupsForCurrentUser() {
        User user = currentUser();
        if (user == null || user.getMember() == null || user.getMember().getId() == null) {
            return java.util.Collections.emptyList();
        }

        java.util.List<GroupMember> groupMembers = groupMemberRepository
                .findActiveGroupsByMemberId(user.getMember().getId());
        java.util.List<GroupWithSettingsResponse> results = new java.util.ArrayList<>();
        for (GroupMember gm : groupMembers) {
            VikobaGroup g = gm.getGroup();

            // load settings if present
            GroupSettingsRequest settingsRequest = null;
            java.util.Optional<GroupSettings> optionalSettings = groupSettingsRepository.findByGroupId(g.getId());
            boolean configured = false;
            if (optionalSettings.isPresent()) {
                GroupSettings s = optionalSettings.get();
                settingsRequest = new GroupSettingsRequest();
                settingsRequest.setMinimumContribution(s.getMinimumContribution());
                settingsRequest.setMaximumContribution(s.getMaximumContribution());
                settingsRequest.setSharePrice(s.getSharePrice());
                settingsRequest.setMaximumSharesPerMember(s.getMaximumSharesPerMember());
                settingsRequest.setLoanMultiplier(s.getLoanMultiplier());
                settingsRequest.setDefaultInterestRate(s.getDefaultInterestRate());
                settingsRequest.setDefaultLoanDurationMonths(s.getDefaultLoanDurationMonths());
                settingsRequest.setLatePaymentFine(s.getLatePaymentFine());
                configured = true;
            }

            VikobaGroupCreateResponse groupResponse = new VikobaGroupCreateResponse(
                    g.getOrganization().getId(),
                    g.getId(),
                    g.getOrganization().getName(),
                    g.getName(),
                    g.getCode(),
                    g.getCurrency(),
                    g.getStartDate(),
                    g.getEndDate());

            results.add(new GroupWithSettingsResponse(groupResponse, settingsRequest, configured));
        }
        return results;
    }

    private String generateMembershipNumber(VikobaGroup group) {

        return group.getCode()
                + "-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
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
                group.getCurrency(),
                group.getStartDate(),
                group.getEndDate());
    }

    @Transactional
    public VikobaGroupCreateResponse updateGroupAndSettings(Long groupId, GroupProfileSettingsRequest request) {
        return saveGroupDetailsAndSettings(groupId, request);
    }

    @Transactional(readOnly = true)
    public GroupWithSettingsResponse getGroupWithSettings(Long groupId) {

        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }

        // ============================================================
        // 1. LOAD GROUP + ORGANIZATION
        // ============================================================

        VikobaGroup group = groupRepository
                .findByIdWithOrganization(groupId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Group was not found."));

        // ============================================================
        // 2. BUILD GROUP RESPONSE
        // ============================================================

        VikobaGroupCreateResponse groupResponse = new VikobaGroupCreateResponse(
                group.getOrganization().getId(),
                group.getId(),
                group.getOrganization().getName(),
                group.getName(),
                group.getCode(),
                group.getCurrency(),
                group.getStartDate(),
                group.getEndDate());

        // ============================================================
        // 3. LOAD GROUP SETTINGS
        // ============================================================

        GroupSettingsRequest settingsRequest = null;

        Optional<GroupSettings> optionalSettings = groupSettingsRepository.findByGroupId(groupId);

        if (optionalSettings.isPresent()) {

            GroupSettings settings = optionalSettings.get();

            settingsRequest = new GroupSettingsRequest();

            settingsRequest.setMinimumContribution(
                    settings.getMinimumContribution());

            settingsRequest.setMaximumContribution(
                    settings.getMaximumContribution());

            settingsRequest.setSharePrice(
                    settings.getSharePrice());

            settingsRequest.setMaximumSharesPerMember(
                    settings.getMaximumSharesPerMember());

            settingsRequest.setLoanMultiplier(
                    settings.getLoanMultiplier());

            settingsRequest.setDefaultInterestRate(
                    settings.getDefaultInterestRate());

            settingsRequest.setDefaultLoanDurationMonths(
                    settings.getDefaultLoanDurationMonths());

            settingsRequest.setLatePaymentFine(
                    settings.getLatePaymentFine());
        }

        // ============================================================
        // 4. RETURN
        // ============================================================

        return new GroupWithSettingsResponse(
                groupResponse,
                settingsRequest);
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

    public static void validateGroupCycleDates(
            LocalDate startDate,
            LocalDate endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("startDate is required.");
        }

        if (endDate == null) {
            throw new IllegalArgumentException("endDate is required.");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must be after startDate.");
        }

        long cycleDays = ChronoUnit.DAYS.between(startDate, endDate);

        if (cycleDays <= 0) {
            throw new IllegalArgumentException(
                    "The Kikoba end date must be after the start date. " +
                            "Please choose a valid date range.");
        }
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

    private Member buildMemberFromUser(User user) {
        String fullName = user.getUsername() == null || user.getUsername().isBlank()
                ? "Group Owner"
                : user.getUsername().trim();
        String[] nameParts = fullName.split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : firstName;

        return Member.builder()
                .memberNumber(
                        "MBR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT))
                .firstName(firstName)
                .lastName(lastName)
                .phone(user.getPhone())
                .email(user.getEmail())
                .build();
    }

    private String uniqueMembershipNumber(Long groupId) {
        return "MEM-" + groupId + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
