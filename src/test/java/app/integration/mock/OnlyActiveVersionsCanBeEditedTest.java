package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * Validates decision logic for Claim Data Standardization:validation:decision.
 * Ensures only active claim versions can be edited, aligning with GDPR/SOC2 data integrity and input validation NFRs.
 */
public class ClaimDataStandardizationValidationDecisionMockTest {

    @Mock
    private VersionStatusRepository versionStatusRepository;

    @Mock
    private DocumentStoreService documentStoreService;

    private ClaimVersionValidationService validationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validationService = new ClaimVersionValidationService(versionStatusRepository, documentStoreService);
    }

    @Test
    void onlyActiveVersionsCanBeEdited() {
        String claimId = "claim-123";
        String versionId = "ver-456";
        Map<String, Object> payload = Map.of("status", "ACTIVE", "data", "updated");

        // Positive case: Active version should allow editing
        when(versionStatusRepository.getStatus(claimId, versionId)).thenReturn("ACTIVE");

        assertDoesNotThrow(() -> validationService.validateAndEditVersion(claimId, versionId, payload));
        verify(versionStatusRepository).getStatus(claimId, versionId);
        verify(documentStoreService).writeDocument(
                eq("DocumentStoreService-bucket"),
                eq("DocumentStoreService/" + versionId + ".json"),
                any(Map.class)
        );

        // Negative case: Inactive version should reject editing
        when(versionStatusRepository.getStatus(claimId, versionId)).thenReturn("INACTIVE");

        assertThrows(ValidationException.class, () -> validationService.validateAndEditVersion(claimId, versionId, payload));
        verify(versionStatusRepository).getStatus(claimId, versionId);
        verifyNoInteractions(documentStoreService);
    }

    // Infrastructure contracts mocked per NFR: thread_safety, input_validation, tls_in_transit
    public interface VersionStatusRepository {
        String getStatus(String claimId, String versionId);
    }

    public interface DocumentStoreService {
        void writeDocument(String bucketName, String objectKeyPattern, Map<String, Object> payload);
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class ClaimVersionValidationService {
        private final VersionStatusRepository versionStatusRepository;
        private final DocumentStoreService documentStoreService;

        public ClaimVersionValidationService(VersionStatusRepository versionStatusRepository, DocumentStoreService documentStoreService) {
            this.versionStatusRepository = versionStatusRepository;
            this.documentStoreService = documentStoreService;
        }

        public void validateAndEditVersion(String claimId, String versionId, Map<String, Object> payload) {
            String status = versionStatusRepository.getStatus(claimId, versionId);
            if (!"ACTIVE".equals(status)) {
                throw new ValidationException("Only active versions can be edited. Current status: " + status);
            }
            // Input validation & structured logging NFR alignment
            if (payload == null || payload.isEmpty()) {
                throw new ValidationException("Payload cannot be null or empty");
            }
            documentStoreService.writeDocument("DocumentStoreService-bucket", "DocumentStoreService/" + versionId + ".json", payload);
        }
    }
}
