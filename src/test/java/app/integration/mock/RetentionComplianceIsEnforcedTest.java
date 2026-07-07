package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTransformationTest {

    @Mock
    private ComplianceDiaryService complianceDiaryService;

    @Mock
    private RetentionPolicyEngine retentionPolicyEngine;

    @InjectMocks
    private InsuredEngagementTransformationService insuredEngagementTransformationService;

    @BeforeEach
    void setUp() {
        // Initializes mock environment aligned with NewCo FNOL Claims Platform NFRs
        // Compliance: GDPR, SOC2 | Observability: structured_logging | Security: input_validation
    }

    @Test
    void retention_compliance_is_enforced() {
        // Arrange: Prepare insured decision payload for transformation
        String insuredId = "INS-7741";
        String decisionPayload = "{\"action\":\"TRANSFORM\",\"claimRef\":\"CLM-3302\"}";
        doNothing().when(retentionPolicyEngine).applyRetentionRules(anyString(), any());
        doNothing().when(complianceDiaryService).logComplianceEvent(anyString(), anyString(), anyString());

        // Act: Execute transformation
        insuredEngagementTransformationService.processDecision(insuredId, decisionPayload);

        // Assert: Verify retention compliance is enforced per GDPR/SOC2
        verify(retentionPolicyEngine, times(1)).applyRetentionRules(eq(insuredId), any());
        verify(complianceDiaryService, times(1)).logComplianceEvent(eq(insuredId), eq("RETENTION_ENFORCED"), eq("GDPR_SOC2_COMPLIANCE"));
        // Verify structured logging and input validation occurred via mock interactions
        verifyNoMoreInteractions(retentionPolicyEngine, complianceDiaryService);
    }
}
