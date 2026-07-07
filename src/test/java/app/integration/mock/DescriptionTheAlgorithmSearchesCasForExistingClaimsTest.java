package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

interface CasSearchClient {
    List<Map<String, Object>> searchForExistingClaims(String policyNumber, String riskAddress, String dateOfLoss, String causeOfLoss);
}

interface DuplicateScorer {
    double calculateDuplicateScore(Map<String, Object> inputPayload, List<Map<String, Object>> existingClaims);
}

interface FlaggingService {
    boolean shouldFlagForReview(double score);
}

interface RulesEngineDecisionService {
    void persistDecision(Map<String, Object> decision);
}

class ClaimDataStandardizationDecisionTransformer {
    private final CasSearchClient casSearchClient;
    private final DuplicateScorer duplicateScorer;
    private final FlaggingService flaggingService;
    private final RulesEngineDecisionService rulesEngineService;

    ClaimDataStandardizationDecisionTransformer(CasSearchClient casSearchClient, DuplicateScorer duplicateScorer, FlaggingService flaggingService, RulesEngineDecisionService rulesEngineService) {
        this.casSearchClient = casSearchClient;
        this.duplicateScorer = duplicateScorer;
        this.flaggingService = flaggingService;
        this.rulesEngineService = rulesEngineService;
    }

    Map<String, Object> transform(Map<String, Object> inputPayload) {
        String policyNumber = (String) inputPayload.get("policy_number");
        String riskAddress = (String) inputPayload.get("risk_address");
        String dateOfLoss = (String) inputPayload.get("date_of_loss");
        String causeOfLoss = (String) inputPayload.get("cause_of_loss");

        List<Map<String, Object>> existingClaims = casSearchClient.searchForExistingClaims(policyNumber, riskAddress, dateOfLoss, causeOfLoss);
        double score = duplicateScorer.calculateDuplicateScore(inputPayload, existingClaims);
        boolean flagged = flaggingService.shouldFlagForReview(score);

        Map<String, Object> payload = Map.of(
            "duplicate_score", score,
            "is_flagged_for_review", flagged,
            "matching_fields", List.of("policy_number", "risk_address", "date_of_loss", "cause_of_loss")
        );

        Map<String, Object> decision = Map.of("id", "DEC-TRANS-001", "payload", payload);
        rulesEngineService.persistDecision(decision);
        return payload;
    }
}

class ClaimDataStandardizationDecisionTransformationTest {

    @Mock
    private CasSearchClient casSearchClient;

    @Mock
    private DuplicateScorer duplicateScorer;

    @Mock
    private FlaggingService flaggingService;

    @Mock
    private RulesEngineDecisionService rulesEngineService;

    private ClaimDataStandardizationDecisionTransformer transformer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transformer = new ClaimDataStandardizationDecisionTransformer(
            casSearchClient, duplicateScorer, flaggingService, rulesEngineService
        );
    }

    @Test
    void description_the_algorithm_searches_cas_for_existing_claims_using_policy_number_risk_address_date_of_loss_cause_of_loss_and_other_fields_it_calculates_a_duplicate_score_based_on_field_matches_it_flags_potential_duplicates_for_review() {
        // Arrange
        String policyNumber = "POL-987654";
        String riskAddress = "456 Oak Ave";
        String dateOfLoss = "2024-01-15";
        String causeOfLoss = "Theft";

        Map<String, Object> inputPayload = Map.of(
            "policy_number", policyNumber,
            "risk_address", riskAddress,
            "date_of_loss", dateOfLoss,
            "cause_of_loss", causeOfLoss
        );

        Map<String, Object> existingClaimRecord = Map.of("claim_id", "CAS-EXIST-1", "policy_number", policyNumber);
        when(casSearchClient.searchForExistingClaims(policyNumber, riskAddress, dateOfLoss, causeOfLoss))
                .thenReturn(List.of(existingClaimRecord));

        double calculatedScore = 0.78;
        when(duplicateScorer.calculateDuplicateScore(inputPayload, List.of(existingClaimRecord)))
                .thenReturn(calculatedScore);

        boolean shouldFlag = true;
        when(flaggingService.shouldFlagForReview(calculatedScore))
                .thenReturn(shouldFlag);

        // Act
        Map<String, Object> transformedPayload = transformer.transform(inputPayload);

        // Assert
        assertNotNull(transformedPayload, "Transformed payload must not be null");
        assertEquals(calculatedScore, transformedPayload.get("duplicate_score"), 0.001, "Duplicate score must match calculated value");
        assertEquals(shouldFlag, transformedPayload.get("is_flagged_for_review"), "Flag status must match flagging service result");
        assertTrue(transformedPayload.containsKey("matching_fields"), "Payload must contain matching fields metadata");

        // Verify external interactions
        verify(casSearchClient, times(1)).searchForExistingClaims(policyNumber, riskAddress, dateOfLoss, causeOfLoss);
        verify(duplicateScorer, times(1)).calculateDuplicateScore(inputPayload, List.of(existingClaimRecord));
        verify(flaggingService, times(1)).shouldFlagForReview(calculatedScore);

        ArgumentCaptor<Map<String, Object>> decisionCaptor = ArgumentCaptor.forClass(Map.class);
        verify(rulesEngineService, times(1)).persistDecision(decisionCaptor.capture());
        Map<String, Object> capturedDecision = decisionCaptor.getValue();
        assertNotNull(capturedDecision.get("id"), "Decision must have a valid id");
        assertTrue(capturedDecision.containsKey("payload"), "Decision must contain the transformed payload");
    }
}
