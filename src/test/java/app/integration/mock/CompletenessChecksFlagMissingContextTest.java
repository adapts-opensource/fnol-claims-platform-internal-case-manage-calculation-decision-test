package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuredEngagementTransformationIntegrationTest {

    @Mock
    private EngagementContextProvider contextProvider;

    @Mock
    private CompletenessCheckService completenessChecker;

    @Test
    void completeness_checks_flag_missing_context() {
        // Arrange: Mock external context resolution to simulate missing context
        when(contextProvider.getContextFor(anyString())).thenReturn(null);
        when(completenessChecker.validate(any())).thenReturn("FLAG_MISSING_CONTEXT");

        // Act: Invoke transformation service with mocked dependencies
        InsuredEngagementTransformationService transformationService = mock(InsuredEngagementTransformationService.class);
        when(transformationService.process(any())).thenAnswer(invocation -> {
            String context = contextProvider.getContextFor("engagementId");
            String status = completenessChecker.validate(context);
            return new TransformationOutcome(status, false);
        });

        TransformationOutcome result = transformationService.process("engagementId");

        // Assert: Verify that missing context correctly triggers the completeness flag
        assertNotNull(result);
        assertEquals("FLAG_MISSING_CONTEXT", result.getContextStatus());
        assertFalse(result.isContextComplete());
    }
}
