package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

public class ClaimDataStandardizationEnrichmentDecisionTest {

    @Mock
    private ClaimAssignmentRepository claimAssignmentRepository;

    private ClaimEnrichmentDecisionService enrichmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enrichmentService = new ClaimEnrichmentDecisionService(claimAssignmentRepository);
    }

    @Test
    void applies_when_claim_assigned_to_examiner() {
        // Arrange
        String claimId = "claim-101";
        String examinerId = "examiner-202";
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("id", claimId);
        inputPayload.put("assignedTo", examinerId);
        inputPayload.put("originalStatus", "NEW");

        when(claimAssignmentRepository.isAssignedToExaminer(claimId)).thenReturn(true);

        // Act
        Map<String, Object> enrichedPayload = enrichmentService.processDecision(inputPayload);

        // Assert
        assertNotNull(enrichedPayload);
        assertTrue(enrichedPayload.containsKey("decisionValidation"));
        assertEquals("EXAMINER_APPLIES", enrichedPayload.get("decisionValidation"));
        assertEquals("ENRICHED", enrichedPayload.get("standardizationStatus"));
        verify(claimAssignmentRepository).isAssignedToExaminer(claimId);
    }

    // Minimal stubs to support mock testing without external dependencies
    private interface ClaimAssignmentRepository {
        boolean isAssignedToExaminer(String claimId);
    }

    private static class ClaimEnrichmentDecisionService {
        private final ClaimAssignmentRepository repository;

        ClaimEnrichmentDecisionService(ClaimAssignmentRepository repository) {
            this.repository = repository;
        }

        Map<String, Object> processDecision(Map<String, Object> payload) {
            String claimId = (String) payload.get("id");
            if (repository.isAssignedToExaminer(claimId)) {
                Map<String, Object> result = new HashMap<>(payload);
                result.put("decisionValidation", "EXAMINER_APPLIES");
                result.put("standardizationStatus", "ENRICHED");
                return result;
            }
            return payload;
        }
    }
}
