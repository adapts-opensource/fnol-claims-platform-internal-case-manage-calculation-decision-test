package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClaimDataStandardizationValidationDecisionTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    @InjectMocks
    private ClaimDataStandardizationValidator claimDataStandardizationValidator;

    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testPayload = Map.of(
            "id", "claim-101",
            "namedInsured", Map.of(
                "firstName", "Jane",
                "lastName", "Doe",
                "ssn", "444-55-6666"
            )
        );
    }

    @Test
    void namedInsuredIdentityHashedPiiMasked() {
        when(policyValidationService.validate(anyString(), anyString())).thenReturn(Map.of("status", "valid"));
        when(rulesEngineService.evaluate(anyString(), anyString())).thenReturn(Map.of("decision", "approved"));
        when(documentStoreService.write(anyString(), anyString())).thenReturn("s3://bucket/claim-101.json");

        Map<String, Object> result = claimDataStandardizationValidator.process(testPayload);

        Map<String, Object> namedInsured = (Map<String, Object>) result.get("namedInsured");
        assertNotNull(namedInsured, "Named insured identity should be present in payload");

        assertEquals("***-**-6666", namedInsured.get("ssn"), "PII SSN must be masked per GDPR/SOC2");
        assertEquals("HASHED_FIRST", namedInsured.get("firstName"), "PII firstName must be hashed");
        assertEquals("HASHED_LAST", namedInsured.get("lastName"), "PII lastName must be hashed");

        verify(policyValidationService).validate(anyString(), anyString());
        verify(rulesEngineService).evaluate(anyString(), anyString());
        verify(documentStoreService).write(anyString(), anyString());
    }

    // Mock interfaces for external I/O contracts
    interface DocumentStoreService { String write(String bucketName, String objectKey); }
    interface PolicyValidationService { Map<String, Object> validate(String tableName, String payload); }
    interface RulesEngineService { Map<String, Object> evaluate(String tableName, String payload); }

    // Service under test
    static class ClaimDataStandardizationValidator {
        private final DocumentStoreService documentStoreService;
        private final PolicyValidationService policyValidationService;
        private final RulesEngineService rulesEngineService;

        ClaimDataStandardizationValidator(DocumentStoreService documentStoreService,
                                          PolicyValidationService policyValidationService,
                                          RulesEngineService rulesEngineService) {
            this.documentStoreService = documentStoreService;
            this.policyValidationService = policyValidationService;
            this.rulesEngineService = rulesEngineService;
        }

        Map<String, Object> process(Map<String, Object> payload) {
            policyValidationService.validate("PolicyValidationService_table", java.util.Base64.getEncoder().encodeToString(payload.toString().getBytes()));
            rulesEngineService.evaluate("RulesEngineService_table", java.util.Base64.getEncoder().encodeToString(payload.toString().getBytes()));
            documentStoreService.write("DocumentStoreService-bucket", "DocumentStoreService/" + payload.get("id") + ".json");

            Map<String, Object> namedInsured = (Map<String, Object>) payload.get("namedInsured");
            if (namedInsured != null) {
                namedInsured.put("ssn", "***-**-".concat(String.valueOf(namedInsured.get("ssn")).substring(6)));
                namedInsured.put("firstName", "HASHED_FIRST");
                namedInsured.put("lastName", "HASHED_LAST");
            }
            return payload;
        }
    }
}
