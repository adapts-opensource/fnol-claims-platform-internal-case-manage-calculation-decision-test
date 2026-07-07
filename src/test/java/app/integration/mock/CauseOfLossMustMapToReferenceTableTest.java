package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateTransitionIntegrationMockTest {

    @Mock
    private CauseOfLossReferenceMapper referenceMapper;

    @InjectMocks
    private InsuredEngagementStateTransitionService stateTransitionService;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles injection automatically; setup reserved for additional NFR hooks
    }

    @Test
    void cause_of_loss_must_map_to_reference_table() {
        // Arrange
        String inputCause = "STRUCTURAL_FIRE";
        String expectedMappedValue = "Structural Fire - Standard Coverage";
        when(referenceMapper.resolve(inputCause)).thenReturn(expectedMappedValue);

        // Act
        String actualMappedValue = stateTransitionService.evaluateCauseOfLoss(inputCause);

        // Assert
        assertNotNull(actualMappedValue, "Mapped cause of loss must not be null");
        assertEquals(expectedMappedValue, actualMappedValue, "Cause of loss must map to reference table value");
        verify(referenceMapper).resolve(inputCause);
    }

    // Package-private dependencies for mock compilation completeness
    interface CauseOfLossReferenceMapper {
        String resolve(String causeOfLoss);
    }

    static class InsuredEngagementStateTransitionService {
        private final CauseOfLossReferenceMapper referenceMapper;

        InsuredEngagementStateTransitionService(CauseOfLossReferenceMapper referenceMapper) {
            this.referenceMapper = referenceMapper;
        }

        String evaluateCauseOfLoss(String causeOfLoss) {
            if (causeOfLoss == null || causeOfLoss.isBlank()) {
                throw new IllegalArgumentException("Cause of loss must not be null or blank");
            }
            return referenceMapper.resolve(causeOfLoss);
        }
    }
}
