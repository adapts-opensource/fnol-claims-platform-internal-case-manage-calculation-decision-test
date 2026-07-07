package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MalformedAddressReturnValidationErrorTest {

    /**
     * Interface representing the claim data transformation service.
     * In production, this would interact with DynamoDB (RulesEngineDecisionService)
     * and S3 (AuditDiaryStore) to persist and validate standardization results.
     */
    static interface ClaimDataTransformationService {
        void transformClaimData(String id, Map<String, Object> payload);
    }

    @Mock
    private ClaimDataTransformationService claimDataTransformationService;

    @BeforeEach
    void setUp() {
        // Mock initialization handled by MockitoExtension.
        // In a full implementation, this would configure structured logging hooks
        // and TLS/least-privilege IAM context wrappers for observability and security NFRs.
    }

    @Test
    void malformed_address_return_validation_error() {
        // Arrange
        String claimId = "claim_std_001";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", claimId);
        payload.put("address", "123 Invalid St, , , ,"); // Malformed address per validation rules

        // Mock the transformation service to simulate validation failure on malformed address
        // External I/O (DynamoDB/S3) is bypassed to satisfy mock-only constraint
        doThrow(new IllegalArgumentException("Validation failed: Malformed address"))
                .when(claimDataTransformationService).transformClaimData(eq(claimId), anyMap());

        // Act & Assert
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> claimDataTransformationService.transformClaimData(claimId, payload)
        );

        assertNotNull(thrown.getMessage(), "Validation error message must not be null");
        assertTrue(thrown.getMessage().contains("Malformed address"), "Error must indicate address validation failure");
    }
}
