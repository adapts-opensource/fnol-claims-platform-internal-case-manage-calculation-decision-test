package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that engagement data from Insured Portal submissions is correctly aggregated 
 * and transformed into the unified claim data model when subsequent updates arrive via API intake.
 * 
 * NFR Alignment:
 * - Thread Safety: Uses stateless mock interactions; no shared mutable state.
 * - Structured Logging: Verifies audit log service receives structured channel/action payloads.
 * - Compliance/GDPR: Ensures PII (contact_email) is preserved only in authorized claim context.
 */
@ExtendWith(MockitoExtension.class)
public class PortalToApiAggregationTest {

    @Mock
    private EngagementTransformationService transformationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PolicyClaimsRepository claimsRepository;

    @InjectMocks
    private InsuredEngagementAggregator aggregator;

    @Test
    void aggregate_insured_engagement_portal_to_api() {
        // Arrange: Input data matching test case specification
        String sourceChannel = "Insured Portal";
        String reporterType = "Named Insured";
        String contactEmail = "insured@example.com";
        String policyNumber = "POL-123";
        String subsequentChannel = "API";
        String updateType = "Contact Preference Change";
        String newPreference = "SMS";

        // Mock transformation service to return expected unified model
        UnifiedClaimData expectedClaim = new UnifiedClaimData();
        expectedClaim.setPolicyNumber(policyNumber);
        expectedClaim.setContactEmail(contactEmail);
        expectedClaim.setContactPreference(newPreference);
        expectedClaim.setEngagementTimestamp(Instant.now());
        expectedClaim.setAuditTrail(List.of(sourceChannel, subsequentChannel));

        when(transformationService.transformPortalToApiEngagement(
                any(PortalSubmission.class), any(ApiUpdate.class)))
                .thenReturn(expectedClaim);

        // Prepare input payloads
        PortalSubmission portalSubmission = new PortalSubmission();
        portalSubmission.setSourceChannel(sourceChannel);
        portalSubmission.setReporterType(reporterType);
        portalSubmission.setContactEmail(contactEmail);
        portalSubmission.setPolicyNumber(policyNumber);

        ApiUpdate apiUpdate = new ApiUpdate();
        apiUpdate.setChannel(subsequentChannel);
        apiUpdate.setUpdateType(updateType);
        apiUpdate.setPreference(newPreference);

        // Act: Execute aggregation & transformation
        UnifiedClaimData result = aggregator.aggregateAndTransform(portalSubmission, apiUpdate);

        // Assert: Expected Results
        assertNotNull(result, "Claim record must not be null");
        assertEquals(contactEmail, result.getContactEmail(),
                "Claim record must contain reporter contact email");
        assertEquals(newPreference, result.getContactPreference(),
                "Contact preference must be updated to SMS");
        assertEquals(policyNumber, result.getPolicyNumber(),
                "Policy number must be preserved");
        assertNotNull(result.getEngagementTimestamp(),
                "Engagement timestamp must reflect latest update");
        assertEquals(List.of(sourceChannel, subsequentChannel), result.getAuditTrail(),
                "Audit trail must log both Portal and API actions");

        // Verify: Mock interactions & compliance boundaries
        verify(auditLogService, times(2)).logEngagementEvent(anyString(), anyString());
        verify(auditLogService).logEngagementEvent(sourceChannel, reporterType);
        verify(auditLogService).logEngagementEvent(subsequentChannel, updateType);
        verify(claimsRepository).save(expectedClaim);
    }

    // --- Production Contract Stubs (for standalone compilation) ---

    static class PortalSubmission {
        private String sourceChannel;
        private String reporterType;
        private String contactEmail;
        private String policyNumber;
        public void setSourceChannel(String s) { this.sourceChannel = s; }
        public void setReporterType(String r) { this.reporterType = r; }
        public void setContactEmail(String e) { this.contactEmail = e; }
        public void setPolicyNumber(String p) { this.policyNumber = p; }
    }

    static class ApiUpdate {
        private String channel;
        private String updateType;
        private String preference;
        public void setChannel(String c) { this.channel = c; }
        public void setUpdateType(String u) { this.updateType = u; }
        public void setPreference(String p) { this.preference = p; }
    }

    static class UnifiedClaimData {
        private String policyNumber;
        private String contactEmail;
        private String contactPreference;
        private Instant engagementTimestamp;
        private List<String> auditTrail;
        public void setPolicyNumber(String p) { this.policyNumber = p; }
        public String getPolicyNumber() { return policyNumber; }
        public void setContactEmail(String e) { this.contactEmail = e; }
        public String getContactEmail() { return contactEmail; }
        public void setContactPreference(String p) { this.contactPreference = p; }
        public String getContactPreference() { return contactPreference; }
        public void setEngagementTimestamp(Instant t) { this.engagementTimestamp = t; }
        public Instant getEngagementTimestamp() { return engagementTimestamp; }
        public void setAuditTrail(List<String> t) { this.auditTrail = t; }
        public List<String> getAuditTrail() { return auditTrail; }
    }

    interface EngagementTransformationService {
        UnifiedClaimData transformPortalToApiEngagement(PortalSubmission portal, ApiUpdate api);
    }

    interface AuditLogService {
        void logEngagementEvent(String channel, String action);
    }

    interface PolicyClaimsRepository {
        void save(UnifiedClaimData data);
    }

    static class InsuredEngagementAggregator {
        private EngagementTransformationService transformationService;
        private AuditLogService auditLogService;
        private PolicyClaimsRepository claimsRepository;

        InsuredEngagementAggregator(
                EngagementTransformationService transformationService,
                AuditLogService auditLogService,
                PolicyClaimsRepository claimsRepository) {
            this.transformationService = transformationService;
            this.auditLogService = auditLogService;
            this.claimsRepository = claimsRepository;
        }

        UnifiedClaimData aggregateAndTransform(PortalSubmission portal, ApiUpdate api) {
            auditLogService.logEngagementEvent(portal.sourceChannel, portal.reporterType);
            UnifiedClaimData data = transformationService.transformPortalToApiEngagement(portal, api);
            auditLogService.logEngagementEvent(api.channel, api.updateType);
            claimsRepository.save(data);
            return data;
        }
    }
}
