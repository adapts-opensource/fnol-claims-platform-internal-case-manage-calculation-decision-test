package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AobIndicatorsTriggerComplianceFlagTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private SesClient sesClient;

    @Mock
    private FnolOrchestrationService fnolOrchestrationService;

    private Map<String, Object> aobPayload;

    @BeforeEach
    void setUp() {
        aobPayload = new HashMap<>();
        aobPayload.put("policy_number", "POL-FL-001");
        aobPayload.put("aob_indicators", Map.of(
            "has_legal_rep", true,
            "industry_affiliation", true
        ));
        aobPayload.put("state", "FL");
        aobPayload.put("claim_type", "AUTO");
    }

    @Test
    void aob_indicators_trigger_compliance_flag() {
        String entityId = "FNOL-TEST-001";
        Map<String, Object> expectedItem = new HashMap<>(aobPayload);
        expectedItem.put("compliance_flag", "AOB_REGULATORY_REVIEW");

        when(dynamoDbClient.putItem(anyString(), anyMap())).thenReturn(Map.of("Attributes", Map.of("id", entityId)));
        when(s3Client.putObject(anyString(), anyString(), any())).thenReturn(null);
        when(sesClient.sendEmail(any())).thenReturn(Map.of("MessageId", "SES-ID-123"));

        Map<String, Object> result = fnolOrchestrationService.submitAndValidate(entityId, aobPayload);

        assertNotNull(result);
        assertEquals("AOB_REGULATORY_REVIEW", result.get("compliance_flag"));

        verify(dynamoDbClient).putItem(eq("Data_Store_table"), argThat(item ->
            "AOB_REGULATORY_REVIEW".equals(item.get("compliance_flag"))
        ));

        verifyNoInteractions(sesClient);
    }
}
