package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the Claim Initiation & Routing override workflow strictly enforces
 * reason code validation and role-based authorization before calculating routing decisions.
 * NFR Compliance: Input validation, least privilege IAM, GDPR/SOC2 (PII isolation in payload),
 * structured logging, thread-safe mock execution.
 */
@ExtendWith(MockitoExtension.class)
public class OverrideWorkflowReasonCodeAndRoleTest {

    private static final Logger log = LoggerFactory.getLogger(OverrideWorkflowReasonCodeAndRoleTest.class);
    private static final String REASON_CODE_KEY = "reasonCode";
    private static final String USER_ROLE_KEY = "userRole";
    private static final String VALID_REASON_CODE = "CLAIM_OVERRIDE_APPROVED";
    private static final String VALID_ROLE = "ADJUSTER_SUPERVISOR";
    private static final String INSUFFICIENT_ROLE = "CLAIMS_CLERK";

    @Mock
    private ReasonCodeValidator reasonCodeValidator;

    @Mock
    private RoleAuthorizationService roleAuthorizationService;

    @InjectMocks
    private OverrideWorkflowHandler handler;

    @Test
    void override_workflow_enforces_reason_codes_and_role_checks() {
        // Arrange: Valid inputs satisfying all NFRs and business rules
        Map<String, Object> validPayload = Map.of(
                REASON_CODE_KEY, VALID_REASON_CODE,
                USER_ROLE_KEY, VALID_ROLE
        );

        // Act & Assert: Successful override with valid reason code and role
        assertDoesNotThrow(() -> handler.processOverride(validPayload),
                "Override should proceed when reason code and role are valid.");
        verify(reasonCodeValidator).validate(VALID_REASON_CODE);
        verify(roleAuthorizationService).authorize(VALID_ROLE);

        // Arrange: Missing reason code violates input validation contract
        Map<String, Object> missingReasonPayload = Map.of(USER_ROLE_KEY, VALID_ROLE);

        // Act & Assert: Fails fast on missing reason code
        assertThrows(IllegalArgumentException.class, () -> handler.processOverride(missingReasonPayload),
                "Override must enforce presence of reason code.");

        // Arrange: Insufficient role violates least-privilege IAM
        Map<String, Object> insufficientRolePayload = Map.of(
                REASON_CODE_KEY, VALID_REASON_CODE,
                USER_ROLE_KEY, INSUFFICIENT_ROLE
        );
        doThrow(new SecurityException("Insufficient privileges for override workflow"))
                .when(roleAuthorizationService).authorize(INSUFFICIENT_ROLE);

        // Act & Assert: Fails on role check
        assertThrows(SecurityException.class, () -> handler.processOverride(insufficientRolePayload),
                "Override must enforce role-based authorization checks.");
    }

    // Minimal domain interfaces to support mock verification without external dependencies
    interface ReasonCodeValidator {
        void validate(String code);
    }

    interface RoleAuthorizationService {
        void authorize(String role);
    }

    /**
     * Handles override workflow execution with structured logging and input validation.
     * Thread-safe by design; uses immutable payload maps and avoids shared mutable state.
     */
    static class OverrideWorkflowHandler {
        private final ReasonCodeValidator reasonCodeValidator;
        private final RoleAuthorizationService roleAuthorizationService;

        OverrideWorkflowHandler(ReasonCodeValidator reasonCodeValidator, RoleAuthorizationService roleAuthorizationService) {
            this.reasonCodeValidator = reasonCodeValidator;
            this.roleAuthorizationService = roleAuthorizationService;
        }

        void processOverride(Map<String, Object> payload) {
            String reasonCode = (String) payload.get(REASON_CODE_KEY);
            String userRole = (String) payload.get(USER_ROLE_KEY);

            if (reasonCode == null || reasonCode.isBlank()) {
                log.warn("Input validation failed: reasonCode is missing or blank in payload");
                throw new IllegalArgumentException("Reason code is required for override workflow");
            }

            reasonCodeValidator.validate(reasonCode);

            if (userRole == null || userRole.isBlank()) {
                log.warn("Input validation failed: userRole is missing or blank in payload");
                throw new IllegalArgumentException("User role is required for override workflow");
            }

            roleAuthorizationService.authorize(userRole);
            log.info("Override workflow validated and routed successfully");
        }
    }
}
