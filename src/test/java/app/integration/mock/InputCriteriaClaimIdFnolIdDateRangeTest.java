package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementDecisionTransformationTest {

    @Mock
    private DecisionTransformationGateway mockGateway;

    private InsuredEngagementDecisionTransformer underTest;

    @BeforeEach
    void setUp() {
        underTest = new InsuredEngagementDecisionTransformer(mockGateway);
    }

    @Test
    void input_criteria_claim_id_fnol_id_date_range_event_types() {
        // Arrange
        String claimId = "CLM-2024-001";
        String fnolId = "FNOL-2024-001";
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        List<String> eventTypes = Arrays.asList("CLAIM_SUBMITTED", "CLAIM_REVIEWED");

        DecisionInput criteria = new DecisionInput(claimId, fnolId, startDate, endDate, eventTypes);
        DecisionOutput expectedOutput = new DecisionOutput("TRANSFORMED", "decision-payload-123");

        when(mockGateway.fetchAndTransform(any(DecisionInput.class))).thenReturn(expectedOutput);

        // Act
        DecisionOutput actualOutput = underTest.processDecision(criteria);

        // Assert
        assertNotNull(actualOutput);
        assertEquals("TRANSFORMED", actualOutput.getStatus());
        assertEquals("decision-payload-123", actualOutput.getPayload());
        verify(mockGateway, times(1)).fetchAndTransform(criteria);
    }

    // Minimal domain models for test isolation
    record DecisionInput(String claimId, String fnolId, LocalDate startDate, LocalDate endDate, List<String> eventTypes) {}
    record DecisionOutput(String status, String payload) {}

    interface DecisionTransformationGateway {
        DecisionOutput fetchAndTransform(DecisionInput input);
    }

    class InsuredEngagementDecisionTransformer {
        private final DecisionTransformationGateway gateway;

        InsuredEngagementDecisionTransformer(DecisionTransformationGateway gateway) {
            this.gateway = gateway;
        }

        DecisionOutput processDecision(DecisionInput input) {
            return gateway.fetchAndTransform(input);
        }
    }
}
