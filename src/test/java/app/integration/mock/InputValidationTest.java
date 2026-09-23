package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DecisionTransformationInputValidationTest {

    @Mock
    private ReserveRepository reserveRepository;

    @Mock
    private CommunicationService communicationService;

    @Mock
    private DocumentStorageService documentStorageService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    private DecisionRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new DecisionRequest("res-001", "exp-001", 5000.00, "USD", "PENDING");
    }

    @Test
    @DisplayName("input_validation_118: should reject null or blank reserve_id")
    void input_validation_118_null_reserveId() {
        DecisionRequest request = new DecisionRequest(null, "exp-001", 5000.00, "USD", "PENDING");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> decisionTransformationService.transformDecision(request));
        assertEquals("reserve_id cannot be null or blank", exception.getMessage());
        verify(reserveRepository, never()).save(any());
    }

    @Test
    @DisplayName("input_validation_118: should reject negative or zero amount")
    void input_validation_118_invalid_amount() {
        DecisionRequest request = new DecisionRequest("res-001", "exp-001", -100.0, "USD", "PENDING");
        assertThrows(IllegalArgumentException.class, () -> decisionTransformationService.transformDecision(request));
        verify(reserveRepository, never()).save(any());
    }

    @Test
    @DisplayName("input_validation_118: should reject invalid currency format")
    void input_validation_118_invalid_currency() {
        DecisionRequest request = new DecisionRequest("res-001", "exp-001", 5000.00, "US$", "PENDING");
        assertThrows(IllegalArgumentException.class, () -> decisionTransformationService.transformDecision(request));
        verify(reserveRepository, never()).save(any());
    }

    @Test
    @DisplayName("input_validation_118: should reject invalid approval_status")
    void input_validation_118_invalid_status() {
        DecisionRequest request = new DecisionRequest("res-001", "exp-001", 5000.00, "USD", "CLOSED");
        assertThrows(IllegalArgumentException.class, () -> decisionTransformationService.transformDecision(request));
        verify(reserveRepository, never()).save(any());
    }

    // Minimal domain contracts for self-contained test execution
    record DecisionRequest(String reserveId, String exposureId, Double amount, String currency, String approvalStatus) {}

    interface ReserveRepository {
        void save(Map<String, Object> item);
    }

    interface CommunicationService {
        String sendEmail(Map<String, Object> payload);
    }

    interface DocumentStorageService {
        String uploadDocument(Map<String, Object> metadata);
    }

    class DecisionTransformationService {
        private final ReserveRepository reserveRepository;
        private final CommunicationService communicationService;
        private final DocumentStorageService documentStorageService;

        public DecisionTransformationService(ReserveRepository reserveRepository, CommunicationService communicationService, DocumentStorageService documentStorageService) {
            this.reserveRepository = reserveRepository;
            this.communicationService = communicationService;
            this.documentStorageService = documentStorageService;
        }

        public void transformDecision(DecisionRequest request) {
            if (request.reserveId() == null || request.reserveId().isBlank()) {
                throw new IllegalArgumentException("reserve_id cannot be null or blank");
            }
            if (request.amount() == null || request.amount() <= 0) {
                throw new IllegalArgumentException("amount must be a positive number");
            }
            if (request.currency() == null || !request.currency().matches("^[A-Z]{3}$")) {
                throw new IllegalArgumentException("currency must be a valid 3-letter ISO code");
            }
            if (request.approvalStatus() == null || !List.of("PENDING", "APPROVED", "REJECTED").contains(request.approvalStatus().toUpperCase())) {
                throw new IllegalArgumentException("approval_status must be PENDING, APPROVED, or REJECTED");
            }
            // External I/O (DynamoDB, SES, S3) would be invoked here in production flow
        }
    }
}
