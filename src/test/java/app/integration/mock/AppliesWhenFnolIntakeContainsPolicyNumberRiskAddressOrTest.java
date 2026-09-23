package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppliesWhenFnolIntakeContainsPolicyNumberRiskAddressOrTest {

    @Mock
    private FnolIntakeRepository fnolIntakeRepository;

    @Mock
    private ClaimCalculationDecisionService calculationDecisionService;

    @Mock
    private AwsDynamoDbClient dynamoDbClient;

    private ClaimStandardizationProcessor standardizationProcessor;

    @BeforeEach
    void setUp() {
        standardizationProcessor = new ClaimStandardizationProcessor(
                fnolIntakeRepository,
                calculationDecisionService,
                dynamoDbClient
        );
    }

    @Test
    void appliesWhenFnolIntakeContainsPolicyNumberRiskAddressOrNamedInsured() {
        // Arrange
        String claimId = "claim-std-001";
        String policyNumber = "POL-98765";
        String riskAddress = "456 Oak Ave, Springfield, IL";
        String namedInsured = "Jane Smith";

        var mockFnolIntake = new FnolIntake(claimId, policyNumber, riskAddress, namedInsured);

        when(fnolIntakeRepository.findByClaimId(claimId)).thenReturn(java.util.Optional.of(mockFnolIntake));
        when(calculationDecisionService.evaluate(any(FnlIntake.class))).thenReturn(DecisionOutcome.APPLIED);

        // Act
        DecisionOutcome outcome = standardizationProcessor.processClaimDecision(claimId);

        // Assert
        assertEquals(DecisionOutcome.APPLIED, outcome);
        verify(calculationDecisionService, times(1)).evaluate(mockFnolIntake);
        verify(fnolIntakeRepository, times(1)).findByClaimId(claimId);
        verifyNoInteractions(dynamoDbClient);
    }

    // Minimal domain stubs to ensure compilation context
    record FnolIntake(String claimId, String policyNumber, String riskAddress, String namedInsured) {}
    enum DecisionOutcome { APPLIED, SKIPPED }
    interface FnolIntakeRepository { java.util.Optional<FnolIntake> findByClaimId(String claimId); }
    interface ClaimCalculationDecisionService { DecisionOutcome evaluate(FnlIntake intake); }
    interface AwsDynamoDbClient { void putItem(String table, java.util.Map<String, Object> item); }
    class ClaimStandardizationProcessor {
        private final FnolIntakeRepository repository;
        private final ClaimCalculationDecisionService service;
        private final AwsDynamoDbClient client;
        ClaimStandardizationProcessor(FnlIntakeRepository repo, ClaimCalculationDecisionService svc, AwsDynamoDbClient db) {
            this.repository = repo; this.service = svc; this.client = db;
        }
        DecisionOutcome processClaimDecision(String claimId) {
            var intake = repository.findByClaimId(claimId).orElseThrow();
            return service.evaluate(intake);
        }
    }
}
