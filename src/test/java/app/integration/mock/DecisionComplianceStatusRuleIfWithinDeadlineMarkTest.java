package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;

public class DecisionComplianceStatusRuleTest {

    @Mock
    private ComplianceAuditService complianceAuditService;

    @Mock
    private PolicyClaimsDb policyClaimsDb;

    private ClaimTransformationService transformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transformationService = new ClaimTransformationService(complianceAuditService, policyClaimsDb);
    }

    @Test
    void decisionComplianceStatusRuleIfWithinDeadlineMarkCompliantElseFlagNonCompliantExpectedOutcomeClearComplianceStatusPerFnol() {
        // Given: Claim initiated within compliance deadline
        Instant now = Instant.now();
        Claim claimWithinDeadline = new Claim("CLM-001", now, now.plusSeconds(7200));

        // When: Transformation orchestrates compliance status check
        Claim transformedClaim = transformationService.transform(claimWithinDeadline);

        // Then: Compliance status should be marked as COMPLIANT
        assertEquals(ComplianceStatus.COMPLIANT, transformedClaim.getComplianceStatus(),
                "Claim within deadline must be marked compliant per FNOL rule");

        // Verify external I/O triggers for audit and persistence
        verify(complianceAuditService).audit("ComplianceAuditService-bucket", Map.of("entity_id", "CLM-001", "status", "COMPLIANT"));
        verify(policyClaimsDb).persist(Map.of("pk", "CLM-001", "item", transformedClaim));

        // Given: Claim initiated past compliance deadline
        Claim claimExpired = new Claim("CLM-002", now.minusSeconds(7200), now.minusSeconds(3600));

        // When: Transformation orchestrates compliance status check
        Claim transformedExpiredClaim = transformationService.transform(claimExpired);

        // Then: Compliance status should be flagged as NON_COMPLIANT
        assertEquals(ComplianceStatus.NON_COMPLIANT, transformedExpiredClaim.getComplianceStatus(),
                "Claim past deadline must be flagged non-compliant per FNOL rule");

        // Verify external I/O triggers correctly for non-compliant case
        verify(complianceAuditService).audit("ComplianceAuditService-bucket", Map.of("entity_id", "CLM-002", "status", "NON_COMPLIANT"));
        verify(policyClaimsDb).persist(Map.of("pk", "CLM-002", "item", transformedExpiredClaim));
    }
}

// Supporting types for test isolation and compilation
enum ComplianceStatus { COMPLIANT, NON_COMPLIANT }

record Claim(String claimId, Instant initiationTimestamp, Instant deadlineTimestamp, ComplianceStatus complianceStatus) {
    Claim(String claimId, Instant initiationTimestamp, Instant deadlineTimestamp) {
        this(claimId, initiationTimestamp, deadlineTimestamp, null);
    }
}

interface ComplianceAuditService {
    void audit(String bucketName, Map<String, Object> payload);
}

interface PolicyClaimsDb {
    void persist(Map<String, Object> itemPayload);
}

class ClaimTransformationService {
    private final ComplianceAuditService complianceAuditService;
    private final PolicyClaimsDb policyClaimsDb;

    ClaimTransformationService(ComplianceAuditService complianceAuditService, PolicyClaimsDb policyClaimsDb) {
        this.complianceAuditService = complianceAuditService;
        this.policyClaimsDb = policyClaimsDb;
    }

    Claim transform(Claim claim) {
        ComplianceStatus status = claim.deadlineTimestamp().isAfter(Instant.now())
                ? ComplianceStatus.COMPLIANT
                : ComplianceStatus.NON_COMPLIANT;
        Claim transformed = new Claim(claim.claimId(), claim.initiationTimestamp(), claim.deadlineTimestamp(), status);

        // Orchestrated external I/O per infra contracts
        complianceAuditService.audit("ComplianceAuditService-bucket", Map.of("entity_id", claim.claimId(), "status", status.name()));
        policyClaimsDb.persist(Map.of("pk", claim.claimId(), "item", transformed));
        return transformed;
    }
}
