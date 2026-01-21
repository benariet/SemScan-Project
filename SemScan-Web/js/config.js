/**
 * SemScan Web App Configuration
 */
const CONFIG = {
    // API Base URL - change when deploying via Cloudflare Tunnel
    API_BASE: 'http://132.72.50.53:8080/api/v1',

    // Toast durations (ms)
    TOAST_SUCCESS: 5000,
    TOAST_ERROR: 10000,
    TOAST_INFO: 6000,

    // Session
    SESSION_KEY: 'semscan_session',
    USERNAME_KEY: 'semscan_username',
    USER_DATA_KEY: 'semscan_user_data',

    // QR Scanner
    QR_SCAN_FPS: 10,
    QR_BOX_SIZE: 250
};

// Freeze config to prevent accidental modification
Object.freeze(CONFIG);
