package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MultiChannelFnolStateTransitionCalculationTest {

    @Mock
    private StateTransitionCalculator calculator;

    @Mock
    private DataStoreClient dataStoreClient;

    @Mock
    private CommunicationsHandler communicationsHandler;

    @Spy
    private FnolSubmissionProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Inject mocked dependencies into the processor for isolated unit/integration testing
        processor.setCalculator(calculator);
        processor.setDataStoreClient(dataStoreClient);
        processor.setCommunicationsHandler(communicationsHandler);
    }

    @Test
    void address_changed_recently_on_policy() {
        // Arrange: Define submission ID and payload reflecting recent address change
        String submissionId = "fnol-sub-001";
        Map<String, Object> payload = Map.of(
                "id", submissionId,
                "policyId", "pol-98765",
                "addressChangedRecently", true,
                "effectiveDate", "2024-05-20",
                "newAddress", "789 Updated Blvd"
        );

        String expectedState = "REVIEW_ADDRESS_CHANGE";
        when(calculator.calculate(anyMap())).thenReturn(expectedState);

        // Act: Execute state transition calculation and persistence
        String actualState = processor.calculateAndPersistState(submissionId, payload);

        // Assert: Verify state transition logic, payload handling, and mocked I/O contracts
        assertEquals(expectedState, actualState);
        verify(calculator).calculate(payload);
        verify(dataStoreClient).saveItem(eq("Data Store_table"), eq(payload));
        verifyNoInteractions(communicationsHandler); // Email not triggered until manual review
    }
}
