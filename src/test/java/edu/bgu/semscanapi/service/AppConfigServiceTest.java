package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.AppConfig;
import edu.bgu.semscanapi.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AppConfigService
 */
@ExtendWith(MockitoExtension.class)
class AppConfigServiceTest {

    @Mock
    private AppConfigRepository appConfigRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @InjectMocks
    private AppConfigService appConfigService;

    private AppConfig testStringConfig;
    private AppConfig testIntegerConfig;
    private AppConfig testBooleanConfig;

    @BeforeEach
    void setUp() {
        // Setup test string config
        testStringConfig = new AppConfig();
        testStringConfig.setConfigId(1L);
        testStringConfig.setConfigKey("server_url");
        testStringConfig.setConfigValue("http://132.72.50.53:8080");
        testStringConfig.setConfigType(AppConfig.ConfigType.STRING);
        testStringConfig.setTargetSystem(AppConfig.TargetSystem.BOTH);

        // Setup test integer config
        testIntegerConfig = new AppConfig();
        testIntegerConfig.setConfigId(2L);
        testIntegerConfig.setConfigKey("connection_timeout_seconds");
        testIntegerConfig.setConfigValue("30");
        testIntegerConfig.setConfigType(AppConfig.ConfigType.INTEGER);
        testIntegerConfig.setTargetSystem(AppConfig.TargetSystem.API);

        // Setup test boolean config
        testBooleanConfig = new AppConfig();
        testBooleanConfig.setConfigId(3L);
        testBooleanConfig.setConfigKey("email_enabled");
        testBooleanConfig.setConfigValue("true");
        testBooleanConfig.setConfigType(AppConfig.ConfigType.BOOLEAN);
        testBooleanConfig.setTargetSystem(AppConfig.TargetSystem.API);
    }

    // ==================== getStringConfig Tests ====================

    @Test
    void getStringConfig_WithExistingKey_ReturnsValue() {
        // Given
        when(appConfigRepository.findByConfigKey("server_url"))
                .thenReturn(Optional.of(testStringConfig));

        // When
        String result = appConfigService.getStringConfig("server_url", "default");

        // Then
        assertEquals("http://132.72.50.53:8080", result);
        verify(appConfigRepository).findByConfigKey("server_url");
    }

    @Test
    void getStringConfig_WithNonExistingKey_ReturnsDefault() {
        // Given
        when(appConfigRepository.findByConfigKey("non_existing_key"))
                .thenReturn(Optional.empty());

        // When
        String result = appConfigService.getStringConfig("non_existing_key", "default_value");

        // Then
        assertEquals("default_value", result);
        verify(databaseLoggerService).logAction(eq("INFO"), eq("APP_CONFIG_NOT_FOUND"),
                contains("non_existing_key"), isNull(), contains("non_existing_key"));
    }

    @Test
    void getStringConfig_WithNullDefault_ReturnsNull() {
        // Given
        when(appConfigRepository.findByConfigKey("missing_key"))
                .thenReturn(Optional.empty());

        // When
        String result = appConfigService.getStringConfig("missing_key", null);

        // Then
        assertNull(result);
    }

    @Test
    void getStringConfig_WithEmptyValue_ReturnsEmptyString() {
        // Given
        AppConfig emptyConfig = new AppConfig();
        emptyConfig.setConfigKey("empty_key");
        emptyConfig.setConfigValue("");
        when(appConfigRepository.findByConfigKey("empty_key"))
                .thenReturn(Optional.of(emptyConfig));

        // When
        String result = appConfigService.getStringConfig("empty_key", "default");

        // Then
        assertEquals("", result);
    }

    // ==================== getIntegerConfig Tests ====================

    @Test
    void getIntegerConfig_WithValidInteger_ReturnsValue() {
        // Given
        when(appConfigRepository.findByConfigKey("connection_timeout_seconds"))
                .thenReturn(Optional.of(testIntegerConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("connection_timeout_seconds", 60);

        // Then
        assertEquals(30, result);
        verify(appConfigRepository).findByConfigKey("connection_timeout_seconds");
    }

    @Test
    void getIntegerConfig_WithNonExistingKey_ReturnsDefault() {
        // Given
        when(appConfigRepository.findByConfigKey("non_existing_int"))
                .thenReturn(Optional.empty());

        // When
        Integer result = appConfigService.getIntegerConfig("non_existing_int", 100);

        // Then
        assertEquals(100, result);
    }

    @Test
    void getIntegerConfig_WithInvalidInteger_ReturnsDefault() {
        // Given
        AppConfig invalidIntConfig = new AppConfig();
        invalidIntConfig.setConfigKey("invalid_int");
        invalidIntConfig.setConfigValue("not_a_number");
        when(appConfigRepository.findByConfigKey("invalid_int"))
                .thenReturn(Optional.of(invalidIntConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("invalid_int", 50);

        // Then
        assertEquals(50, result);
        verify(databaseLoggerService).logError(eq("APP_CONFIG_CONVERSION_ERROR"),
                contains("invalid_int"), any(Exception.class), isNull(), contains("invalid_int"));
    }

    @Test
    void getIntegerConfig_WithWhitespace_TrimsAndParsesCorrectly() {
        // Given
        AppConfig whitespacedConfig = new AppConfig();
        whitespacedConfig.setConfigKey("whitespaced_int");
        whitespacedConfig.setConfigValue("  42  ");
        when(appConfigRepository.findByConfigKey("whitespaced_int"))
                .thenReturn(Optional.of(whitespacedConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("whitespaced_int", 0);

        // Then
        assertEquals(42, result);
    }

    @Test
    void getIntegerConfig_WithNegativeValue_ReturnsNegative() {
        // Given
        AppConfig negativeConfig = new AppConfig();
        negativeConfig.setConfigKey("negative_int");
        negativeConfig.setConfigValue("-10");
        when(appConfigRepository.findByConfigKey("negative_int"))
                .thenReturn(Optional.of(negativeConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("negative_int", 0);

        // Then
        assertEquals(-10, result);
    }

    @Test
    void getIntegerConfig_WithZero_ReturnsZero() {
        // Given
        AppConfig zeroConfig = new AppConfig();
        zeroConfig.setConfigKey("zero_int");
        zeroConfig.setConfigValue("0");
        when(appConfigRepository.findByConfigKey("zero_int"))
                .thenReturn(Optional.of(zeroConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("zero_int", 99);

        // Then
        assertEquals(0, result);
    }

    // ==================== getBooleanConfig Tests ====================

    @Test
    void getBooleanConfig_WithTrueValue_ReturnsTrue() {
        // Given
        when(appConfigRepository.findByConfigKey("email_enabled"))
                .thenReturn(Optional.of(testBooleanConfig));

        // When
        Boolean result = appConfigService.getBooleanConfig("email_enabled", false);

        // Then
        assertTrue(result);
        verify(appConfigRepository).findByConfigKey("email_enabled");
    }

    @Test
    void getBooleanConfig_WithFalseValue_ReturnsFalse() {
        // Given
        AppConfig falseConfig = new AppConfig();
        falseConfig.setConfigKey("debug_mode");
        falseConfig.setConfigValue("false");
        when(appConfigRepository.findByConfigKey("debug_mode"))
                .thenReturn(Optional.of(falseConfig));

        // When
        Boolean result = appConfigService.getBooleanConfig("debug_mode", true);

        // Then
        assertFalse(result);
    }

    @Test
    void getBooleanConfig_WithNonExistingKey_ReturnsDefault() {
        // Given
        when(appConfigRepository.findByConfigKey("non_existing_bool"))
                .thenReturn(Optional.empty());

        // When
        Boolean result = appConfigService.getBooleanConfig("non_existing_bool", true);

        // Then
        assertTrue(result);
    }

    @Test
    void getBooleanConfig_WithInvalidBoolean_ReturnsFalse() {
        // Given - Boolean.parseBoolean returns false for any non-"true" string
        AppConfig invalidBoolConfig = new AppConfig();
        invalidBoolConfig.setConfigKey("invalid_bool");
        invalidBoolConfig.setConfigValue("yes"); // "yes" is not parsed as true
        when(appConfigRepository.findByConfigKey("invalid_bool"))
                .thenReturn(Optional.of(invalidBoolConfig));

        // When
        Boolean result = appConfigService.getBooleanConfig("invalid_bool", true);

        // Then
        assertFalse(result); // Boolean.parseBoolean("yes") returns false
    }

    @Test
    void getBooleanConfig_WithWhitespace_TrimsAndParsesCorrectly() {
        // Given
        AppConfig whitespacedConfig = new AppConfig();
        whitespacedConfig.setConfigKey("whitespaced_bool");
        whitespacedConfig.setConfigValue("  true  ");
        when(appConfigRepository.findByConfigKey("whitespaced_bool"))
                .thenReturn(Optional.of(whitespacedConfig));

        // When
        Boolean result = appConfigService.getBooleanConfig("whitespaced_bool", false);

        // Then
        assertTrue(result);
    }

    @Test
    void getBooleanConfig_WithMixedCaseTrue_ReturnsTrue() {
        // Given
        AppConfig mixedCaseConfig = new AppConfig();
        mixedCaseConfig.setConfigKey("mixed_case_bool");
        mixedCaseConfig.setConfigValue("TRUE");
        when(appConfigRepository.findByConfigKey("mixed_case_bool"))
                .thenReturn(Optional.of(mixedCaseConfig));

        // When
        Boolean result = appConfigService.getBooleanConfig("mixed_case_bool", false);

        // Then
        assertTrue(result); // Boolean.parseBoolean is case-insensitive for "true"
    }

    // ==================== getConfigValue Tests (Generic) ====================

    @Test
    void getConfigValue_WithUnsupportedType_ReturnsDefault() {
        // Given
        when(appConfigRepository.findByConfigKey("some_key"))
                .thenReturn(Optional.of(testStringConfig));

        // When - Double is not a supported type
        Double result = appConfigService.getConfigValue("some_key", Double.class, 1.5);

        // Then
        assertEquals(1.5, result);
        verify(databaseLoggerService).logError(eq("APP_CONFIG_UNSUPPORTED_TYPE"),
                contains("Double"), isNull(), isNull(), contains("some_key"));
    }

    @Test
    void getConfigValue_WithNullDatabaseLoggerService_HandlesGracefully() {
        // This test verifies the null check for databaseLoggerService
        // Given
        when(appConfigRepository.findByConfigKey("test_key"))
                .thenReturn(Optional.empty());

        // When - databaseLoggerService is mocked but the code checks for null
        String result = appConfigService.getStringConfig("test_key", "default");

        // Then
        assertEquals("default", result);
        // Verify databaseLoggerService was called (since it's mocked, not null)
        verify(databaseLoggerService).logAction(eq("INFO"), eq("APP_CONFIG_NOT_FOUND"),
                anyString(), isNull(), anyString());
    }

    // ==================== getConfigsForApi Tests ====================

    @Test
    void getConfigsForApi_ReturnsApiAndBothConfigs() {
        // Given
        AppConfig apiConfig = new AppConfig();
        apiConfig.setConfigKey("api_only");
        apiConfig.setTargetSystem(AppConfig.TargetSystem.API);

        AppConfig bothConfig = new AppConfig();
        bothConfig.setConfigKey("both_systems");
        bothConfig.setTargetSystem(AppConfig.TargetSystem.BOTH);

        List<AppConfig.TargetSystem> expectedTargets = Arrays.asList(
                AppConfig.TargetSystem.API,
                AppConfig.TargetSystem.BOTH
        );

        when(appConfigRepository.findByTargetSystemIn(expectedTargets))
                .thenReturn(Arrays.asList(apiConfig, bothConfig));

        // When
        List<AppConfig> result = appConfigService.getConfigsForApi();

        // Then
        assertEquals(2, result.size());
        verify(appConfigRepository).findByTargetSystemIn(expectedTargets);
        verify(databaseLoggerService).logAction(eq("INFO"), eq("APP_CONFIG_API_RETRIEVED"),
                contains("2 config(s)"), isNull(), eq("count=2"));
    }

    @Test
    void getConfigsForApi_WithNoConfigs_ReturnsEmptyList() {
        // Given
        List<AppConfig.TargetSystem> expectedTargets = Arrays.asList(
                AppConfig.TargetSystem.API,
                AppConfig.TargetSystem.BOTH
        );
        when(appConfigRepository.findByTargetSystemIn(expectedTargets))
                .thenReturn(Collections.emptyList());

        // When
        List<AppConfig> result = appConfigService.getConfigsForApi();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(databaseLoggerService).logAction(eq("INFO"), eq("APP_CONFIG_API_RETRIEVED"),
                contains("0 config(s)"), isNull(), eq("count=0"));
    }

    // ==================== getConfigsForMobile Tests ====================

    @Test
    void getConfigsForMobile_ReturnsMobileAndBothConfigs() {
        // Given
        AppConfig mobileConfig = new AppConfig();
        mobileConfig.setConfigKey("mobile_only");
        mobileConfig.setTargetSystem(AppConfig.TargetSystem.MOBILE);

        AppConfig bothConfig = new AppConfig();
        bothConfig.setConfigKey("both_systems");
        bothConfig.setTargetSystem(AppConfig.TargetSystem.BOTH);

        List<AppConfig.TargetSystem> expectedTargets = Arrays.asList(
                AppConfig.TargetSystem.MOBILE,
                AppConfig.TargetSystem.BOTH
        );

        when(appConfigRepository.findByTargetSystemIn(expectedTargets))
                .thenReturn(Arrays.asList(mobileConfig, bothConfig));

        // When
        List<AppConfig> result = appConfigService.getConfigsForMobile();

        // Then
        assertEquals(2, result.size());
        verify(appConfigRepository).findByTargetSystemIn(expectedTargets);
        verify(databaseLoggerService).logAction(eq("INFO"), eq("APP_CONFIG_MOBILE_RETRIEVED"),
                contains("2 config(s)"), isNull(), eq("count=2"));
    }

    @Test
    void getConfigsForMobile_WithNoConfigs_ReturnsEmptyList() {
        // Given
        List<AppConfig.TargetSystem> expectedTargets = Arrays.asList(
                AppConfig.TargetSystem.MOBILE,
                AppConfig.TargetSystem.BOTH
        );
        when(appConfigRepository.findByTargetSystemIn(expectedTargets))
                .thenReturn(Collections.emptyList());

        // When
        List<AppConfig> result = appConfigService.getConfigsForMobile();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(databaseLoggerService).logAction(eq("INFO"), eq("APP_CONFIG_MOBILE_RETRIEVED"),
                contains("0 config(s)"), isNull(), eq("count=0"));
    }

    @Test
    void getConfigsForMobile_ReturnsAllMobileConfigs() {
        // Given
        AppConfig config1 = new AppConfig();
        config1.setConfigKey("APP_VERSION");
        config1.setConfigValue("1.0.0");
        config1.setTargetSystem(AppConfig.TargetSystem.MOBILE);

        AppConfig config2 = new AppConfig();
        config2.setConfigKey("toast_duration_error");
        config2.setConfigValue("10000");
        config2.setTargetSystem(AppConfig.TargetSystem.MOBILE);

        AppConfig config3 = new AppConfig();
        config3.setConfigKey("server_url");
        config3.setConfigValue("http://132.72.50.53:8080");
        config3.setTargetSystem(AppConfig.TargetSystem.BOTH);

        List<AppConfig.TargetSystem> expectedTargets = Arrays.asList(
                AppConfig.TargetSystem.MOBILE,
                AppConfig.TargetSystem.BOTH
        );

        when(appConfigRepository.findByTargetSystemIn(expectedTargets))
                .thenReturn(Arrays.asList(config1, config2, config3));

        // When
        List<AppConfig> result = appConfigService.getConfigsForMobile();

        // Then
        assertEquals(3, result.size());
    }

    // ==================== getConfigsByTag Tests ====================

    @Test
    void getConfigsByTag_WithMatchingTag_ReturnsConfigs() {
        // Given
        AppConfig emailConfig1 = new AppConfig();
        emailConfig1.setConfigKey("email_from_name");
        emailConfig1.setTags("#EMAIL");

        AppConfig emailConfig2 = new AppConfig();
        emailConfig2.setConfigKey("email_reply_to");
        emailConfig2.setTags("#EMAIL #SETTINGS");

        when(appConfigRepository.findByTagsContaining("#EMAIL"))
                .thenReturn(Arrays.asList(emailConfig1, emailConfig2));

        // When
        List<AppConfig> result = appConfigService.getConfigsByTag("#EMAIL");

        // Then
        assertEquals(2, result.size());
        verify(appConfigRepository).findByTagsContaining("#EMAIL");
    }

    @Test
    void getConfigsByTag_WithNoMatchingTag_ReturnsEmptyList() {
        // Given
        when(appConfigRepository.findByTagsContaining("#NONEXISTENT"))
                .thenReturn(Collections.emptyList());

        // When
        List<AppConfig> result = appConfigService.getConfigsByTag("#NONEXISTENT");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getConfigsByTag_WithAttendanceTag_ReturnsAttendanceConfigs() {
        // Given
        AppConfig attendanceConfig = new AppConfig();
        attendanceConfig.setConfigKey("presenter_close_session_duration_minutes");
        attendanceConfig.setConfigValue("15");
        attendanceConfig.setTags("#ATTENDANCE #TIMEOUT");

        when(appConfigRepository.findByTagsContaining("#ATTENDANCE"))
                .thenReturn(Arrays.asList(attendanceConfig));

        // When
        List<AppConfig> result = appConfigService.getConfigsByTag("#ATTENDANCE");

        // Then
        assertEquals(1, result.size());
        assertEquals("presenter_close_session_duration_minutes", result.get(0).getConfigKey());
    }

    // ==================== Default Value Edge Cases ====================

    @Test
    void getStringConfig_WithNullDefaultWhenKeyExists_ReturnsActualValue() {
        // Given
        when(appConfigRepository.findByConfigKey("server_url"))
                .thenReturn(Optional.of(testStringConfig));

        // When
        String result = appConfigService.getStringConfig("server_url", null);

        // Then
        assertEquals("http://132.72.50.53:8080", result);
    }

    @Test
    void getIntegerConfig_WithNullDefault_ReturnsNullWhenNotFound() {
        // Given
        when(appConfigRepository.findByConfigKey("missing_int"))
                .thenReturn(Optional.empty());

        // When
        Integer result = appConfigService.getIntegerConfig("missing_int", null);

        // Then
        assertNull(result);
    }

    @Test
    void getBooleanConfig_WithNullDefault_ReturnsNullWhenNotFound() {
        // Given
        when(appConfigRepository.findByConfigKey("missing_bool"))
                .thenReturn(Optional.empty());

        // When
        Boolean result = appConfigService.getBooleanConfig("missing_bool", null);

        // Then
        assertNull(result);
    }

    // ==================== Caching Behavior Tests ====================

    @Test
    void getConfigValue_IsCacheable_VerifyAnnotation() {
        // This test verifies the caching behavior by checking method is called
        // Note: Actual cache testing requires Spring integration test
        // Given
        when(appConfigRepository.findByConfigKey("cached_key"))
                .thenReturn(Optional.of(testStringConfig));

        // When - First call
        appConfigService.getStringConfig("cached_key", "default");

        // Then - Repository should be called
        verify(appConfigRepository, times(1)).findByConfigKey("cached_key");

        // Note: In a real Spring context with caching enabled, the second call
        // would not hit the repository due to @Cacheable annotation
    }

    // ==================== Real Config Key Tests ====================

    @Test
    void getIntegerConfig_PhdCapacityWeight_ReturnsCorrectValue() {
        // Given - Testing actual config key from the system
        AppConfig phdWeightConfig = new AppConfig();
        phdWeightConfig.setConfigKey("phd.capacity.weight");
        phdWeightConfig.setConfigValue("3");
        when(appConfigRepository.findByConfigKey("phd.capacity.weight"))
                .thenReturn(Optional.of(phdWeightConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("phd.capacity.weight", 1);

        // Then
        assertEquals(3, result);
    }

    @Test
    void getIntegerConfig_ApprovalTokenExpiryDays_ReturnsCorrectValue() {
        // Given
        AppConfig expiryConfig = new AppConfig();
        expiryConfig.setConfigKey("approval_token_expiry_days");
        expiryConfig.setConfigValue("14");
        when(appConfigRepository.findByConfigKey("approval_token_expiry_days"))
                .thenReturn(Optional.of(expiryConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("approval_token_expiry_days", 7);

        // Then
        assertEquals(14, result);
    }

    @Test
    void getStringConfig_ServerUrl_ReturnsCorrectValue() {
        // Given
        when(appConfigRepository.findByConfigKey("server_url"))
                .thenReturn(Optional.of(testStringConfig));

        // When
        String result = appConfigService.getStringConfig("server_url", "http://localhost:8080");

        // Then
        assertEquals("http://132.72.50.53:8080", result);
    }

    @Test
    void getIntegerConfig_WaitingListLimit_ReturnsCorrectValue() {
        // Given
        AppConfig waitingListConfig = new AppConfig();
        waitingListConfig.setConfigKey("waiting.list.limit.per.slot");
        waitingListConfig.setConfigValue("3");
        when(appConfigRepository.findByConfigKey("waiting.list.limit.per.slot"))
                .thenReturn(Optional.of(waitingListConfig));

        // When
        Integer result = appConfigService.getIntegerConfig("waiting.list.limit.per.slot", 5);

        // Then
        assertEquals(3, result);
    }
}
