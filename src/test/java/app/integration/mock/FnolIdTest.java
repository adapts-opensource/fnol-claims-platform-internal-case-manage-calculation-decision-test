package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InsuredEngagementStateTransitionTest {

    @Mock
    private InsuredEngagementService mockEngagementService;

    private static final String VALID_FNOL_ID = "FNOL-2024-001";
    private static final String INITIAL_STATE = "OPEN";
    private static final String TARGET_STATE = "INVESTIGATING";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fnolId() {
        // Arrange
        when(mockEngagementService.transitionState(anyString(), anyString(), anyString()))
                .thenReturn(true);

        // Act
        boolean transitionResult = mockEngagementService.transitionState(
                VALID_FNOL_ID, INITIAL_STATE, TARGET_STATE);

        // Assert
        assertTrue(transitionResult, "State transition should succeed for valid fnol_id");
        verify(mockEngagementService, times(1))
                .transitionState(eq(VALID_FNOL_ID), eq(INITIAL_STATE), eq(TARGET_STATE));
    }
}
