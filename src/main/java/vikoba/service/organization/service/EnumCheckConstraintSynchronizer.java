package vikoba.service.organization.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.PaymentAllocationType;

/** Hibernate does not expand PostgreSQL enum checks when Java enum values are added. */
@Configuration
@RequiredArgsConstructor
public class EnumCheckConstraintSynchronizer {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Bean
    ApplicationRunner synchronizeEnumChecks() {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            Boolean postgres = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
            if (!Boolean.TRUE.equals(postgres)) return;
            synchronize("member_roles", "member_roles_role_check", "role", GroupRole.values());
            synchronize("payment_allocations", "payment_allocations_type_check", "type", PaymentAllocationType.values());
        });
    }

    private void synchronize(String table, String constraint, String column, Enum<?>[] values) {
        // All identifiers come from constants above; only enum names become SQL values.
        Boolean tableExists = jdbcTemplate.queryForObject(
                "select to_regclass('public." + table + "') is not null", Boolean.class);
        if (!Boolean.TRUE.equals(tableExists)) return;
        List<String> definitions = jdbcTemplate.queryForList("""
                select pg_get_constraintdef(c.oid)
                from pg_constraint c
                where c.conrelid = ?::regclass and c.conname = ?
                """, String.class, "public." + table, constraint);
        String current = definitions.isEmpty() ? "" : definitions.getFirst();
        if (Arrays.stream(values).allMatch(value -> current.contains("'" + value.name() + "'"))) return;

        String allowed = Arrays.stream(values).map(value -> "'" + value.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("alter table public." + table + " drop constraint if exists " + constraint);
        jdbcTemplate.execute("alter table public." + table + " add constraint " + constraint
                + " check (" + column + " in (" + allowed + "))");
    }
}
