package vikoba.service.loan.dto;

import java.math.BigDecimal;

public record LoanGuaranteeRequest(Long id, Long loanId, String loanNumber, String applicantName,
        BigDecimal guaranteedAmount, String purpose, String status) {}
