package vikoba.service.organization.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import vikoba.service.common.enums.GroupRole;

/** Keeps PostgreSQL's enum check in step with roles assignable by the application.
 * Hibernate's schema update creates this check but does not expand an older check
 * when new Java enum values are added.
 */
@Configuration
@RequiredArgsConstructor
public class MemberRoleConstraintSynchronizer {
    private static final String CONSTRAINT = "member_roles_role_check";
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Bean
    ApplicationRunner synchronizeMemberRoleConstraint() {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            Boolean postgres = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
            if (!Boolean.TRUE.equals(postgres)) return;
            Boolean tableExists = jdbcTemplate.queryForObject(
                    "select to_regclass('public.member_roles') is not null", Boolean.class);
            if (!Boolean.TRUE.equals(tableExists)) return;

            List<String> definitions = jdbcTemplate.queryForList("""
                    select pg_get_constraintdef(c.oid)
                    from pg_constraint c
                    where c.conrelid = 'public.member_roles'::regclass and c.conname = ?
                    """, String.class, CONSTRAINT);
            String current = definitions.isEmpty() ? "" : definitions.getFirst();
            boolean currentForEveryRole = Arrays.stream(GroupRole.values())
                    .allMatch(role -> current.contains("'" + role.name() + "'"));
            if (currentForEveryRole) return;

            String allowedRoles = Arrays.stream(GroupRole.values())
                    .map(role -> "'" + role.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbcTemplate.execute("alter table public.member_roles drop constraint if exists " + CONSTRAINT);
            jdbcTemplate.execute("alter table public.member_roles add constraint " + CONSTRAINT
                    + " check (role in (" + allowedRoles + "))");
        });
    }
}
