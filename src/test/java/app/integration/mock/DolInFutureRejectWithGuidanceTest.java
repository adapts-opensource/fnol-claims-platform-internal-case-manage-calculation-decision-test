package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementDecisionTransformationMockTest {

    @Mock
    private DecisionTransformationService decisionService;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private DataPersistenceService persistenceService;

    private InsuredEngagementTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new InsuredEngagementTransformer(decisionService, communicationService, persistenceService);
    }

    @Test
    void dol_in_future_reject_with_guidance() {
        // Arrange
        String claimId = "CLM-DOL-FUTURE-001";
        String futureDol = "2099-12-31";
        String expectedGuidance = "Date of Loss cannot be in the future. Please verify incident details and resubmit.";

        when(decisionService.evaluate(any(IncidentPayload.class)))
                .thenReturn(DecisionOutcome.reject(expectedGuidance));

        // Act
        TransformationResult result = transformer.processClaim(claimId, futureDol);

        // Assert
        assertNotNull(result);
        assertEquals(DecisionOutcome.Status.REJECTED, result.status());
        assertEquals(expectedGuidance, result.guidance());

        verify(decisionService).evaluate(any(IncidentPayload.class));
        verify(communicationService).sendGuidance(eq(claimId), eq(expectedGuidance));
        verify(persistenceService).persistDecision(eq(claimId), eq(DecisionOutcome.Status.REJECTED), eq(expectedGuidance));
    }
}

// Supporting types for mock integration context
record IncidentPayload(String claimId, String dol) {}
record DecisionOutcome(DecisionOutcome.Status status, String guidance) {
    enum Status { APPROVED, REJECTED, PENDING }
    static DecisionOutcome reject(String guidance) { return new DecisionOutcome(Status.REJECTED, guidance); }
}
record TransformationResult(DecisionOutcome.Status status, String guidance) {}

interface DecisionTransformationService {
    DecisionOutcome evaluate(IncidentPayload payload);
}
interface CommunicationService {
    void sendGuidance(String claimId, String guidanceText);
}
interface DataPersistenceService {
    void persistDecision(String claimId, DecisionOutcome.Status status, String guidance);
}

class InsuredEngagementTransformer {
    private final DecisionTransformationService decisionService;
    private final CommunicationService communicationService;
    private final DataPersistenceService persistenceService;

    InsuredEngagementTransformer(DecisionTransformationService decisionService,
                                 CommunicationService communicationService,
                                 DataPersistenceService persistenceService) {
        this.decisionService = decisionService;
        this.communicationService = communicationService;
        this.persistenceService = persistenceService;
    }

    TransformationResult processClaim(String claimId, String dol) {
        IncidentPayload payload = new IncidentPayload(claimId, dol);
        DecisionOutcome outcome = decisionService.evaluate(payload);
        communicationService.sendGuidance(claimId, outcome.guidance());
        persistenceService.persistDecision(claimId, outcome.status(), outcome.guidance());
        return new TransformationResult(outcome.status(), outcome.guidance());
    }
}
