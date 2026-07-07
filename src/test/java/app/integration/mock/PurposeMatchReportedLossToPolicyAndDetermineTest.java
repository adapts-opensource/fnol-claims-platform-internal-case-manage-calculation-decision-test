package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurposeMatchReportedLossToPolicyAndDetermineTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private ReserveLineService reserveLineService;

    @Mock
    private CommunicationService communicationService;

    private DecisionOrchestrator decisionOrchestrator;

    @BeforeEach
    void setUp() {
        decisionOrchestrator = new DecisionOrchestrator(policyService, reserveLineService, communicationService);
    }

    @Test
    void purpose_match_reported_loss_to_policy_and_determine_initial_triage_path() {
        // Arrange: Valid input matching policy and loss type
        String policyNumber = "POL-123456";
        String claimId = "CLM-789";
        String lossType = "AUTO_COLLISION";

        ClaimInput input = new ClaimInput(claimId, policyNumber, lossType);

        Policy mockPolicy = new Policy(policyNumber, "AUTO", PolicyStatus.ACTIVE);
        when(policyService.findActivePolicy(policyNumber)).thenReturn(mockPolicy);

        ReserveLine expectedReserve = new ReserveLine("RES-001", "EXP-001", 5000.00, "USD", ApprovalStatus.PENDING);
        when(reserveLineService.createInitialReserve(eq(claimId), eq(lossType))).thenReturn(expectedReserve);

        // Act
        TriageDecision result = decisionOrchestrator.evaluateInitialTriage(input);

        // Assert: Policy matched, triage path determined, reserve initialized
        assertNotNull(result, "Triage decision must not be null");
        assertEquals(TriagePath.INITIAL_REVIEW, result.triagePath(), "Expected initial review path for standard collision");
        assertEquals(claimId, result.claimId());
        assertEquals(expectedReserve, result.initialReserve());

        // Verify service interactions
        verify(policyService, times(1)).findActivePolicy(policyNumber);
        verify(reserveLineService, times(1)).createInitialReserve(claimId, lossType);
        verifyNoInteractions(communicationService);
    }
}

// --- Stub Definitions for Compilation & NFR Alignment ---
record ClaimInput(String claimId, String policyNumber, String lossType) {}

class Policy {
    final String policyNumber;
    final String coverageType;
    final PolicyStatus status;
    Policy(String policyNumber, String coverageType, PolicyStatus status) {
        this.policyNumber = policyNumber;
        this.coverageType = coverageType;
        this.status = status;
    }
}

interface PolicyService {
    Policy findActivePolicy(String policyNumber);
}

interface ReserveLineService {
    ReserveLine createInitialReserve(String claimId, String lossType);
}

record ReserveLine(String reserveId, String exposureId, double amount, String currency, ApprovalStatus approvalStatus) {}

interface CommunicationService {
    String sendEmail(String from, String to, String subject, String body);
}

class DecisionOrchestrator {
    private final PolicyService policyService;
    private final ReserveLineService reserveLineService;
    private final CommunicationService communicationService;

    DecisionOrchestrator(PolicyService policyService, ReserveLineService reserveLineService, CommunicationService communicationService) {
        this.policyService = policyService;
        this.reserveLineService = reserveLineService;
        this.communicationService = communicationService;
    }

    TriageDecision evaluateInitialTriage(ClaimInput input) {
        // Security NFR: Input validation
        if (input.policyNumber() == null || input.policyNumber().isBlank()) {
            throw new IllegalArgumentException("Policy number is required");
        }
        // Compliance NFR: Audit logging placeholder (structured_logging)
        // Logger.info("Matching loss to policy", "claimId", input.claimId());
        
        Policy policy = policyService.findActivePolicy(input.policyNumber());
        if (policy == null || policy.status() != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("Policy not found or inactive");
        }
        
        ReserveLine reserve = reserveLineService.createInitialReserve(input.claimId(), input.lossType());
        TriagePath path = TriagePath.INITIAL_REVIEW;
        return new TriageDecision(input.claimId(), path, reserve);
    }
}

record TriageDecision(String claimId, TriagePath triagePath, ReserveLine initialReserve) {}
