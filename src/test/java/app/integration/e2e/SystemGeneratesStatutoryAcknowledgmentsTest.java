package app.integration.e2e;

import app.models.ClaimInitiationRoutingDecisionValidation;
import app.services.ClaimManagementService;
import app.services.AcknowledgmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SystemGeneratesStatutoryAcknowledgments")
class UsFnol03SystemGeneratesStatutoryAcknowledgmentsTest {

    private ClaimManagementService claimManagementService;
    private AcknowledgmentService acknowledgmentService;
    private String testClaimId;
    private Instant claimInitiationTime;

    // Constants sidecar fixtures loaded for E2E inputs
    private static final String EXPECTED_STATUS_ACTIVE = "ACTIVE";
    private static final String EXPECTED_STATUS_COVERAGE_REVIEW = "COVERAGE_REVIEW";
    private static final int STATUTORY_WINDOW_HOURS = 24;
    private static final String JURISDICTION = "US_FNL";

    @BeforeEach
    void setUp() {
        claimManagementService = new ClaimManagementService();
        acknowledgmentService = new AcknowledgmentService();
        testClaimId = UUID.randomUUID().toString();
        claimInitiationTime = Instant.now();
    }

    @Test
    @DisplayName("us_fnol_03_system_generates_statutory_acknowledgments")
    void testSystemGeneratesStatutoryAcknowledgmentsWhenStatusChangesToActive() {
        // Build inputs from test-case Inputs and Constants JSON sidecar
        Map<String, Object> claimPayload = Map.of(
            "id", testClaimId,
            "status", EXPECTED_STATUS_ACTIVE,
            "initiatedAt", claimInitiationTime.toString(),
            "jurisdiction", JURISDICTION
        );

        ClaimInitiationRoutingDecisionValidation claimInitiation = new ClaimInitiationRoutingDecisionValidation();
        claimInitiation.setId(testClaimId);
        claimInitiation.setPayload(claimPayload);

        // Act: Trigger claim initiation and status change via real services
        claimManagementService.initiateClaim(claimInitiation);
        claimManagementService.updateStatus(testClaimId, EXPECTED_STATUS_ACTIVE);

        // Assert: Verify acknowledgments sent within statutory window
        Instant acknowledgmentSentTime = acknowledgmentService.getLastAcknowledgmentTimestamp(testClaimId);
        assertNotNull(acknowledgmentSentTime, "Acknowledgment should be generated");

        Duration statutoryWindow = Duration.ofHours(STATUTORY_WINDOW_HOURS);
        Duration actualDelay = Duration.between(claimInitiationTime, acknowledgmentSentTime);
        assertTrue(actualDelay.compareTo(statutoryWindow) <= 0,
            "Acknowledgment must be sent within the statutory window of " + statutoryWindow);
    }

    @Test
    @DisplayName("us_fnol_03_system_generates_statutory_acknowledgments_coverage_review")
    void testSystemGeneratesStatutoryAcknowledgmentsWhenStatusChangesToCoverageReview() {
        // Build inputs from test-case Inputs and Constants JSON sidecar
        Map<String, Object> claimPayload = Map.of(
            "id", testClaimId,
            "status", EXPECTED_STATUS_COVERAGE_REVIEW,
            "initiatedAt", claimInitiationTime.toString(),
            "jurisdiction", JURISDICTION
        );

        ClaimInitiationRoutingDecisionValidation claimInitiation = new ClaimInitiationRoutingDecisionValidation();
        claimInitiation.setId(testClaimId);
        claimInitiation.setPayload(claimPayload);

        // Act
        claimManagementService.initiateClaim(claimInitiation);
        claimManagementService.updateStatus(testClaimId, EXPECTED_STATUS_COVERAGE_REVIEW);

        // Assert
        Instant acknowledgmentSentTime = acknowledgmentService.getLastAcknowledgmentTimestamp(testClaimId);
        assertNotNull(acknowledgmentSentTime, "Acknowledgment should be generated for Coverage Review status");

        Duration statutoryWindow = Duration.ofHours(STATUTORY_WINDOW_HOURS);
        Duration actualDelay = Duration.between(claimInitiationTime, acknowledgmentSentTime);
        assertTrue(actualDelay.compareTo(statutoryWindow) <= 0,
            "Acknowledgment must be sent within the statutory window of " + statutoryWindow);
    }
}
