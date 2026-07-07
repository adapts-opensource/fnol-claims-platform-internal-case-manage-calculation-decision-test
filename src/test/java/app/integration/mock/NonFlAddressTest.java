package app.integration.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that the Insured Engagement & Tracking decision transformation
 * correctly processes and flags insured records with non-Florida addresses.
 */
public class NonFlAddressTest {

    @Mock
    private InsuredEngagementRepository engagementRepository;

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private DocumentStorageService documentStorageService;

    @InjectMocks
    private DecisionTransformationService decisionTransformationService;

    @BeforeEach
    void setUp() {
        // MockitoExtension initializes mocks and performs injection
    }

    @Test
    void non_fl_address() {
        // Arrange
        String engagementId = "eng-nfl-001";
        InsuredEngagement engagement = new InsuredEngagement();
        engagement.setId(engagementId);
        Address address = new Address();
        address.setState("NY");
        address.setCity("Buffalo");
        address.setLine1("100 Lake Ave");
        engagement.setAddress(address);

        when(engagementRepository.findById(engagementId)).thenReturn(Optional.of(engagement));

        // Act
        DecisionResult result = decisionTransformationService.transformEngagement(engagementId);

        // Assert
        assertNotNull(result, "Transformation result should not be null");
        assertEquals(DecisionStatus.NON_FL_PROCESSED, result.getStatus());
        assertTrue(result.isNonFlAddressDetected(), "Non-FL flag should be set");

        // Verify mocked external I/O interactions
        verify(engagementRepository).save(argThat(savedEngagement ->
                savedEngagement.isNonFlFlagged() && "NY".equals(savedEngagement.getAddress().getState())
        ));
        verify(emailService).sendDecisionUpdate(eq(engagementId), eq("NON_FL_ADDRESS_TRANSFORMED"));
        verifyNoInteractions(documentStorageService);
    }
}
