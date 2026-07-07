package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatastropheWithHighVolumeTest {

    @Mock
    private StateTransitionService stateTransitionService;
    @Mock
    private DynamoDbPersistence dynamoDbPersistence;
    @Mock
    private SesCommunication sesCommunication;
    @Mock
    private S3DocumentStore s3DocumentStore;

    private InsuredEngagementService insuredEngagementService;

    @BeforeEach
    void setUp() {
        insuredEngagementService = new InsuredEngagementService(
                stateTransitionService,
                dynamoDbPersistence,
                sesCommunication,
                s3DocumentStore
        );
    }

    @Test
    void catastrophe_with_high_volume() {
        // Arrange: Simulate catastrophe event triggering high volume of reserve lines
        int expectedVolume = 1000;
        List<ReserveLine> incomingReserves = new ArrayList<>(expectedVolume);
        for (int i = 0; i < expectedVolume; i++) {
            incomingReserves.add(new ReserveLine(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    10000.00,
                    "USD",
                    "PENDING"
            ));
        }

        // Mock state transition behavior
        when(stateTransitionService.transition(any(ReserveLine.class), any(String.class)))
                .thenAnswer(invocation -> {
                    ReserveLine reserve = invocation.getArgument(0);
                    reserve.setApprovalStatus("APPROVED");
                    return reserve;
                });

        // Mock external I/O (DynamoDB, SES, S3)
        when(dynamoDbPersistence.save(any(ReserveLine.class))).thenReturn(true);
        when(sesCommunication.sendNotification(anyString(), anyList())).thenReturn("SES-MSG-ID-" + UUID.randomUUID());
        when(s3DocumentStore.storeExposureMetadata(anyString(), anyString())).thenReturn("s3://doc-bucket/exposure-" + UUID.randomUUID() + ".json");

        // Act: Process batch under high volume conditions
        List<ReserveLine> processedReserves = insuredEngagementService.processCatastropheBatch(incomingReserves, "APPROVED");

        // Assert: Verify volume, state transition correctness, and I/O call counts
        assertEquals(expectedVolume, processedReserves.size());
        assertTrue(processedReserves.stream().allMatch(r -> "APPROVED".equals(r.getApprovalStatus())));

        verify(stateTransitionService, times(expectedVolume)).transition(any(ReserveLine.class), eq("APPROVED"));
        verify(dynamoDbPersistence, times(expectedVolume)).save(any(ReserveLine.class));
        verify(sesCommunication, times(expectedVolume)).sendNotification(anyString(), anyList());
        verify(s3DocumentStore, times(expectedVolume)).storeExposureMetadata(anyString(), anyString());
    }

    // Domain Entity
    static class ReserveLine {
        private final String reserveId;
        private final String exposureId;
        private final double amount;
        private final String currency;
        private String approvalStatus;

        public ReserveLine(String reserveId, String exposureId, double amount, String currency, String approvalStatus) {
            this.reserveId = reserveId;
            this.exposureId = exposureId;
            this.amount = amount;
            this.currency = currency;
            this.approvalStatus = approvalStatus;
        }

        public String getReserveId() { return reserveId; }
        public String getExposureId() { return exposureId; }
        public double getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getApprovalStatus() { return approvalStatus; }
        public void setApprovalStatus(String status) { this.approvalStatus = status; }
    }

    // Service Interfaces (Mocked in test)
    interface StateTransitionService {
        ReserveLine transition(ReserveLine reserve, String newStatus);
    }

    interface DynamoDbPersistence {
        boolean save(ReserveLine reserve);
    }

    interface SesCommunication {
        String sendNotification(String insuredId, List<String> contacts);
    }

    interface S3DocumentStore {
        String storeExposureMetadata(String exposureId, String metadata);
    }

    // Application Service
    static class InsuredEngagementService {
        private final StateTransitionService stateTransitionService;
        private final DynamoDbPersistence dynamoDbPersistence;
        private final SesCommunication sesCommunication;
        private final S3DocumentStore s3DocumentStore;

        public InsuredEngagementService(StateTransitionService stateTransitionService,
                                        DynamoDbPersistence dynamoDbPersistence,
                                        SesCommunication sesCommunication,
                                        S3DocumentStore s3DocumentStore) {
            this.stateTransitionService = stateTransitionService;
            this.dynamoDbPersistence = dynamoDbPersistence;
            this.sesCommunication = sesCommunication;
            this.s3DocumentStore = s3DocumentStore;
        }

        public List<ReserveLine> processCatastropheBatch(List<ReserveLine> reserves, String targetStatus) {
            List<ReserveLine> processed = new ArrayList<>(reserves.size());
            for (ReserveLine reserve : reserves) {
                ReserveLine transitioned = stateTransitionService.transition(reserve, targetStatus);
                dynamoDbPersistence.save(transitioned);
                sesCommunication.sendNotification(reserve.getReserveId(), List.of("insured@example.com"));
                s3DocumentStore.storeExposureMetadata(reserve.getExposureId(), "{}");
                processed.add(transitioned);
            }
            return processed;
        }
    }
}
