package vikoba.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.PaymentAllocationType;
import vikoba.service.common.enums.PaymentStatus;
import vikoba.service.contribution.repository.PaymentAllocationRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.social.service.SocialFundService;

@Component
@RequiredArgsConstructor
public class SocialFundSeeder implements CommandLineRunner {
    private final VikobaGroupRepository groupRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SocialFundService socialFundService;

    @Override
    @Transactional
    public void run(String... args) {
        // Calling types persists the standard request categories for existing groups.
        groupRepository.findAll().forEach(group -> socialFundService.types(group.getId()));

        // Earlier approved share purchases already have a completed JAMII payment
        // allocation. Convert each one into the social-fund ledger exactly once.
        allocationRepository.findByTypeAndPaymentStatus(
                        PaymentAllocationType.JAMII_SHARE_PAYMENT, PaymentStatus.COMPLETED)
                .forEach(allocation -> {
                    var payment = allocation.getPayment();
                    if (payment.getGroupMember() != null) {
                        socialFundService.recordShareContribution(payment.getGroupMember(), allocation.getAmount(),
                                payment.getReference());
                    }
                });
    }
}
