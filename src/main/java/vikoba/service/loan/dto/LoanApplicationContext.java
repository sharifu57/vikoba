package vikoba.service.loan.dto;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

public record LoanApplicationContext(Long groupMemberId, String name, String nationalId, String phone,
        String address, String membershipNumber, BigDecimal sharesValue, BigDecimal loanMultiplier,
        BigDecimal maximumLoan, Integer requiredGuarantors, Integer defaultDurationMonths, Integer maxDurationMonths,
        BigDecimal interestRate, LocalDate groupEndDate, List<LoanGuarantorOption> guarantors) {}
