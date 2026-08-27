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

@Service
@RequiredArgsConstructor
public class SocialFundService {
    private final SocialFundTypeRepository typeRepository;
    private final SocialFundRequestRepository requestRepository;
    private final SocialFundContributionRepository contributionRepository;
    private final GroupMemberRepository memberRepository;
    private final VikobaGroupRepository groupRepository;

    @Transactional(readOnly = true)
    public java.util.List<SocialFundTypeResponse> types(Long groupId) {
        return typeRepository.findByGroupIdAndActiveTrueOrderByNameAsc(groupId).stream()
                .map(this::typeResponse).toList();
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
        return requestRepository.findByGroupId(groupId).stream().map(this::requestResponse).toList();
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
                .totalPaid(totalPaid).pendingRequests(pending).availableBalance(totalContributions.subtract(totalPaid))
                .requestCount(requests.size())
                .pendingCount(requests.stream().filter(r -> r.getStatus() == SocialFundRequestStatus.PENDING).count())
                .build();
    }

    @Transactional
    public SocialFundRequestResponse request(Long groupId, SocialFundRequestInput input) {
        if (input.getGroupMemberId() == null || input.getFundTypeId() == null)
            throw new IllegalArgumentException("Member and Jamii fund type are required.");
        if (input.getRequestedAmount() == null || input.getRequestedAmount().signum() <= 0)
            throw new IllegalArgumentException("Requested amount must be greater than zero");
        GroupMember member = memberRepository.findById(input.getGroupMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Group member not found"));
        if (!member.getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Member does not belong to this group");
        SocialFundType type = typeRepository.findByIdAndGroupId(input.getFundTypeId(), groupId)
                .orElseThrow(() -> new IllegalArgumentException("Jamii fund type not found"));
        if (!type.isActive())
            throw new IllegalArgumentException("This Jamii fund type is not active.");
        SocialFundRequest saved = requestRepository.save(SocialFundRequest.builder().groupMember(member).fundType(type)
                .reference("JAMII-" + UUID.randomUUID()).requestedAmount(input.getRequestedAmount())
                .reason(input.getReason()).requestedDate(LocalDate.now()).build());
        return requestResponse(saved);
    }

    @Transactional
    public SocialFundRequestResponse approve(Long groupId, Long requestId, BigDecimal amount) {
        SocialFundRequest request = getRequest(groupId, requestId);
        if (request.getStatus() != SocialFundRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending Jamii requests can be approved.");
        if (amount == null || amount.signum() <= 0 || amount.compareTo(request.getRequestedAmount()) > 0)
            throw new IllegalArgumentException("Approved amount must be positive and not exceed the request");
        request.setApprovedAmount(amount);
        request.setApprovedDate(LocalDate.now());
        request.setStatus(SocialFundRequestStatus.APPROVED);
        return requestResponse(requestRepository.save(request));
    }

    @Transactional
    public SocialFundRequestResponse reject(Long groupId, Long requestId) {
        SocialFundRequest request = getRequest(groupId, requestId);
        if (request.getStatus() != SocialFundRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending Jamii requests can be rejected.");
        request.setStatus(SocialFundRequestStatus.REJECTED);
        return requestResponse(requestRepository.save(request));
    }

    @Transactional
    public SocialFundRequestResponse pay(Long groupId, Long requestId) {
        SocialFundRequest request = getRequest(groupId, requestId);
        if (request.getStatus() != SocialFundRequestStatus.APPROVED)
            throw new IllegalArgumentException("Only approved requests can be paid");
        BigDecimal contributions = contributionRepository.findByGroupId(groupId).stream()
                .map(SocialFundContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = requestRepository.findByGroupId(groupId).stream()
                .filter(r -> r.getStatus() == SocialFundRequestStatus.PAID)
                .map(r -> r.getApprovedAmount() == null ? BigDecimal.ZERO : r.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getApprovedAmount().compareTo(contributions.subtract(paid)) > 0)
            throw new IllegalArgumentException("Insufficient Jamii fund balance to disburse this request.");
        request.setStatus(SocialFundRequestStatus.PAID);
        return requestResponse(requestRepository.save(request));
    }

    private SocialFundRequest getRequest(Long groupId, Long id) {
        SocialFundRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jamii request not found"));
        if (!request.getGroupMember().getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Request does not belong to this group");
        return request;
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

    private String memberName(GroupMember member) {
        return member.getMember().getFirstName() + " " + member.getMember().getLastName();
    }

    private SocialFundRequestResponse requestResponse(SocialFundRequest r) {
        return SocialFundRequestResponse.builder().id(r.getId()).groupMemberId(r.getGroupMember().getId())
                .memberName(memberName(r.getGroupMember())).membershipNumber(r.getGroupMember().getMembershipNumber())
                .fundTypeId(r.getFundType().getId()).fundTypeName(r.getFundType().getName()).reference(r.getReference())
                .requestedAmount(r.getRequestedAmount()).approvedAmount(r.getApprovedAmount()).reason(r.getReason())
                .status(r.getStatus().name()).requestedDate(r.getRequestedDate()).approvedDate(r.getApprovedDate())
                .build();
    }

    private SocialFundContributionResponse contributionResponse(SocialFundContribution c) {
        return SocialFundContributionResponse.builder().id(c.getId()).groupMemberId(c.getGroupMember().getId())
                .fundTypeId(c.getFundType().getId()).amount(c.getAmount()).contributionDate(c.getContributionDate())
                .reference(c.getReference()).build();
    }
}
