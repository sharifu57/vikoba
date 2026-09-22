package vikoba.service.social.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.SocialFundRequestStatus;
import vikoba.service.social.dto.*;
import vikoba.service.social.entity.*;
import vikoba.service.social.repository.*;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.contribution.dto.ShareApprovalStep;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import vikoba.service.organization.repository.MemberRoleRepository;
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.organization.service.SocialFundApprovalWorkflowService;
import org.springframework.context.ApplicationEventPublisher;
import vikoba.service.notification.SmsNotificationRequestedEvent;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SocialFundService {
    public static final String SHARE_CONTRIBUTION_CODE = "JAMII_CONTRIBUTION";

    private record DefaultType(String code, String name, String description) {}
    private static final java.util.List<DefaultType> DEFAULT_TYPES = java.util.List.of(
            new DefaultType("MEDICAL", "Medical support", "Treatment, medicine, hospital, and health emergencies."),
            new DefaultType("BEREAVEMENT", "Bereavement support", "Support after the death of a member or close family member."),
            new DefaultType("EMERGENCY", "Emergency relief", "Urgent support after an unexpected hardship or disaster."),
            new DefaultType("EDUCATION", "Education support", "Approved education-related welfare assistance."),
            new DefaultType("FAMILY_EVENT", "Family ceremony support", "Support for an approved family or community ceremony."),
            new DefaultType("OTHER_WELFARE", "Other welfare support", "Other welfare assistance approved under group rules."));
    private final SocialFundTypeRepository typeRepository;
    private final SocialFundRequestRepository requestRepository;
    private final SocialFundContributionRepository contributionRepository;
    private final GroupMemberRepository memberRepository;
    private final VikobaGroupRepository groupRepository;
    private final GroupAuthorizationService authorizationService;
    private final SocialFundApprovalWorkflowService approvalWorkflow;
    private final MemberRoleRepository memberRoleRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public java.util.List<SocialFundTypeResponse> types(Long groupId) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));
        ensureDefaultTypes(group);
        return typeRepository.findByGroupIdAndActiveTrueOrderByNameAsc(groupId).stream()
                .filter(type -> !SHARE_CONTRIBUTION_CODE.equals(type.getCode()))
                .map(this::typeResponse).toList();
    }

    @Transactional
    public void recordShareContribution(GroupMember member, BigDecimal amount, String reference) {
        if (member == null || amount == null || amount.signum() <= 0 || reference == null || reference.isBlank()) return;
        if (contributionRepository.existsByReference(reference)) return;
        var type = contributionType(member.getGroup());
        contributionRepository.save(SocialFundContribution.builder()
                .groupMember(member).fundType(type).amount(amount)
                .contributionDate(LocalDate.now()).reference(reference.trim()).build());
    }

    /** Creates a support type that belongs only to the selected group. */
    @Transactional
    public SocialFundTypeResponse createType(Long groupId, SocialFundTypeInput input) {
        String name = required(input.getName(), "Fund type name");
        String code = input.getCode() == null || input.getCode().isBlank()
                ? codeFrom(name)
                : normalizeCode(input.getCode());
        if (typeRepository.existsByGroupIdAndCode(groupId, code))
            throw new IllegalArgumentException("A Jamii fund type with this code already exists for this group.");
        BigDecimal contribution = input.getDefaultContribution();
        if (contribution != null && contribution.signum() < 0)
            throw new IllegalArgumentException("Default contribution cannot be negative.");
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));
        SocialFundType saved = typeRepository.save(SocialFundType.builder()
                .group(group).code(code).name(name)
                .description(blankToNull(input.getDescription()))
                .defaultContribution(contribution).mandatory(Boolean.TRUE.equals(input.getMandatory()))
                .active(true).build());
        return typeResponse(saved);
    }

    @Transactional(readOnly = true)
    public java.util.List<SocialFundRequestResponse> requests(Long groupId) {
        GroupMember current = authorizationService.requireCurrentMembership(groupId);
        boolean reviewer = approvalWorkflow.get(groupId).stream().anyMatch(step -> hasRole(groupId, step.role()));
        return requestRepository.findByGroupId(groupId).stream()
                .filter(request -> reviewer || request.getGroupMember().getId().equals(current.getId()))
                .map(this::requestResponse).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<SocialFundContributionResponse> contributions(Long groupId) {
        return contributionRepository.findByGroupId(groupId).stream().map(this::contributionResponse).toList();
    }

    @Transactional(readOnly = true)
    public SocialFundSummaryResponse summary(Long groupId) {
        var requests = requestRepository.findByGroupId(groupId);
        var contributions = contributionRepository.findByGroupId(groupId);
        BigDecimal totalContributions = contributions.stream().map(SocialFundContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalApproved = requests.stream().filter(r -> r.getApprovedAmount() != null)
                .map(SocialFundRequest::getApprovedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = requests.stream().filter(r -> r.getStatus() == SocialFundRequestStatus.PAID)
                .map(r -> r.getApprovedAmount() == null ? BigDecimal.ZERO : r.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = requests.stream().filter(r -> r.getStatus() == SocialFundRequestStatus.APPROVED)
                .map(r -> r.getApprovedAmount() == null ? BigDecimal.ZERO : r.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return SocialFundSummaryResponse.builder().totalContributions(totalContributions).totalApproved(totalApproved)
                .totalPaid(totalPaid).pendingRequests(pending)
                .availableBalance(totalContributions.subtract(totalPaid).subtract(pending).max(BigDecimal.ZERO))
                .requestCount(requests.size())
                .pendingCount(requests.stream().filter(r -> r.getStatus() == SocialFundRequestStatus.PENDING).count())
                .build();
    }

    @Transactional
    public SocialFundRequestResponse request(Long groupId, SocialFundRequestInput input) {
        if (input.getFundTypeId() == null)
            throw new IllegalArgumentException("Jamii fund type is required.");
        if (input.getRequestedAmount() == null || input.getRequestedAmount().signum() <= 0)
            throw new IllegalArgumentException("Requested amount must be greater than zero");
        GroupMember member = authorizationService.requireCurrentMembership(groupId);
        SocialFundType type = typeRepository.findByIdAndGroupId(input.getFundTypeId(), groupId)
                .orElseThrow(() -> new IllegalArgumentException("Jamii fund type not found"));
        if (!type.isActive())
            throw new IllegalArgumentException("This Jamii fund type is not active.");
        if (SHARE_CONTRIBUTION_CODE.equals(type.getCode()))
            throw new IllegalArgumentException("Select a welfare support type for this request.");
        if (input.getRequestedAmount().compareTo(availableBalance(groupId, null)) > 0)
            throw new IllegalArgumentException("Requested amount exceeds the available Jamii fund balance.");
        SocialFundRequest saved = requestRepository.save(SocialFundRequest.builder().groupMember(member).fundType(type)
                .reference("JAMII-" + UUID.randomUUID()).requestedAmount(input.getRequestedAmount())
                .reason(input.getReason()).requestedDate(LocalDate.now())
                .approvalStepsJson(writeSteps(initialSteps(groupId, member))).build());
        return requestResponse(saved);
    }

    @Transactional
    public SocialFundRequestResponse approve(Long groupId, Long requestId, BigDecimal amount) {
        SocialFundRequest request = getRequest(groupId, requestId);
        if (request.getStatus() != SocialFundRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending Jamii requests can be approved.");
        GroupMember reviewer = authorizationService.requireCurrentMembership(groupId);
        if (reviewer.getId().equals(request.getGroupMember().getId()))
            throw new AccessDeniedException("You cannot approve your own Jamii request.");
        var steps = readSteps(request);
        int next = nextStep(steps);
        if (next < 0) throw new IllegalArgumentException("All Jamii approval steps are complete.");
        GroupRole requiredRole = GroupRole.valueOf(steps.get(next).role());
        if (!hasRole(groupId, requiredRole))
            throw new AccessDeniedException("This request is waiting for " + steps.get(next).label() + ".");
        if (amount == null || amount.signum() <= 0 || amount.compareTo(request.getRequestedAmount()) > 0)
            throw new IllegalArgumentException("Approved amount must be positive and not exceed the request");
        if (amount.compareTo(availableBalance(groupId, request.getId())) > 0)
            throw new IllegalArgumentException("Approved amount exceeds the uncommitted Jamii fund balance.");
        var current = steps.get(next);
        steps.set(next, new ShareApprovalStep(current.role(), current.label(), LocalDateTime.now().toString(), reviewer.getId(), false));
        request.setApprovalStepsJson(writeSteps(steps));
        request.setApprovedAmount(amount);
        if (nextStep(steps) < 0) {
            request.setApprovedDate(LocalDate.now());
            request.setStatus(SocialFundRequestStatus.APPROVED);
            notifyApplicant(request, "VIKOBA360: Ombi lako la Jamii " + request.getReference()
                    + " limeidhinishwa kwa TZS " + amount.toPlainString() + ". Linasubiri malipo.");
        }
        return requestResponse(requestRepository.save(request));
    }

    @Transactional
    public SocialFundRequestResponse reject(Long groupId, Long requestId) {
        SocialFundRequest request = getRequest(groupId, requestId);
        if (request.getStatus() != SocialFundRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending Jamii requests can be rejected.");
        GroupMember reviewer = authorizationService.requireCurrentMembership(groupId);
        if (reviewer.getId().equals(request.getGroupMember().getId()))
            throw new AccessDeniedException("You cannot reject your own Jamii request.");
        int next = nextStep(readSteps(request));
        if (next < 0 || !hasRole(groupId, GroupRole.valueOf(readSteps(request).get(next).role())))
            throw new AccessDeniedException("You are not the current reviewer for this Jamii request.");
        request.setStatus(SocialFundRequestStatus.REJECTED);
        notifyApplicant(request, "VIKOBA360: Ombi lako la Jamii " + request.getReference()
                + " limekataliwa. Ingia kwenye mfumo kuona hali ya ombi.");
        return requestResponse(requestRepository.save(request));
    }

    @Transactional
    public SocialFundRequestResponse pay(Long groupId, Long requestId) {
        SocialFundRequest request = getRequest(groupId, requestId);
        if (request.getStatus() != SocialFundRequestStatus.APPROVED)
            throw new IllegalArgumentException("Only approved requests can be paid");
        GroupMember payer = authorizationService.requireCurrentMembership(groupId);
        if (payer.getId().equals(request.getGroupMember().getId())
                || !(authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT)
                || authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN)))
            throw new AccessDeniedException("Only an independent accountant may disburse approved Jamii support.");
        BigDecimal contributions = contributionRepository.findByGroupId(groupId).stream()
                .map(SocialFundContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = requestRepository.findByGroupId(groupId).stream()
                .filter(r -> r.getStatus() == SocialFundRequestStatus.PAID)
                .map(r -> r.getApprovedAmount() == null ? BigDecimal.ZERO : r.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getApprovedAmount().compareTo(contributions.subtract(paid)) > 0)
            throw new IllegalArgumentException("Insufficient Jamii fund balance to disburse this request.");
        request.setStatus(SocialFundRequestStatus.PAID);
        notifyApplicant(request, "VIKOBA360: Malipo ya Jamii kwa ombi " + request.getReference()
                + " ya TZS " + request.getApprovedAmount().toPlainString() + " yametolewa.");
        return requestResponse(requestRepository.save(request));
    }

    private SocialFundRequest getRequest(Long groupId, Long id) {
        SocialFundRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jamii request not found"));
        if (!request.getGroupMember().getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Request does not belong to this group");
        return request;
    }

    private BigDecimal availableBalance(Long groupId, Long excludedRequestId) {
        BigDecimal contributed = contributionRepository.findByGroupId(groupId).stream()
                .map(SocialFundContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal committed = requestRepository.findByGroupId(groupId).stream()
                .filter(request -> !java.util.Objects.equals(request.getId(), excludedRequestId))
                .filter(request -> request.getStatus() == SocialFundRequestStatus.APPROVED
                        || request.getStatus() == SocialFundRequestStatus.PAID)
                .map(request -> request.getApprovedAmount() == null ? BigDecimal.ZERO : request.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return contributed.subtract(committed).max(BigDecimal.ZERO);
    }

    private void ensureDefaultTypes(vikoba.service.organization.entity.VikobaGroup group) {
        DEFAULT_TYPES.forEach(definition -> typeRepository.findByGroupIdAndCode(group.getId(), definition.code())
                .orElseGet(() -> typeRepository.save(SocialFundType.builder().group(group)
                        .code(definition.code()).name(definition.name()).description(definition.description())
                        .mandatory(false).active(true).build())));
        contributionType(group);
    }

    private SocialFundType contributionType(vikoba.service.organization.entity.VikobaGroup group) {
        return typeRepository.findByGroupIdAndCode(group.getId(), SHARE_CONTRIBUTION_CODE)
                .orElseGet(() -> typeRepository.save(SocialFundType.builder().group(group)
                        .code(SHARE_CONTRIBUTION_CODE).name("Jamii share-purchase contribution")
                        .description("Jamii amount collected automatically whenever a member buys shares.")
                        .mandatory(true).active(true).build()));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String codeFrom(String name) {
        return normalizeCode(name);
    }

    private String normalizeCode(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (code.isBlank() || code.length() > 50)
            throw new IllegalArgumentException("Fund type code must contain up to 50 letters or numbers.");
        return code;
    }

    private SocialFundTypeResponse typeResponse(SocialFundType type) {
        return SocialFundTypeResponse.builder().id(type.getId()).code(type.getCode()).name(type.getName())
                .description(type.getDescription()).defaultContribution(type.getDefaultContribution())
                .mandatory(type.isMandatory()).active(type.isActive()).build();
    }

    public java.util.List<ShareApprovalStepConfig> approvalConfig(Long groupId) {
        authorizationService.requireMembership(groupId);
        return approvalWorkflow.get(groupId);
    }

    @Transactional
    public java.util.List<ShareApprovalStepConfig> configureApproval(Long groupId, java.util.List<ShareApprovalStepConfig> steps) {
        authorizationService.requirePermission(groupId, "WORKFLOW_MANAGE");
        approvalWorkflow.configure(authorizationService.requireCurrentMembership(groupId).getGroup(), steps);
        return approvalWorkflow.get(groupId);
    }

    private String memberName(GroupMember member) {
        return member.getMember().getFirstName() + " " + member.getMember().getLastName();
    }

    private SocialFundRequestResponse requestResponse(SocialFundRequest r) {
        var steps = readSteps(r);
        int next = nextStep(steps);
        GroupMember current = authorizationService.requireCurrentMembership(r.getGroupMember().getGroup().getId());
        boolean independent = !current.getId().equals(r.getGroupMember().getId());
        boolean canAct = r.getStatus() == SocialFundRequestStatus.PENDING && independent && next >= 0
                && hasRole(r.getGroupMember().getGroup().getId(), GroupRole.valueOf(steps.get(next).role()));
        return SocialFundRequestResponse.builder().id(r.getId()).groupMemberId(r.getGroupMember().getId())
                .memberName(memberName(r.getGroupMember())).membershipNumber(r.getGroupMember().getMembershipNumber())
                .fundTypeId(r.getFundType().getId()).fundTypeName(r.getFundType().getName()).reference(r.getReference())
                .requestedAmount(r.getRequestedAmount()).approvedAmount(r.getApprovedAmount()).reason(r.getReason())
                .status(r.getStatus().name()).requestedDate(r.getRequestedDate()).approvedDate(r.getApprovedDate())
                .approvalSteps(steps).currentStepRole(next < 0 ? null : steps.get(next).role())
                .currentStepLabel(next < 0 ? null : steps.get(next).label())
                .canApprove(canAct).canReject(canAct)
                .canDisburse(r.getStatus() == SocialFundRequestStatus.APPROVED && independent
                        && (authorizationService.hasRole(r.getGroupMember().getGroup().getId(), GroupRole.ACCOUNTANT)
                        || authorizationService.hasRole(r.getGroupMember().getGroup().getId(), GroupRole.GROUP_ADMIN)))
                .build();
    }

    private java.util.List<ShareApprovalStep> initialSteps(Long groupId, GroupMember applicant) {
        var applicantRoles = memberRoleRepository.findByGroupMemberIdAndActiveTrue(applicant.getId()).stream()
                .map(role -> role.getRole()).toList();
        var result = new java.util.ArrayList<ShareApprovalStep>();
        approvalWorkflow.get(groupId).forEach(config -> {
            boolean skip = applicantRoles.contains(config.role())
                    || (config.role() == GroupRole.GROUP_CHAIRMAN && applicantRoles.contains(GroupRole.CHAIRPERSON))
                    || (config.role() == GroupRole.CHAIRPERSON && applicantRoles.contains(GroupRole.GROUP_CHAIRMAN));
            result.add(new ShareApprovalStep(config.role().name(), config.label(), null, null, skip));
        });
        if (result.stream().allMatch(ShareApprovalStep::skipped))
            result.add(new ShareApprovalStep(GroupRole.GROUP_ADMIN.name(), "Independent admin review", null, null, false));
        return result;
    }

    private java.util.List<ShareApprovalStep> readSteps(SocialFundRequest request) {
        if (request.getApprovalStepsJson() == null || request.getApprovalStepsJson().isBlank())
            return initialSteps(request.getGroupMember().getGroup().getId(), request.getGroupMember());
        try { return objectMapper.readValue(request.getApprovalStepsJson(), new TypeReference<java.util.List<ShareApprovalStep>>() {}); }
        catch (Exception error) { throw new IllegalStateException("Invalid saved Jamii approval workflow", error); }
    }

    private String writeSteps(java.util.List<ShareApprovalStep> steps) {
        try { return objectMapper.writeValueAsString(steps); }
        catch (Exception error) { throw new IllegalStateException("Unable to save Jamii approval workflow", error); }
    }

    private int nextStep(java.util.List<ShareApprovalStep> steps) {
        for (int index = 0; index < steps.size(); index++)
            if (!steps.get(index).skipped() && steps.get(index).approvedAt() == null) return index;
        return -1;
    }

    private boolean hasRole(Long groupId, GroupRole role) {
        return authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN)
                || authorizationService.hasRole(groupId, role)
                || (role == GroupRole.GROUP_CHAIRMAN && authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON))
                || (role == GroupRole.CHAIRPERSON && authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN));
    }

    private void notifyApplicant(SocialFundRequest request, String message) {
        var person = request.getGroupMember().getMember();
        eventPublisher.publishEvent(new SmsNotificationRequestedEvent(person.getPhone(), memberName(request.getGroupMember()), message));
    }

    private SocialFundContributionResponse contributionResponse(SocialFundContribution c) {
        return SocialFundContributionResponse.builder().id(c.getId()).groupMemberId(c.getGroupMember().getId())
                .fundTypeId(c.getFundType().getId()).amount(c.getAmount()).contributionDate(c.getContributionDate())
                .reference(c.getReference()).build();
    }
}
