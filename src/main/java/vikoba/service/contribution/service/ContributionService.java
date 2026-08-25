package vikoba.service.contribution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vikoba.service.common.enums.ContributionStatus;
import vikoba.service.contribution.dto.*;
import vikoba.service.contribution.entity.ContributionPeriod;
import vikoba.service.contribution.entity.ContributionType;
import vikoba.service.contribution.entity.MemberContribution;
import vikoba.service.contribution.repository.ContributionPeriodRepository;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.repository.GroupMemberRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing member contributions
 * Handles single and bulk contribution recording, retrieval, and reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContributionService {

    private final MemberContributionRepository memberContributionRepository;
    private final ContributionPeriodRepository contributionPeriodRepository;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * Record a single member contribution
     */
    @Transactional
    public MemberContributionResponse recordContribution(RecordContributionRequest request) {
        log.info("Recording contribution for member: {} in period: {}",
                request.getGroupMemberId(), request.getContributionPeriodId());

        // Validate member exists
        GroupMember groupMember = groupMemberRepository.findById(request.getGroupMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        // Validate contribution period exists
        ContributionPeriod period = contributionPeriodRepository.findById(request.getContributionPeriodId())
                .orElseThrow(() -> new IllegalArgumentException("Contribution period not found"));

        // Validate amount
        if (request.getPaidAmount() == null || request.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Paid amount must be greater than zero");
        }

        // Check for existing contribution for this member and period
        Optional<MemberContribution> existing = memberContributionRepository
                .findByGroupMemberId(request.getGroupMemberId())
                .stream()
                .filter(mc -> mc.getContributionPeriod().getId().equals(request.getContributionPeriodId()))
                .findFirst();

        MemberContribution contribution;
        if (existing.isPresent()) {
            // Update existing contribution
            contribution = existing.get();
            BigDecimal oldPaidAmount = contribution.getPaidAmount();
            contribution.setPaidAmount(request.getPaidAmount());
            contribution.setBalance(period.getExpectedAmount().subtract(request.getPaidAmount()));

            // Update status
            if (contribution.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                contribution.setStatus(ContributionStatus.PAID);
            } else if (contribution.getBalance().compareTo(period.getExpectedAmount()) < 0) {
                contribution.setStatus(ContributionStatus.PARTIAL);
            }
            contribution.setPaidAt(LocalDateTime.now());
        } else {
            // Create new contribution
            BigDecimal balance = period.getExpectedAmount().subtract(request.getPaidAmount());
            ContributionStatus status = balance.compareTo(BigDecimal.ZERO) == 0 ? ContributionStatus.PAID
                    : ContributionStatus.PARTIAL;

            contribution = MemberContribution.builder()
                    .groupMember(groupMember)
                    .contributionPeriod(period)
                    .expectedAmount(period.getExpectedAmount())
                    .paidAmount(request.getPaidAmount())
                    .balance(balance)
                    .status(status)
                    .paidAt(LocalDateTime.now())
                    .build();
        }

        MemberContribution saved = memberContributionRepository.save(contribution);
        log.info("Contribution recorded successfully with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    /**
     * Process bulk contribution upload from Excel file
     */
    @Transactional
    public BulkContributionResult processBulkContributionUpload(Long groupId, MultipartFile file) {
        log.info("Processing bulk contribution upload for group: {}", groupId);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        List<BulkContributionRow> rows = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Start from row 1 (skip header)
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                BulkContributionRow bulkRow = extractRowData(row, i);

                try {
                    processBulkRow(bulkRow, groupId);
                    bulkRow.setProcessed(true);
                    successCount++;
                } catch (Exception e) {
                    bulkRow.setErrorMessage(e.getMessage());
                    bulkRow.setProcessed(false);
                    failureCount++;
                }
                rows.add(bulkRow);
            }
        } catch (Exception e) {
            log.error("Error processing bulk upload file", e);
            throw new IllegalArgumentException("Error processing Excel file: " + e.getMessage());
        }

        List<BulkContributionRow> failedRows = rows.stream()
                .filter(r -> !r.getProcessed())
                .collect(Collectors.toList());

        String status = failureCount == 0 ? "SUCCESS" : (successCount > 0 ? "PARTIAL" : "FAILED");
        String summary = String.format("Processed %d rows: %d successful, %d failed",
                rows.size(), successCount, failureCount);

        log.info("Bulk upload completed: {}", summary);

        return BulkContributionResult.builder()
                .totalRows(rows.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .summary(summary)
                .failedRows(failedRows)
                .status(status)
                .build();
    }

    /**
     * Extract data from Excel row
     */
    private BulkContributionRow extractRowData(Row row, int rowNumber) {
        return BulkContributionRow.builder()
                .memberIdentifier(getCellValue(row.getCell(0)))
                .contributionPeriod(getCellValue(row.getCell(1)))
                .paidAmount(new BigDecimal(getCellValue(row.getCell(2))))
                .paymentMethod(getCellValue(row.getCell(3)))
                .paymentReference(getCellValue(row.getCell(4)))
                .remarks(getCellValue(row.getCell(5)))
                .rowNumber(rowNumber)
                .build();
    }

    /**
     * Get cell value as string
     */
    private String getCellValue(Cell cell) {
        if (cell == null)
            return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /**
     * Process a single bulk contribution row
     */
    private void processBulkRow(BulkContributionRow row, Long groupId) {
        // Find member by identifier (ID, account number, or name)
        GroupMember member = findMemberByIdentifier(row.getMemberIdentifier(), groupId);
        if (member == null) {
            throw new IllegalArgumentException("Member not found: " + row.getMemberIdentifier());
        }

        // Find contribution period
        ContributionPeriod period = findContributionPeriodByDescription(row.getContributionPeriod(), groupId);
        if (period == null) {
            throw new IllegalArgumentException("Contribution period not found: " + row.getContributionPeriod());
        }

        // Record contribution
        RecordContributionRequest request = RecordContributionRequest.builder()
                .groupMemberId(member.getId())
                .contributionPeriodId(period.getId())
                .paidAmount(row.getPaidAmount())
                .paymentMethod(row.getPaymentMethod())
                .paymentReference(row.getPaymentReference())
                .remarks(row.getRemarks())
                .build();

        recordContribution(request);
    }

    /**
     * Find member by identifier (ID, account number, or name)
     */
    private GroupMember findMemberByIdentifier(String identifier, Long groupId) {
        // Try to find by ID
        try {
            Long memberId = Long.parseLong(identifier);
            return groupMemberRepository.findById(memberId)
                    .filter(m -> m.getGroup().getId().equals(groupId))
                    .orElse(null);
        } catch (NumberFormatException e) {
            // Not a numeric ID, try other fields
            return null;
        }
    }

    /**
     * Find contribution period by description (e.g., "January 2024" or "2024-01")
     */
    private ContributionPeriod findContributionPeriodByDescription(String periodDescription, Long groupId) {
        try {
            // Try parsing as YYYY-MM format
            YearMonth ym = YearMonth.parse(periodDescription, DateTimeFormatter.ofPattern("yyyy-MM"));

            // Find active contribution periods for the group and match the year/month
            return contributionPeriodRepository.findActiveByGroupId(groupId)
                    .stream()
                    .filter(cp -> {
                        YearMonth cpYm = YearMonth.from(cp.getPeriodStart());
                        return cpYm.equals(ym);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            // Try other formats if needed
            return null;
        }
    }

    /**
     * Get active contribution periods for a group
     */
    @Transactional(readOnly = true)
    public List<ContributionPeriodResponse> getActiveContributionPeriods(Long groupId) {

        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required.");
        }

        List<ContributionPeriod> periods = contributionPeriodRepository.findActiveByGroupId(groupId);

        return periods.stream()
                .map(cp -> ContributionPeriodResponse.builder()
                        .id(cp.getId())

                        .contributionTypeName(
                                cp.getContributionType() != null
                                        ? cp.getContributionType().getName()
                                        : null)

                        .periodStart(cp.getPeriodStart())
                        .periodEnd(cp.getPeriodEnd())
                        .expectedAmount(cp.getExpectedAmount())

                        .status(
                                cp.getStatus() != null
                                        ? cp.getStatus().name()
                                        : null)

                        .displayText(
                                formatPeriodDisplay(cp.getPeriodStart()))

                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Format period start date as display text
     */
    private String formatPeriodDisplay(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
    }

    /**
     * Get member contribution details
     */
    @Transactional(readOnly = true)
    public List<ContributionDetailResponse> getMemberContributionDetails(Long groupMemberId) {
        GroupMember member = groupMemberRepository.findById(groupMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        List<MemberContribution> contributions = memberContributionRepository.findByGroupMemberId(groupMemberId);

        return contributions.stream()
                .map(mc -> mapToDetailResponse(mc, member))
                .collect(Collectors.toList());
    }

    /**
     * Get group contribution details with optional filters
     */
    @Transactional(readOnly = true)
    public List<ContributionDetailResponse> getGroupContributionDetails(Long groupId, String status, Long periodId) {
        List<MemberContribution> contributions = memberContributionRepository.findByGroupId(groupId);

        return contributions.stream()
                .filter(mc -> status == null || mc.getStatus().toString().equals(status))
                .filter(mc -> periodId == null || mc.getContributionPeriod().getId().equals(periodId))
                .map(mc -> mapToDetailResponse(mc, mc.getGroupMember()))
                .collect(Collectors.toList());
    }

    /**
     * Update an existing contribution
     */
    @Transactional
    public MemberContributionResponse updateContribution(Long contributionId, RecordContributionRequest request) {
        MemberContribution contribution = memberContributionRepository.findById(contributionId)
                .orElseThrow(() -> new IllegalArgumentException("Contribution not found"));

        contribution.setPaidAmount(request.getPaidAmount());
        contribution.setBalance(contribution.getExpectedAmount().subtract(request.getPaidAmount()));

        if (contribution.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            contribution.setStatus(ContributionStatus.PAID);
        } else if (contribution.getBalance().compareTo(contribution.getExpectedAmount()) < 0) {
            contribution.setStatus(ContributionStatus.PARTIAL);
        } else {
            contribution.setStatus(ContributionStatus.PENDING);
        }

        contribution.setPaidAt(LocalDateTime.now());

        MemberContribution saved = memberContributionRepository.save(contribution);
        return mapToResponse(saved);
    }

    /**
     * Generate Excel template for bulk upload
     */
    public byte[] generateBulkUploadTemplate() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Contributions");

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Member ID/Account Number",
                "Contribution Period (YYYY-MM)",
                "Paid Amount",
                "Payment Method",
                "Payment Reference",
                "Remarks"
        };

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Add sample row
        Row sampleRow = sheet.createRow(1);
        sampleRow.createCell(0).setCellValue("1");
        sampleRow.createCell(1).setCellValue("2024-01");
        sampleRow.createCell(2).setCellValue(50000);
        sampleRow.createCell(3).setCellValue("Mobile Money");
        sampleRow.createCell(4).setCellValue("TXN12345");
        sampleRow.createCell(5).setCellValue("January contribution");

        // Set column widths
        int[] columnWidths = { 25, 20, 15, 15, 20, 25 };
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    /**
     * Get contribution summary for a group
     */
    public ContributionSummaryResponse getContributionSummary(Long groupId) {
        List<MemberContribution> contributions = memberContributionRepository.findByGroupId(groupId);

        BigDecimal totalExpected = contributions.stream()
                .map(MemberContribution::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = contributions.stream()
                .map(MemberContribution::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBalance = contributions.stream()
                .map(MemberContribution::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int membersCompleted = (int) contributions.stream()
                .filter(c -> c.getStatus() == ContributionStatus.PAID)
                .count();

        int membersPartial = (int) contributions.stream()
                .filter(c -> c.getStatus() == ContributionStatus.PARTIAL)
                .count();

        int membersPending = (int) contributions.stream()
                .filter(c -> c.getStatus() == ContributionStatus.PENDING)
                .count();

        Long totalMembers = groupMemberRepository.countByGroupId(groupId);

        double collectionRate = totalExpected.compareTo(BigDecimal.ZERO) > 0
                ? totalPaid.doubleValue() / totalExpected.doubleValue() * 100
                : 0;

        return ContributionSummaryResponse.builder()
                .totalExpected(totalExpected)
                .totalPaid(totalPaid)
                .totalBalance(totalBalance)
                .membersCompleted(membersCompleted)
                .membersPartial(membersPartial)
                .membersPending(membersPending)
                .collectionRate(collectionRate)
                .totalMembers(Math.toIntExact(totalMembers))
                .build();
    }

    /**
     * Map MemberContribution to MemberContributionResponse
     */
    private MemberContributionResponse mapToResponse(MemberContribution contribution) {
        return MemberContributionResponse.builder()
                .id(contribution.getId())
                .groupMemberId(contribution.getGroupMember().getId())
                .contributionPeriodId(contribution.getContributionPeriod().getId())
                .expectedAmount(contribution.getExpectedAmount())
                .paidAmount(contribution.getPaidAmount())
                .balance(contribution.getBalance())
                .status(contribution.getStatus().toString())
                .paidAt(contribution.getPaidAt())
                .build();
    }

    /**
     * Map MemberContribution to ContributionDetailResponse
     */
    private ContributionDetailResponse mapToDetailResponse(MemberContribution contribution, GroupMember member) {
        return ContributionDetailResponse.builder()
                .id(contribution.getId())
                .groupMemberId(member.getId())
                .memberName(member.getMember().getFirstName() + " " + member.getMember().getLastName())
                .memberPhone(member.getMember().getPhone())
                .memberAccountNumber(member.getMembershipNumber())
                .contributionType(contribution.getContributionPeriod().getContributionType().getName())
                .periodStart(contribution.getContributionPeriod().getPeriodStart())
                .periodEnd(contribution.getContributionPeriod().getPeriodEnd())
                .expectedAmount(contribution.getExpectedAmount())
                .paidAmount(contribution.getPaidAmount())
                .balance(contribution.getBalance())
                .status(contribution.getStatus().toString())
                .paidAt(contribution.getPaidAt())
                .build();
    }
}
