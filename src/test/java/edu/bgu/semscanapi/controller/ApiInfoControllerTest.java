package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.config.GlobalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Simple test for ApiInfoController
 * Tests the public endpoints that don't require authentication
 */
@ExtendWith(MockitoExtension.class)
class ApiInfoControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GlobalConfig globalConfig;

    @InjectMocks
    private ApiInfoController apiInfoController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(apiInfoController).build();
    }

    @Test
    void getApiEndpoints_ShouldReturnEndpoints() throws Exception {
        // Given
        when(globalConfig.getServerUrl()).thenReturn("http://localhost:8080");
        when(globalConfig.getApiBaseUrl()).thenReturn("http://localhost:8080/api/v1");
        when(globalConfig.getSeminarsEndpoint()).thenReturn("http://localhost:8080/api/v1/seminars");
        when(globalConfig.getSessionsEndpoint()).thenReturn("http://localhost:8080/api/v1/sessions");
        when(globalConfig.getApplicationName()).thenReturn("SemScan API");
        when(globalConfig.getApplicationVersion()).thenReturn("1.0.0");
        when(globalConfig.getApiVersion()).thenReturn("v1");
        when(globalConfig.getApiKeyHeader()).thenReturn("x-api-key");
        when(globalConfig.getEnvironment()).thenReturn("development");

        // When & Then
        mockMvc.perform(get("/api/v1/info/endpoints")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.serverUrl").value("http://localhost:8080"))
                .andExpect(jsonPath("$.apiBaseUrl").value("http://localhost:8080/api/v1"))
                .andExpect(jsonPath("$.endpoints.seminars").value("http://localhost:8080/api/v1/seminars"))
                .andExpect(jsonPath("$.endpoints.sessions").value("http://localhost:8080/api/v1/sessions"))
                .andExpect(jsonPath("$.config.applicationName").value("SemScan API"))
                .andExpect(jsonPath("$.config.applicationVersion").value("1.0.0"));
    }

    @Test
    void getApiConfig_ShouldReturnConfig() throws Exception {
        // Given
        when(globalConfig.getApplicationName()).thenReturn("SemScan API");
        when(globalConfig.getApplicationVersion()).thenReturn("1.0.0");
        when(globalConfig.getApplicationDescription()).thenReturn("SemScan Attendance System API");
        when(globalConfig.getApiVersion()).thenReturn("v1");
        when(globalConfig.getEnvironment()).thenReturn("development");
        when(globalConfig.getServerUrl()).thenReturn("http://localhost:8080");
        when(globalConfig.getApiBaseUrl()).thenReturn("http://localhost:8080/api/v1");
        when(globalConfig.getApiKeyHeader()).thenReturn("x-api-key");
        when(globalConfig.getCorsAllowedOrigins()).thenReturn("*");
        when(globalConfig.getManualAttendanceWindowBeforeMinutes()).thenReturn(10);
        when(globalConfig.getManualAttendanceWindowAfterMinutes()).thenReturn(15);
        when(globalConfig.getManualAttendanceAutoApproveMinCap()).thenReturn(5);
        when(globalConfig.getManualAttendanceAutoApproveCapPercentage()).thenReturn(5);
        when(globalConfig.getMaxExportFileSizeMb()).thenReturn(50);
        when(globalConfig.getAllowedExportFormats()).thenReturn(new String[]{"csv", "xlsx"});

        // When & Then
        mockMvc.perform(get("/api/v1/info/config")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.applicationName").value("SemScan API"))
                .andExpect(jsonPath("$.applicationVersion").value("1.0.0"))
                .andExpect(jsonPath("$.applicationDescription").value("SemScan Attendance System API"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.environment").value("development"))
                .andExpect(jsonPath("$.serverUrl").value("http://localhost:8080"))
                .andExpect(jsonPath("$.apiBaseUrl").value("http://localhost:8080/api/v1"));
    }
}
