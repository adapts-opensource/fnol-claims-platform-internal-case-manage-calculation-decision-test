package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimInitiationOrchestrationMockTest {

    @Mock
    private AuthorizationService authorizationService;

    private ClaimTransformationService transformationService;

    @BeforeEach
    void setUp() {
        transformationService = new ClaimTransformationService(authorizationService);
    }

    @Test
    void inputCriteriaRequiredFieldsValidWithNonExpiredTokenAndMatchingInsuredIdShouldTransformSuccessfully() {
        // Arrange
        String reporterId = "REP-12345";
        String insuredId = "INS-67890";
        String authToken = "AUTH-TOKEN-XYZ";
        Instant submissionTimestamp = Instant.now();
        Instant authorizationExpiryDate = Instant.now().plusSeconds(3600);
        Map<String, String> scopeOfAuthorization = Map.of("scope", "FNOL_INITIATION");

        when(authorizationService.isTokenExpired(authToken, submissionTimestamp)).thenReturn(false);
        when(authorizationService.getInsuredId(authToken)).thenReturn(insuredId);
        when(authorizationService.checkRealTimeStatus(authToken)).thenReturn(AuthorizationStatus.ACTIVE);

        // Act
        TransformedClaim result = transformationService.transform(
                reporterId, insuredId, authToken, submissionTimestamp, authorizationExpiryDate, scopeOfAuthorization
        );

        // Assert
        assertNotNull(result);
        assertEquals(insuredId, result.insuredId());
        assertEquals(reporterId, result.reporterId());
        assertEquals(submissionTimestamp, result.submissionTimestamp());
        verify(authorizationService, times(1)).checkRealTimeStatus(authToken);
        verify(authorizationService, times(1)).getInsuredId(authToken);
        verify(authorizationService, times(1)).isTokenExpired(authToken, submissionTimestamp);
    }

    @Test
    void missingRequiredFieldsShouldThrowValidationException() {
        assertThrows(IllegalArgumentException.class, () ->
            transformationService.transform(null, "INS-67890", "token", Instant.now(), null, null)
        );
    }

    @Test
    void expiredTokenShouldThrowValidationException() {
        Instant pastTimestamp = Instant.now().minusSeconds(7200);
        when(authorizationService.isTokenExpired(anyString(), any(Instant.class))).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
            transformationService.transform("REP-123", "INS-456", "token", pastTimestamp, null, null)
        );
    }

    @Test
    void mismatchedInsuredIdShouldThrowValidationException() {
        when(authorizationService.getInsuredId("token")).thenReturn("INS-DIFFERENT");

        assertThrows(IllegalArgumentException.class, () ->
            transformationService.transform("REP-123", "INS-456", "token", Instant.now(), null, null)
        );
    }

    @Test
    void staleAuthorizationStatusShouldThrowValidationException() {
        when(authorizationService.checkRealTimeStatus("token")).thenReturn(AuthorizationStatus.STALE);

        assertThrows(IllegalStateException.class, () ->
            transformationService.transform("REP-123", "INS-456", "token", Instant.now(), null, null)
        );
    }
}

// Supporting types for compilation and mock isolation
record ClaimInitiationRequest(String reporterId, String insuredId, String authorizationToken, Instant submissionTimestamp, Instant authorizationExpiryDate, Map<String, String> scopeOfAuthorization) {}
record TransformedClaim(String claimId, String insuredId, String reporterId, Instant submissionTimestamp, String status) {}

enum AuthorizationStatus { ACTIVE, STALE, REVOKED }

class AuthorizationService {
    public boolean isTokenExpired(String token, Instant submissionTimestamp) { return false; }
    public String getInsuredId(String token) { return null; }
    public AuthorizationStatus checkRealTimeStatus(String token) { return AuthorizationStatus.ACTIVE; }
}

class ClaimTransformationService {
    private final AuthorizationService authorizationService;

    ClaimTransformationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    TransformedClaim transform(String reporterId, String insuredId, String authToken, Instant submissionTimestamp, Instant authorizationExpiryDate, Map<String, String> scopeOfAuthorization) {
        if (reporterId == null || insuredId == null || authToken == null || submissionTimestamp == null) {
            throw new IllegalArgumentException("Required fields missing: reporter_id, insured_id, authorization_token, submission_timestamp");
        }

        if (authorizationService.isTokenExpired(authToken, submissionTimestamp)) {
            throw new IllegalArgumentException("Validation failed: token must be non-expired");
        }

        String recordInsuredId = authorizationService.getInsuredId(authToken);
        if (!insuredId.equals(recordInsuredId)) {
            throw new IllegalArgumentException("Validation failed: insured_id must match authorization record");
        }

        AuthorizationStatus status = authorizationService.checkRealTimeStatus(authToken);
        if (status != AuthorizationStatus.ACTIVE) {
            throw new IllegalStateException("Freshness requirement failed: authorization status must be checked in real-time and must be ACTIVE");
        }

        return new TransformedClaim("CLM-" + System.currentTimeMillis(), insuredId, reporterId, submissionTimestamp, "INITIATED");
    }
}
