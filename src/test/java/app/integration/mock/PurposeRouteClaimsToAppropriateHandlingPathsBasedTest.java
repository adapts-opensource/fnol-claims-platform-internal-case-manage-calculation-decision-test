package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ClaimRoutingDecisionEngineTest {

    enum HandlingPath { STANDARD, FAST_TRACK, MANUAL_REVIEW, FRAUD_INVESTIGATION, SPECIAL_HANDLING }
    enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    static class RiskProfile {
        final RiskLevel level;
        final double score;
        RiskProfile(RiskLevel level, double score) { this.level = level; this.score = score; }
    }

    static class ExposureData {
        final String exposureId;
        final String type;
        final double amount;
        ExposureData(String exposureId, String type, double amount) {
            this.exposureId = exposureId;
            this.type = type;
            this.amount = amount;
        }
    }

    interface RiskAssessmentService { RiskProfile assessRisk(String claimId); }
    interface ExposureLookupService { ExposureData lookupExposure(String claimId); }

    @Mock RiskAssessmentService riskAssessmentService;
    @Mock ExposureLookupService exposureLookupService;
    @InjectMocks ClaimRoutingDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new ClaimRoutingDecisionEngine(riskAssessmentService, exposureLookupService);
    }

    @Test
    void purpose_route_claims_to_appropriate_handling_paths_based_on_risk_and_exposure() {
        String claimId = "CLM-ORCH-001";

        // Scenario 1: Low risk + Auto exposure -> Fast Track
        when(riskAssessmentService.assessRisk(claimId)).thenReturn(new RiskProfile(RiskLevel.LOW, 0.15));
        when(exposureLookupService.lookupExposure(claimId)).thenReturn(new ExposureData("EXP-001", "AUTO", 4500.0));
        assertEquals(HandlingPath.FAST_TRACK, decisionEngine.routeClaim(claimId));

        // Scenario 2: Medium risk -> Standard
        when(riskAssessmentService.assessRisk(claimId)).thenReturn(new RiskProfile(RiskLevel.MEDIUM, 0.45));
        when(exposureLookupService.lookupExposure(claimId)).thenReturn(new ExposureData("EXP-002", "PROPERTY", 25000.0));
        assertEquals(HandlingPath.STANDARD, decisionEngine.routeClaim(claimId));

        // Scenario 3: High risk or High exposure -> Manual Review
        when(riskAssessmentService.assessRisk(claimId)).thenReturn(new RiskProfile(RiskLevel.HIGH, 0.75));
        when(exposureLookupService.lookupExposure(claimId)).thenReturn(new ExposureData("EXP-003", "LIABILITY", 120000.0));
        assertEquals(HandlingPath.MANUAL_REVIEW, decisionEngine.routeClaim(claimId));

        // Scenario 4: Critical risk or complex exposure -> Fraud Investigation
        when(riskAssessmentService.assessRisk(claimId)).thenReturn(new RiskProfile(RiskLevel.CRITICAL, 0.92));
        when(exposureLookupService.lookupExposure(claimId)).thenReturn(new ExposureData("EXP-004", "PROPERTY", 55000.0));
        assertEquals(HandlingPath.FRAUD_INVESTIGATION, decisionEngine.routeClaim(claimId));

        verify(riskAssessmentService, times(4)).assessRisk(claimId);
        verify(exposureLookupService, times(4)).lookupExposure(claimId);
    }

    @Test
    void purpose_route_claims_to_appropriate_handling_paths_based_on_risk_and_exposure_inputValidation() {
        assertThrows(IllegalArgumentException.class, () -> decisionEngine.routeClaim(null));
        assertThrows(IllegalArgumentException.class, () -> decisionEngine.routeClaim("   "));
        verifyZeroInteractions(riskAssessmentService, exposureLookupService);
    }
}

class ClaimRoutingDecisionEngine {
    private final RiskAssessmentService riskAssessmentService;
    private final ExposureLookupService exposureLookupService;

    ClaimRoutingDecisionEngine() { this(null, null); }
    ClaimRoutingDecisionEngine(RiskAssessmentService riskAssessmentService, ExposureLookupService exposureLookupService) {
        this.riskAssessmentService = riskAssessmentService;
        this.exposureLookupService = exposureLookupService;
    }

    HandlingPath routeClaim(String claimId) {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("Claim ID must not be null or blank");
        }
        RiskProfile risk = riskAssessmentService.assessRisk(claimId);
        ExposureData exposure = exposureLookupService.lookupExposure(claimId);

        if (risk.level == RiskLevel.CRITICAL || (risk.level == RiskLevel.HIGH && exposure.amount > 100000)) {
            return HandlingPath.FRAUD_INVESTIGATION;
        } else if (risk.level == RiskLevel.HIGH || risk.level == RiskLevel.MEDIUM) {
            return HandlingPath.MANUAL_REVIEW;
        } else if (risk.level == RiskLevel.LOW && "AUTO".equalsIgnoreCase(exposure.type)) {
            return HandlingPath.FAST_TRACK;
        }
        return HandlingPath.STANDARD;
    }
}
