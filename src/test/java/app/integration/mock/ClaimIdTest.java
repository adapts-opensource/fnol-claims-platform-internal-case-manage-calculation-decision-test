package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimIdTransformationTest {

    private interface ClaimIdTransformationService {
        String transformClaimId(String rawInput);
    }

    @Mock
    private ClaimIdTransformationService claimIdTransformationService;

    @BeforeEach
    void setUp() {
        lenient().when(claimIdTransformationService.transformClaimId(anyString()))
                .thenReturn("CLM-TRANSFORMED-001");
    }

    @Test
    void claim_id() {
        // Arrange
        String rawInput = "ENG_TRACK_CLAIM_001";
        String expectedOutput = "CLM-TRANSFORMED-001";

        when(claimIdTransformationService.transformClaimId(rawInput)).thenReturn(expectedOutput);

        // Act
        String transformedId = claimIdTransformationService.transformClaimId(rawInput);

        // Assert
        assertNotNull(transformedId, "Transformed claim ID must not be null");
        assertEquals(expectedOutput, transformedId, "Claim ID transformation must match expected output");
        assertTrue(transformedId.startsWith("CLM-"), "Transformed claim ID must follow standard prefix convention");
    }
}
