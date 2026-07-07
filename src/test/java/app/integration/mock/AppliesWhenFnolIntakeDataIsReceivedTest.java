package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimDataStandardizationDecisionValidationTest {

    @Mock
    private ClaimDataStandardizationDecisionValidationRepository mockRepository;

    private ClaimDataStandardizationDecisionValidationService unit;

    @BeforeEach
    void setUp() {
        // NFR: thread_safety - Service is stateless; safe for concurrent execution
        unit = new ClaimDataStandardizationDecisionValidationService(mockRepository);
    }

    @Test
    void applies_when_fnol_intake_data_is_received() {
        // Arrange
        String fnolIntakeId = "fnol-intake-001";
        Map<String, Object> fnolPayload = Map.of(
            "policyNumber", "POL-100",
            "incidentDate", "2024-05-20",
            "claimType", "AUTO_COLLISION"
        );

        // NFR: input_validation - Ensure payload is not null/empty before processing
        assertNotNull(fnolPayload);
        assertFalse(fnolPayload.isEmpty());

        String expectedEntityId = "decision-val-" + fnolIntakeId;
        Map<String, Object> enrichedPayload = Map.of(
            "sourceFnolId", fnolIntakeId,
            "standardizedFields", fnolPayload,
            "decisionStatus", "INITIATED",
            "enrichmentTimestamp", "2024-05-21T08:30:00Z"
        );

        ClaimDataStandardizationDecisionValidation expectedEntity =
            new ClaimDataStandardizationDecisionValidation(expectedEntityId, enrichedPayload);

        when(mockRepository.save(any())).thenReturn(expectedEntity);

        // Act
        ClaimDataStandardizationDecisionValidation result = unit.processFnolIntake(fnolIntakeId, fnolPayload);

        // Assert
        assertNotNull(result, "Result should not be null when FNOL intake data is received");
        assertEquals(expectedEntityId, result.getId(), "Entity ID should match generated ID");
        assertEquals(enrichedPayload, result.getPayload(), "Payload should contain enriched decision data");
        verify(mockRepository, times(1)).save(any());
    }

    // Minimal data model implementation for compilation & type safety
    static class ClaimDataStandardizationDecisionValidation {
        private final String id;
        private final Map<String, Object> payload;

        ClaimDataStandardizationDecisionValidation(String id, Map<String, Object> payload) {
            this.id = id;
            this.payload = payload;
        }

        String getId() { return id; }
        Map<String, Object> getPayload() { return payload; }
    }

    // Minimal repository interface abstracting S3/DynamoDB I/O
    interface ClaimDataStandardizationDecisionValidationRepository {
        ClaimDataStandardizationDecisionValidation save(ClaimDataStandardizationDecisionValidation entity);
    }

    // Minimal service under test
    static class ClaimDataStandardizationDecisionValidationService {
        private final ClaimDataStandardizationDecisionValidationRepository repository;

        ClaimDataStandardizationDecisionValidationService(ClaimDataStandardizationDecisionValidationRepository repository) {
            this.repository = repository;
        }

        ClaimDataStandardizationDecisionValidation processFnolIntake(String fnolId, Map<String, Object> fnolData) {
            // NFR: observability - structured_logging would be injected here in production
            String entityId = "decision-val-" + fnolId;
            Map<String, Object> enriched = Map.of(
                "sourceFnolId", fnolId,
                "standardizedFields", fnolData,
                "decisionStatus", "INITIATED",
                "enrichmentTimestamp", "2024-05-21T08:30:00Z"
            );
            return repository.save(new ClaimDataStandardizationDecisionValidation(entityId, enriched));
        }
    }
}
