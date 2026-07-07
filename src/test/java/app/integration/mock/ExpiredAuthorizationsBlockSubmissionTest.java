package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ExpiredAuthorizationsBlockSubmissionTest {

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private PolicyClaimsDbService policyClaimsDbService;

    private ClaimOrchestrationService claimOrchestrationService;

    @BeforeEach
    void setUp() {
        claimOrchestrationService = new ClaimOrchestrationService(authorizationService, documentStorageService, policyClaimsDbService);
    }

    @Test
    void expired_authorizations_block_submission() {
        // Arrange
        ClaimInitiationPayload payload = new ClaimInitiationPayload("claim-123", "AUTH-EXPIRED-001");
        when(authorizationService.validateAuthorization("AUTH-EXPIRED-001")).thenReturn(false);
        when(authorizationService.isAuthorizationExpired("AUTH-EXPIRED-001")).thenReturn(true);

        // Act
        SubmissionResult result = claimOrchestrationService.transformAndRoute(payload);

        // Assert
        assertNotNull(result);
        assertEquals(SubmissionOutcome.BLOCKED, result.getOutcome());
        assertEquals("EXPIRED_AUTHORIZATION", result.getErrorCode());
        assertNull(result.getRoutingTarget());
        verify(authorizationService).isAuthorizationExpired("AUTH-EXPIRED-001");
        verifyNoInteractions(documentStorageService, policyClaimsDbService);
    }

    // Minimal DTOs and interfaces for test isolation
    static class ClaimInitiationPayload {
        private final String claimId;
        private final String authId;
        ClaimInitiationPayload(String claimId, String authId) { this.claimId = claimId; this.authId = authId; }
        String getAuthId() { return authId; }
        String getClaimId() { return claimId; }
    }

    enum SubmissionOutcome { PENDING, BLOCKED, SUCCESS }

    static class SubmissionResult {
        private final SubmissionOutcome outcome;
        private final String errorCode;
        private final String routingTarget;
        SubmissionResult(SubmissionOutcome outcome, String errorCode, String target) {
            this.outcome = outcome; this.errorCode = errorCode; this.routingTarget = target;
        }
        SubmissionOutcome getOutcome() { return outcome; }
        String getErrorCode() { return errorCode; }
        String getRoutingTarget() { return routingTarget; }
    }

    interface AuthorizationService {
        boolean validateAuthorization(String authId);
        boolean isAuthorizationExpired(String authId);
    }

    interface DocumentStorageService {
        String uploadDocument(String bucket, String key, byte[] data);
    }

    interface PolicyClaimsDbService {
        void saveClaimItem(String tableName, String pk, Object item);
    }

    static class ClaimOrchestrationService {
        private final AuthorizationService authorizationService;
        private final DocumentStorageService documentStorageService;
        private final PolicyClaimsDbService policyClaimsDbService;

        ClaimOrchestrationService(AuthorizationService auth, DocumentStorageService doc, PolicyClaimsDbService db) {
            this.authorizationService = auth;
            this.documentStorageService = doc;
            this.policyClaimsDbService = db;
        }

        SubmissionResult transformAndRoute(ClaimInitiationPayload payload) {
            if (!authorizationService.validateAuthorization(payload.getAuthId())) {
                if (authorizationService.isAuthorizationExpired(payload.getAuthId())) {
                    return new SubmissionResult(SubmissionOutcome.BLOCKED, "EXPIRED_AUTHORIZATION", null);
                }
                return new SubmissionResult(SubmissionOutcome.BLOCKED, "INVALID_AUTHORIZATION", null);
            }
            // Transform & route logic would proceed here for valid authorizations
            return new SubmissionResult(SubmissionOutcome.PENDING, null, "routing-target");
        }
    }
}
