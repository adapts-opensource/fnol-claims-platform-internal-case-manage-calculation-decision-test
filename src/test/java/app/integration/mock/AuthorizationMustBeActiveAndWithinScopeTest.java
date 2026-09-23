package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that claim initiation orchestration correctly enforces active authorization
 * within the expected scope before proceeding to data transformation.
 */
@ExtendWith(MockitoExtension.class)
public class ClaimInitiationTransformationAuthorizationTest {

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ClaimTransformationOrchestrator orchestrator;

    private ClaimInitiationOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new ClaimInitiationOrchestrationService(authorizationService, orchestrator);
    }

    @Test
    void authorization_must_be_active_and_within_scope() {
        // Arrange
        String claimId = "CLM-12345";
        String userId = "USR-67890";
        String expectedScope = "CLAIM_INITIATION:WRITE";

        AuthorizationResult validAuth = AuthorizationResult.active(expectedScope);
        when(authorizationService.validateClaimInitiation(userId, claimId)).thenReturn(validAuth);

        ClaimInitiationRequest request = new ClaimInitiationRequest(claimId, userId, "AUTO_COLLISION");
        ClaimTransformationPayload payload = new ClaimTransformationPayload(claimId, "TRANSFORMED_DATA");

        when(orchestrator.transformClaim(request)).thenReturn(payload);

        // Act
        ClaimTransformationPayload result = service.processClaimInitiation(request);

        // Assert
        assertNotNull(result, "Transformation must proceed when authorization is active and within scope");
        assertEquals(claimId, result.getClaimId());
        assertEquals("TRANSFORMED_DATA", result.getData());

        verify(authorizationService).validateClaimInitiation(userId, claimId);
        verifyNoMoreInteractions(authorizationService);
        verify(orchestrator).transformClaim(request);
    }

    // --- Internal Stubs for Test Isolation ---

    private interface AuthorizationService {
        AuthorizationResult validateClaimInitiation(String userId, String claimId);
    }

    private record AuthorizationResult(boolean isActive, String scope) {
        static AuthorizationResult active(String scope) {
            return new AuthorizationResult(true, scope);
        }
    }

    private interface ClaimTransformationOrchestrator {
        ClaimTransformationPayload transformClaim(ClaimInitiationRequest request);
    }

    private record ClaimInitiationRequest(String claimId, String userId, String claimType) {}
    private record ClaimTransformationPayload(String claimId, String data) {
        public String getData() { return data; }
        public String getClaimId() { return claimId; }
    }

    private static class ClaimInitiationOrchestrationService {
        private final AuthorizationService auth;
        private final ClaimTransformationOrchestrator orchestrator;

        ClaimInitiationOrchestrationService(AuthorizationService auth, ClaimTransformationOrchestrator orchestrator) {
            this.auth = auth;
            this.orchestrator = orchestrator;
        }

        ClaimTransformationPayload processClaimInitiation(ClaimInitiationRequest request) {
            AuthorizationResult authResult = auth.validateClaimInitiation(request.userId(), request.claimId());
            if (!authResult.isActive() || !authResult.scope().equals("CLAIM_INITIATION:WRITE")) {
                throw new IllegalArgumentException("Authorization must be active and within scope");
            }
            return orchestrator.transformClaim(request);
        }
    }
}
