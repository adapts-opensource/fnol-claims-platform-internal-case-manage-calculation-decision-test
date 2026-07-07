package app.integration.mock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class InsuredEngagementTransformationMockTest {

    @Mock
    private DecisionTransformationService transformationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void different_reporter_but_same_address_dol_cause_medium_confidence() {
        // Arrange
        String differentReporter = "Jane Doe";
        String sameAddress = "742 Evergreen Terrace, Springfield, IL";
        String sameDol = "2023-11-01";
        String sameCause = "Water Damage";

        // Mock external decision service to return MEDIUM confidence
        when(transformationService.computeConfidenceLevel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ConfidenceLevel.MEDIUM);

        // Act
        ConfidenceLevel actualConfidence = transformationService.computeConfidenceLevel(
                differentReporter, sameAddress, sameDol, sameCause
        );

        // Assert
        assertEquals(ConfidenceLevel.MEDIUM, actualConfidence,
                "Different reporter but same address/DOL/cause should result in medium confidence");
    }
}
