package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.format.DateTimeParseException;

@ExtendWith(MockitoExtension.class)
class EngagementOrchestrationDecisionMockTest {

    @Mock
    private EngagementDateValidator dateValidator;

    @Mock
    private AwsInfrastructureClient awsClient;

    @InjectMocks
    private EngagementOrchestrationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        // Reset mock state before each test execution
    }

    @Test
    void date_format_mismatch() {
        // Arrange: Simulate a payload containing an invalid date format
        String malformedDate = "2024/13/45";
        when(dateValidator.validate(malformedDate)).thenThrow(new DateTimeParseException("Date format mismatch", malformedDate, 0));

        // Act & Assert: Verify the orchestration service correctly rejects malformed dates
        assertThrows(DateTimeParseException.class, () -> {
            decisionService.processEngagementDecision("claim-ref-001", malformedDate);
        });

        // Verify: Ensure validation was invoked and external I/O was not triggered
        verify(dateValidator, times(1)).validate(malformedDate);
        verifyNoInteractions(awsClient);
    }
}
