package vikoba.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.fine.entity.FineType;
import vikoba.service.fine.repository.FineTypeRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import java.math.BigDecimal;
import java.util.List;

/** Adds the standard Tanzanian VIKOBA penalty catalogue to every group once. */
@Component
@RequiredArgsConstructor
public class FineTypeSeeder implements CommandLineRunner {
    private final VikobaGroupRepository groupRepository;
    private final FineTypeRepository fineTypeRepository;

    private record DefaultType(String code, String name, String description, BigDecimal amount) {}

    private static final List<DefaultType> DEFAULTS = List.of(
            new DefaultType("MEETING_ABSENCE", "Meeting absence", "Failure to attend a scheduled group meeting without an approved excuse.", new BigDecimal("10000")),
            new DefaultType("MEETING_LATE", "Late arrival", "Arriving after the agreed meeting start time.", new BigDecimal("5000")),
            new DefaultType("LATE_CONTRIBUTION", "Late contribution", "Contribution paid after the group deadline.", new BigDecimal("5000")),
            new DefaultType("LATE_LOAN_PAYMENT", "Late loan repayment", "Loan instalment paid after its due date.", new BigDecimal("10000")),
            new DefaultType("MISCONDUCT", "Misconduct", "Breach of approved group rules or member conduct.", new BigDecimal("10000")),
            new DefaultType("OTHER", "Other penalty", "A penalty approved by the group for an exceptional case.", BigDecimal.ZERO)
    );

    @Override
    @Transactional
    public void run(String... args) {
        groupRepository.findAll().forEach(group -> DEFAULTS.forEach(definition ->
                fineTypeRepository.findByGroupIdAndCode(group.getId(), definition.code()).orElseGet(() ->
                        fineTypeRepository.save(FineType.builder().group(group).code(definition.code())
                                .name(definition.name()).description(definition.description())
                                .defaultAmount(definition.amount()).active(true).build()))));
    }
}
