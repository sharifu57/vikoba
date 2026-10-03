package vikoba.service.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import vikoba.service.common.enums.AuditAction;
import vikoba.service.common.service.AuditLogService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuditRequestInterceptor implements HandlerInterceptor {
    private static final Pattern GROUP_PATH = Pattern.compile("/group/(\\d+)");
    private final AuditLogService auditLogService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        String path = request.getRequestURI();
        // System directory reads must not fill the audit trail while an admin browses it.
        if (path.startsWith("/api/system-admin/") && "GET".equalsIgnoreCase(request.getMethod()))
            return;
        if (path.startsWith("/api/audit-logs") || !path.startsWith("/api/") || response.getStatus() >= 500)
            return;
        Long groupId = groupId(request, path);
        AuditAction action = action(request.getMethod(), response.getStatus());
        String entity = entity(path);
        String username = AuditContext.getCurrentUser();
        if (username == null && action != AuditAction.LOGIN)
            return;
        auditLogService.record(username, groupId, action, entity, null, clientIp(request),
                request.getMethod() + " " + path + " -> " + response.getStatus());
    }

    private Long groupId(HttpServletRequest request, String path) {
        String query = request.getParameter("groupId");
        if (query == null) {
            Matcher matcher = GROUP_PATH.matcher(path);
            if (matcher.find())
                query = matcher.group(1);
        }
        try {
            return query == null ? null : Long.valueOf(query);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private AuditAction action(String method, int status) {
        if ("POST".equalsIgnoreCase(method))
            return status >= 400 ? AuditAction.REJECT : AuditAction.CREATE;
        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
            return status >= 400 ? AuditAction.REJECT : AuditAction.UPDATE;
        if ("DELETE".equalsIgnoreCase(method))
            return AuditAction.DELETE;
        return AuditAction.UPDATE;
    }

    private String entity(String path) {
        String[] parts = path.split("/");
        return parts.length > 2 ? parts[2].toUpperCase() : "API";
    }

    private String clientIp(HttpServletRequest request) {
        // A trusted proxy integration may resolve this address at the container
        // layer. Never trust a client-supplied forwarding header directly.
        return request.getRemoteAddr();
    }
}
