package vikoba.service.loan.dto;

public record LoanGuarantorOption(Long id, String name, String phone, String address, String membershipNumber,
        String status, boolean available, String reason) {}
