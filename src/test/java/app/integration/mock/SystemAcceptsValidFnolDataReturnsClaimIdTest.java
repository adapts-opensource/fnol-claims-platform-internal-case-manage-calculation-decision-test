package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SystemAcceptsValidFnolDataReturnsClaimIdTest {

    @Mock
    private DocumentStoreService documentStoreService;

    @Mock
    private PolicyValidationService policyValidationService;

    @Mock
    private RulesEngineService rulesEngineService;

    private ClaimStandardizationService claimStandardizationService;

    @BeforeEach
    void setUp() {
        claimStandardizationService = new ClaimStandardizationService(documentStoreService, policyValidationService, rulesEngineService);
    }

    @Test
    void system_accepts_valid_fnol_data_returns_claim_id_within_5s() {
        String validFnolPayload = "{\"policyNumber\":\"POL-999\",\"lossDate\":\"2023-12-01\",\"description\":\"Fender bender\"}";
        String expectedClaimId = "CLM-STD-42";

        when(policyValidationService.validate(any())).thenReturn(true);
        when(rulesEngineService.decide(any())).thenReturn("VALIDATED");
        when(documentStoreService.store(anyString(), anyString())).thenReturn("s3://fnol-docs/CLM-STD-42.json");

        long startTime = System.currentTimeMillis();
        String actualClaimId = claimStandardizationService.processFnol(validFnolPayload);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertNotNull(actualClaimId, "Claim ID must be returned for valid FNOL data");
        assertEquals(expectedClaimId, actualClaimId);
        assertTrue(elapsedMs < 5000, "System must process and return claim ID within 5 seconds");
    }
}
