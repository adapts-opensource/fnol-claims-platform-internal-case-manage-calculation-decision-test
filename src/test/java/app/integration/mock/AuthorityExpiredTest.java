package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiChannelFnolSubmissionOrchestrationValidationTest {

    @Mock
    private AuthorityApiClient authorityApiClient;

    @Mock
    private SubmissionOrchestrator submissionOrchestrator;

    private FnolValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new FnolValidationService(authorityApiClient, submissionOrchestrator);
    }

    @Test
    void authority_expired() {
        // Arrange
        String submissionId = "fnol-sub-001";
        String expiredAuthority = "AUTH-EXPIRED-789";

        when(authorityApiClient.validate(expiredAuthority))
                .thenThrow(new AuthorityExpiredException("Authority has expired"));

        // Act & Assert
        AuthorityExpiredException exception = assertThrows(
                AuthorityExpiredException.class,
                () -> validationService.processSubmission(submissionId, expiredAuthority)
        );

        assertEquals("Authority has expired", exception.getMessage());
        verify(authorityApiClient, times(1)).validate(expiredAuthority);
        verifyNoInteractions(submissionOrchestrator);
    }
}
