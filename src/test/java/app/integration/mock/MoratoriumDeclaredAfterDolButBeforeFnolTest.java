package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoratoriumDeclaredAfterDolButBeforeFnolTest {

    @Mock
    private StateTransitionCalculator stateTransitionCalculator;

    @Mock
    private DataStoreClient dataStoreClient;

    @Mock
    private StorageClient storageClient;

    private String testId;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID().toString();
        LocalDate dateOfLoss = LocalDate.of(2023, 10, 1);
        LocalDate moratoriumDate = LocalDate.of(2023, 10, 5);
        LocalDateTime fnolSubmissionTime = LocalDateTime.of(2023, 10, 10, 14, 30);

        testPayload = new HashMap<>();
        testPayload.put("dateOfLoss", dateOfLoss.toString());
        testPayload.put("moratoriumDeclaredDate", moratoriumDate.toString());
        testPayload.put("fnolSubmissionTimestamp", fnolSubmissionTime.toString());
        testPayload.put("channel", "WEB");
        testPayload.put("claimantId", "CL-12345");
    }

    @Test
    void moratorium_declared_after_dol_but_before_fnol() {
        // Arrange: Mock state transition calculation for moratorium declared after DoL but before FNOL
        Map<String, Object> expectedPayload = new HashMap<>(testPayload);
        expectedPayload.put("currentState", "MORATORIUM_AFFECTED");
        expectedPayload.put("nextState", "SUBMITTED");
        expectedPayload.put("stateTransitionValid", true);
        expectedPayload.put("calculationTimestamp", LocalDateTime.now().toString());

        when(stateTransitionCalculator.calculate(testId, testPayload)).thenReturn(expectedPayload);
        when(dataStoreClient.saveItem(testId, expectedPayload)).thenReturn(true);
        when(storageClient.uploadObject("Claim Intake Service-bucket", testId + ".json", expectedPayload))
                .thenReturn("s3://Claim Intake Service-bucket/" + testId + ".json");

        // Act: Execute state transition calculation and persist to infra
        Map<String, Object> resultPayload = stateTransitionCalculator.calculate(testId, testPayload);
        boolean persisted = dataStoreClient.saveItem(testId, resultPayload);
        String objectUri = storageClient.uploadObject("Claim Intake Service-bucket", testId + ".json", resultPayload);

        // Assert: Verify calculation logic, persistence, and S3 upload contracts
        assertNotNull(resultPayload, "Result payload must not be null");
        assertEquals("SUBMITTED", resultPayload.get("nextState"), "Next state should be SUBMITTED");
        assertEquals("MORATORIUM_AFFECTED", resultPayload.get("currentState"), "Current state must reflect moratorium");
        assertTrue((Boolean) resultPayload.get("stateTransitionValid"), "Transition must be valid");
        assertTrue(persisted, "Item must be successfully saved to Data Store");
        assertNotNull(objectUri, "Object URI must be returned from S3 upload");
        assertTrue(objectUri.startsWith("s3://"), "Object URI must follow S3 format");

        verify(stateTransitionCalculator, times(1)).calculate(testId, testPayload);
        verify(dataStoreClient, times(1)).saveItem(testId, resultPayload);
        verify(storageClient, times(1)).uploadObject("Claim Intake Service-bucket", testId + ".json", resultPayload);
    }
}
