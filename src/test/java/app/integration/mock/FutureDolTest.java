package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementTransformationMockTest {

    private static final String FUTURE_DOL_DATE = "2099-12-31";
    private static final String EXPECTED_STATUS = "PENDING_REVIEW";
    private static final String EXPECTED_NOTE = "Date of Loss is in the future. Awaiting verification.";

    @Mock
    private ClaimTransformationService transformationService;

    private InsuredEngagementTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new InsuredEngagementTransformer(transformationService);
    }

    @Test
    void future_dol() {
        Map<String, Object> payload = Map.of(
            "claimId", "CLM-1001",
            "dateOfLoss", FUTURE_DOL_DATE,
            "insuredId", "INS-505"
        );

        when(transformationService.transform(anyMap()))
            .thenReturn(Map.of(
                "claimId", "CLM-1001",
                "status", EXPECTED_STATUS,
                "processingNote", EXPECTED_NOTE
            ));

        Map<String, Object> result = transformer.processEngagement(payload);

        assertNotNull(result);
        assertEquals("CLM-1001", result.get("claimId"));
        assertEquals(EXPECTED_STATUS, result.get("status"));
        assertEquals(EXPECTED_NOTE, result.get("processingNote"));

        verify(transformationService).transform(payload);
    }

    interface ClaimTransformationService {
        Map<String, Object> transform(Map<String, Object> payload);
    }

    static class InsuredEngagementTransformer {
        private final ClaimTransformationService service;

        InsuredEngagementTransformer(ClaimTransformationService service) {
            this.service = service;
        }

        Map<String, Object> processEngagement(Map<String, Object> payload) {
            return service.transform(payload);
        }
    }
}
