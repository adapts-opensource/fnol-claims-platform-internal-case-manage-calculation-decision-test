package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Simplified domain models for test scope
class TenantReportRequest {
    final String tenantId;
    final String landlordId;
    final String incidentDetails;
    final BigDecimal amount;

    TenantReportRequest(String tenantId, String landlordId, String incidentDetails, BigDecimal amount) {
        this.tenantId = tenantId;
        this.landlordId = landlordId;
        this.incidentDetails = incidentDetails;
        this.amount = amount;
    }
}

class ReserveLinePayload {
    final String reserveId;
    final String exposureId;
    final BigDecimal amount;
    final String currency;
    final String approvalStatus;

    ReserveLinePayload(String reserveId, String exposureId, BigDecimal amount, String currency, String approvalStatus) {
        this.reserveId = reserveId;
        this.exposureId = exposureId;
        this.amount = amount;
        this.currency = currency;
        this.approvalStatus = approvalStatus;
    }
}

class DynamoDBInput {
    final String tableName;
    final Map<String, Object> itemPayload;

    DynamoDBInput(String tableName, Map<String, Object> itemPayload) {
        this.tableName = tableName;
        this.itemPayload = itemPayload;
    }
}

class SESInput {
    final String fromAddress;
    final List<String> toAddresses;
    final String region;

    SESInput(String fromAddress, List<String> toAddresses, String region) {
        this.fromAddress = fromAddress;
        this.toAddresses = toAddresses;
        this.region = region;
    }
}

interface DynamoDBPersistence {
    void putItem(DynamoDBInput input);
}

interface SESCommunication {
    String sendMessage(SESInput input);
}

class InsuredEngagementTransformationService {
    private final DynamoDBPersistence dynamoDBPersistence;
    private final SESCommunication sesCommunication;

    InsuredEngagementTransformationService(DynamoDBPersistence dynamoDBPersistence, SESCommunication sesCommunication) {
        this.dynamoDBPersistence = dynamoDBPersistence;
        this.sesCommunication = sesCommunication;
    }

    ReserveLinePayload transformTenantReportToLandlordReserve(TenantReportRequest request) {
        String reserveId = UUID.randomUUID().toString();
        String exposureId = "EXP-" + UUID.randomUUID().toString();

        ReserveLinePayload payload = new ReserveLinePayload(
            reserveId,
            exposureId,
            request.amount,
            "USD",
            "Pending"
        );

        Map<String, Object> dbItem = Map.of(
            "reserve_id", reserveId,
            "exposure_id", exposureId,
            "amount", request.amount.doubleValue(),
            "currency", "USD",
            "approval_status", "Pending"
        );
        dynamoDBPersistence.putItem(new DynamoDBInput("ReserveLine_table", dbItem));

        sesCommunication.sendMessage(new SESInput(
            "claims@newco-insurance.com",
            List.of("landlord@example.com"),
            "us-east-1"
        ));

        return payload;
    }
}

class TenantReportingOnBehalfOfLandlordTest {
    @Mock
    private DynamoDBPersistence dynamoDBPersistence;
    @Mock
    private SESCommunication sesCommunication;

    private InsuredEngagementTransformationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new InsuredEngagementTransformationService(dynamoDBPersistence, sesCommunication);
    }

    @Test
    void tenant_reporting_on_behalf_of_landlord() {
        TenantReportRequest request = new TenantReportRequest(
            "TNT-1001",
            "LRD-2002",
            "Water damage in unit 4B",
            new BigDecimal("1500.00")
        );

        when(sesCommunication.sendMessage(any(SESInput.class))).thenReturn("SES-MSG-9876");

        ReserveLinePayload result = service.transformTenantReportToLandlordReserve(request);

        assertNotNull(result);
        assertEquals("Pending", result.approvalStatus);
        assertEquals("USD", result.currency);
        assertEquals(new BigDecimal("1500.00"), result.amount);

        ArgumentCaptor<DynamoDBInput> dbCaptor = ArgumentCaptor.forClass(DynamoDBInput.class);
        verify(dynamoDBPersistence, times(1)).putItem(dbCaptor.capture());
        Map<String, Object> writtenItem = dbCaptor.getValue().itemPayload;
        assertEquals("Pending", writtenItem.get("approval_status"));
        assertEquals("USD", writtenItem.get("currency"));

        ArgumentCaptor<SESInput> emailCaptor = ArgumentCaptor.forClass(SESInput.class);
        verify(sesCommunication, times(1)).sendMessage(emailCaptor.capture());
        assertEquals("claims@newco-insurance.com", emailCaptor.getValue().fromAddress);
        assertEquals(List.of("landlord@example.com"), emailCaptor.getValue().toAddresses);
        assertEquals("us-east-1", emailCaptor.getValue().region);
    }
}
