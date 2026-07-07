package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PurposeIdentifyLikelyDuplicateClaimsAndScoreMatchTest {

    @Mock
    private ClaimHistoryRepository claimHistoryRepository;

    @Mock
    private ReserveLineService reserveLineService;

    @Mock
    private ComplianceLogger complianceLogger;

    @InjectMocks
    private DecisionOrchestrator decisionOrchestrator;

    @Test
    void purpose_identify_likely_duplicate_claims_and_score_match_confidence() {
        // Arrange: Define input payload for duplicate detection
        String insuredId = "INS-98765";
        String incidentDate = "2023-10-15";
        String exposureId = "EXP-11223";

        // Mock existing claim data retrieved from DynamoDB persistence layer
        Claim existingClaim = new Claim();
        existingClaim.setInsuredId(insuredId);
        existingClaim.setIncidentDate(incidentDate);
        existingClaim.setExposureId(exposureId);
        existingClaim.setReserveAmount(BigDecimal.valueOf(5000.00));
        existingClaim.setCurrency("USD");
        existingClaim.setApprovalStatus("Approved");

        when(claimHistoryRepository.findByInsuredAndDateRange(eq(insuredId), anyString(), anyString()))
                .thenReturn(List.of(existingClaim));

        // Mock reserve line financial bucket lookup
        ReserveLine reserveLine = new ReserveLine();
        reserveLine.setReserveId("RES-001");
        reserveLine.setExposureId(exposureId);
        reserveLine.setAmount(BigDecimal.valueOf(5000.00));
        reserveLine.setCurrency("USD");
        reserveLine.setApprovalStatus("Approved");

        when(reserveLineService.findByExposureId(exposureId)).thenReturn(Optional.of(reserveLine));

        // Act: Execute orchestration decision engine
        DecisionResult result = decisionOrchestrator.evaluateDuplicateConfidence(insuredId, incidentDate, exposureId);

        // Assert: Verify duplicate identification and confidence scoring
        assertNotNull(result, "Decision result must not be null");
        assertTrue(result.isLikelyDuplicate(), "Orchestrator should identify likely duplicate claims");
        assertTrue(result.getMatchConfidenceScore() >= 0.75, "Confidence score should indicate high match certainty");

        // NFR: Observability & Compliance (GDPR/SOC2) - verify structured audit logging
        verify(complianceLogger, times(1)).logStructuredEvent(eq("DUPLICATE_DETECTION"), any());

        // NFR: Security & Input Validation - ensure mocked I/O remains isolated from production endpoints
        verifyNoMoreInteractions(claimHistoryRepository, reserveLineService);
    }
}
