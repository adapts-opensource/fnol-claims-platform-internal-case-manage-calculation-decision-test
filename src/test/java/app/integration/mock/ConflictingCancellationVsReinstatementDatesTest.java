package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsuredStateTransitionMockTest {

    @Mock
    private DataPersistenceService dataPersistenceService;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private DocumentStoreService documentStoreService;

    @InjectMocks
    private InsuredEngagementService insuredEngagementService;

    private static final String CLAIM_ID = "CLM-98765";
    private static final String EXPOSURE_ID = "EXP-54321";

    @BeforeEach
    void setUp() {
        lenient().when(dataPersistenceService.readItem(anyString(), anyString())).thenReturn(Map.of(
                "claimId", CLAIM_ID,
                "exposureId", EXPOSURE_ID,
                "state", "ACTIVE",
                "cancellationEffectiveDate", null
        ));
        lenient().when(communicationService.sendNotification(anyString(), anyList(), anyString())).thenReturn("MSG-ID-123");
        lenient().when(documentStoreService.uploadDocument(anyString(), anyString())).thenReturn("s3://bucket/insured-engagement/CLM-98765.json");
    }

    @Test
    void conflicting_cancellation_vs_reinstatement_dates() {
        // Arrange: Reinstatement date precedes cancellation date, violating business rules
        LocalDate cancellationDate = LocalDate.of(2024, 12, 1);
        LocalDate reinstatementDate = LocalDate.of(2024, 11, 15);

        // Act & Assert: Expect validation failure due to conflicting dates
        assertThrows(InvalidStateTransitionException.class, () -> {
            insuredEngagementService.processStateTransition(
                    CLAIM_ID,
                    EXPOSURE_ID,
                    "CANCELLED",
                    cancellationDate,
                    reinstatementDate
            );
        });

        // Verify side-effects were prevented by early validation
        verify(dataPersistenceService, never()).writeItem(anyString(), anyMap());
        verify(communicationService, never()).sendNotification(anyString(), anyList(), anyString());
    }
}
