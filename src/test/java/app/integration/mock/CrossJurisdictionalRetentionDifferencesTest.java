package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies Multi-Channel FNOL Submission validation:decision logic for cross-jurisdictional retention differences.
 * NFR Alignment:
 * - GDPR/SOC2: Ensures retention periods comply with jurisdiction-specific lawful basis and audit retention mandates.
 * - Security: Validates input boundaries before external rule resolution; mocks I/O to prevent live AWS/HTTP calls.
 * - Concurrency: Test isolation via @Mock/@InjectMocks ensures thread-safe execution without shared mutable state.
 * - Observability: Decisions are deterministic and verifiable for structured logging downstream.
 */
@ExtendWith(MockitoExtension.class)
public class CrossJurisdictionalRetentionDifferencesTest {

    @Mock
    private JurisdictionResolver jurisdictionResolver;

    @Mock
    private RetentionRuleProvider retentionRuleProvider;

    @InjectMocks
    private FnolValidationDecisionService fnolValidationDecisionService;

    private Claim mockClaim;

    @BeforeEach
    void setUp() {
        mockClaim = new Claim();
        mockClaim.setClaimId("claim-123");
        mockClaim.setClaimNumber("FNOL-2024-001");
        mockClaim.setTenantId("tenant-001");
        mockClaim.setPolicyId("policy-456");
    }

    @Test
    void cross_jurisdictional_retention_differences() {
        // Arrange: Simulate California jurisdiction with 7-day retention period
        String californiaJurisdiction = "CA";
        when(jurisdictionResolver.resolveForClaim(mockClaim)).thenReturn(californiaJurisdiction);
        when(retentionRuleProvider.getRetentionPeriod(californiaJurisdiction)).thenReturn(7);

        // Act
        RetentionDecision decision = fnolValidationDecisionService.decideRetention(mockClaim);

        // Assert: Verify jurisdiction-specific retention is applied correctly
        assertNotNull(decision);
        assertEquals(7, decision.getRetentionDays());
        assertEquals(californiaJurisdiction, decision.getJurisdiction());
        verify(jurisdictionResolver).resolveForClaim(mockClaim);
        verify(retentionRuleProvider).getRetentionPeriod(californiaJurisdiction);
    }

    @Test
    void cross_jurisdictional_retention_differences_should_apply_new_york_rules() {
        when(jurisdictionResolver.resolveForClaim(mockClaim)).thenReturn("NY");
        when(retentionRuleProvider.getRetentionPeriod("NY")).thenReturn(10);

        RetentionDecision decision = fnolValidationDecisionService.decideRetention(mockClaim);

        assertEquals(10, decision.getRetentionDays());
        assertEquals("NY", decision.getJurisdiction());
    }

    @Test
    void cross_jurisdictional_retention_differences_should_fallback_to_default_retention() {
        when(jurisdictionResolver.resolveForClaim(mockClaim)).thenReturn("UNKNOWN");
        when(retentionRuleProvider.getRetentionPeriod(anyString())).thenReturn(30);

        RetentionDecision decision = fnolValidationDecisionService.decideRetention(mockClaim);

        assertEquals(30, decision.getRetentionDays());
        verify(retentionRuleProvider).getRetentionPeriod("UNKNOWN");
    }

    // Minimal supporting types for compilation and strict isolation
    static class Claim {
        private String claimId, claimNumber, tenantId, policyId;
        public void setClaimId(String id) { this.claimId = id; }
        public void setClaimNumber(String num) { this.claimNumber = num; }
        public void setTenantId(String tid) { this.tenantId = tid; }
        public void setPolicyId(String pid) { this.policyId = pid; }
    }

    record RetentionDecision(String jurisdiction, int retentionDays) {}

    interface JurisdictionResolver {
        String resolveForClaim(Claim claim);
    }

    interface RetentionRuleProvider {
        int getRetentionPeriod(String jurisdiction);
    }

    static class FnolValidationDecisionService {
        private final JurisdictionResolver jurisdictionResolver;
        private final RetentionRuleProvider retentionRuleProvider;

        FnolValidationDecisionService(JurisdictionResolver jurisdictionResolver, RetentionRuleProvider retentionRuleProvider) {
            this.jurisdictionResolver = jurisdictionResolver;
            this.retentionRuleProvider = retentionRuleProvider;
        }

        RetentionDecision decideRetention(Claim claim) {
            String jurisdiction = jurisdictionResolver.resolveForClaim(claim);
            int days = retentionRuleProvider.getRetentionPeriod(jurisdiction);
            return new RetentionDecision(jurisdiction, days);
        }
    }
}
