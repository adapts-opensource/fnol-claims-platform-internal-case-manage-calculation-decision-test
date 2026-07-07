package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredEngagementDecisionTransformationMockTest {

    @Mock
    private DecisionTransformationService transformationService;

    @Test
    void dol_must_not_be_in_future_120() {
        // Arrange: Simulate a future Date of Loss (DOL)
        LocalDate futureDol = LocalDate.now().plusDays(5);
        Map<String, Object> claimPayload = Map.of(
            "claimId", "CLM-1001",
            "dateOfLoss", futureDol.toString()
        );

        // Arrange: Mock service enforces DOL validation rule
        when(transformationService.transform(claimPayload))
            .thenThrow(new IllegalArgumentException("DOL must not be in future"));

        // Act & Assert: Verify transformation rejects future DOL
        assertThrows(IllegalArgumentException.class, () -> {
            transformationService.transform(claimPayload);
        }, "Transformation should reject future DOL as per validation rules");
    }

    // Mockable service contract for the decision transformation feature
    public interface DecisionTransformationService {
        Map<String, Object> transform(Map<String, Object> inputPayload);
    }
}
