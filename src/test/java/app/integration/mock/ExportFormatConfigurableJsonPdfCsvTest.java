package app.integration.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Export Format Configurable JSON PDF CSV")
public class ExportFormatConfigurableJsonPdfCsvTest {

    @Mock
    private ClaimDataExportService claimDataExportService;

    private ClaimDataStandardizationOrchestration orchestration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestration = new ClaimDataStandardizationOrchestration(claimDataExportService);
    }

    @Nested
    @DisplayName("When Export Format is Configurable")
    class ExportFormatTests {

        @ParameterizedTest(name = "Export format {0} should be supported")
        @EnumSource(value = ExportFormat.class)
        @DisplayName("export_format_configurable_json_pdf_csv")
        void export_format_configurable_json_pdf_csv(ExportFormat format) {
            String claimId = "CLM-1001";
            Map<String, Object> payload = Map.of("id", claimId, "status", "PENDING");
            byte[] expectedBytes = new byte[]{0x01, 0x02};

            when(claimDataExportService.export(eq(claimId), anyMap(), eq(format))).thenReturn(expectedBytes);

            byte[] result = orchestration.processExport(claimId, payload, format);

            assertNotNull(result);
            assertArrayEquals(expectedBytes, result);
            verify(claimDataExportService, times(1)).export(eq(claimId), eq(payload), eq(format));
        }

        @Test
        @DisplayName("Should validate and reject unsupported formats")
        void shouldRejectUnsupportedExportFormat() {
            String claimId = "CLM-1002";
            Map<String, Object> payload = Map.of("id", claimId);

            assertThrows(IllegalArgumentException.class, () ->
                orchestration.processExport(claimId, payload, ExportFormat.XML)
            );
        }
    }
}

// Package-private stubs to simulate external I/O contracts without live AWS/HTTP calls
interface ClaimDataExportService {
    byte[] export(String id, Map<String, Object> payload, ExportFormat format);
}

class ClaimDataStandardizationOrchestration {
    private final ClaimDataExportService exportService;

    ClaimDataStandardizationOrchestration(ClaimDataExportService exportService) {
        this.exportService = exportService;
    }

    byte[] processExport(String id, Map<String, Object> payload, ExportFormat format) {
        // NFR: Input validation - reject non-whitelisted formats early
        if (format == null || format != ExportFormat.JSON && format != ExportFormat.PDF && format != ExportFormat.CSV) {
            throw new IllegalArgumentException("Unsupported export format: " + format);
        }
        // NFR: Thread safety - stateless orchestration method, no shared mutable state
        return exportService.export(id, payload, format);
    }
}
