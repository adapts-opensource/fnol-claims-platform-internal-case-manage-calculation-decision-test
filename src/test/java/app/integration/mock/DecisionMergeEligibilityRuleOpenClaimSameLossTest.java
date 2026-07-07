package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionMergeEligibilityRuleOpenClaimSameLossTest {

    @Mock
    private ReserveRepository reserveRepository;
    @Mock
    private AuditTrailService auditTrailService;
    @Mock
    private MergeTaskService mergeTaskService;

    private DecisionTransformationService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new DecisionTransformationService(reserveRepository, auditTrailService, mergeTaskService);
    }

    @Test
    void decision_merge_eligibility_rule_open_claim_same_loss_details_merge_allowed_expected_outcome_create_merge_task_preserve_reserve_audit_trail() {
        // Arrange
        String targetClaimId = UUID.randomUUID().toString();
        String candidateClaimId = UUID.randomUUID().toString();
        String lossDetailId = "LOSS_DETAIL_001";
        String exposureId = "EXP_001";

        Claim targetClaim = new Claim(targetClaimId, lossDetailId, ClaimStatus.OPEN);
        Claim candidateClaim = new Claim(candidateClaimId, lossDetailId, ClaimStatus.OPEN);

        ReserveLine existingReserve = new ReserveLine(
                UUID.randomUUID().toString(), exposureId, new BigDecimal("2500.00"), "USD", ApprovalStatus.APPROVED
        );
        when(reserveRepository.findByClaimId(targetClaimId)).thenReturn(List.of(existingReserve));
        when(reserveRepository.findByClaimId(candidateClaimId)).thenReturn(List.of());

        // Act
        MergeEligibilityResult result = decisionService.evaluateMergeEligibility(targetClaim, candidateClaim);

        // Assert
        assertEquals(MergeDecision.MERGE_ALLOWED, result.decision());

        verify(mergeTaskService).createMergeTask(eq(targetClaimId), eq(candidateClaimId));

        verify(reserveRepository, never()).delete(any());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailService).logEvent(auditCaptor.capture());
        AuditEvent capturedAudit = auditCaptor.getValue();
        assertEquals(MergeDecision.MERGE_ALLOWED.toString(), capturedAudit.action());
        assertEquals(targetClaimId, capturedAudit.targetId());
        assertNotNull(capturedAudit.timestamp());
    }

    // Minimal domain stubs for self-contained testing
    enum ClaimStatus { OPEN, CLOSED }
    enum ApprovalStatus { PENDING, APPROVED, REJECTED }
    enum MergeDecision { MERGE_ALLOWED, MERGE_DENIED }

    record Claim(String id, String lossDetailId, ClaimStatus status) {}
    record ReserveLine(String reserveId, String exposureId, BigDecimal amount, String currency, ApprovalStatus approvalStatus) {}
    record AuditEvent(String action, String targetId, Instant timestamp) {}
    record MergeEligibilityResult(MergeDecision decision) {}

    interface ReserveRepository {
        List<ReserveLine> findByClaimId(String claimId);
        void delete(ReserveLine reserve);
    }

    interface AuditTrailService {
        void logEvent(AuditEvent event);
    }

    interface MergeTaskService {
        void createMergeTask(String targetClaimId, String candidateClaimId);
    }

    class DecisionTransformationService {
        private final ReserveRepository reserveRepository;
        private final AuditTrailService auditTrailService;
        private final MergeTaskService mergeTaskService;

        DecisionTransformationService(ReserveRepository reserveRepository, AuditTrailService auditTrailService, MergeTaskService mergeTaskService) {
            this.reserveRepository = reserveRepository;
            this.auditTrailService = auditTrailService;
            this.mergeTaskService = mergeTaskService;
        }

        MergeEligibilityResult evaluateMergeEligibility(Claim target, Claim candidate) {
            boolean openAndSameLoss = target.status() == ClaimStatus.OPEN
                    && candidate.status() == ClaimStatus.OPEN
                    && target.lossDetailId().equals(candidate.lossDetailId());

            if (openAndSameLoss) {
                mergeTaskService.createMergeTask(target.id(), candidate.id());
                auditTrailService.logEvent(new AuditEvent(
                        MergeDecision.MERGE_ALLOWED.toString(), target.id(), Instant.now()
                ));
                return new MergeEligibilityResult(MergeDecision.MERGE_ALLOWED);
            }
            return new MergeEligibilityResult(MergeDecision.MERGE_DENIED);
        }
    }
}
