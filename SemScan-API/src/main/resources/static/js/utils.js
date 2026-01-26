/**
 * SemScan Web App - Logger
 * Comprehensive logging similar to Android app
 * Sends logs to server with source='WEB'
 */
const Logger = {
    // Log levels
    LEVELS: { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3 },
    currentLevel: 0, // Show all logs

    // Store logs for debugging
    logs: [],
    maxLogs: 500,

    // Server logging configuration
    serverQueue: [],
    maxServerQueue: 100,
    flushInterval: 30000, // 30 seconds
    flushTimer: null,
    isFlushing: false,
    serverLogLevel: 1, // Only INFO and above go to server (not DEBUG)

    /**
     * Format timestamp
     */
    timestamp() {
        return new Date().toISOString();
    },

    /**
     * Get page/component name
     */
    getSource() {
        const path = window.location.pathname;
        const page = path.split('/').pop() || 'index.html';
        return page.replace('.html', '').toUpperCase();
    },

    /**
     * Core log function
     */
    log(level, tag, message, data = null) {
        const levelNum = this.LEVELS[level] || 0;
        if (levelNum < this.currentLevel) return;

        const entry = {
            timestamp: this.timestamp(),
            level,
            source: this.getSource(),
            tag,
            message,
            data
        };

        // Store log
        this.logs.push(entry);
        if (this.logs.length > this.maxLogs) {
            this.logs.shift();
        }

        // Console output with styling
        const styles = {
            DEBUG: 'color: #9E9E9E',
            INFO: 'color: #2196F3',
            WARN: 'color: #FF9800; font-weight: bold',
            ERROR: 'color: #F44336; font-weight: bold'
        };

        const prefix = `[${entry.timestamp.split('T')[1].split('.')[0]}] [${level}] [${entry.source}] [${tag}]`;

        if (data) {
            console.log(`%c${prefix} ${message}`, styles[level], data);
        } else {
            console.log(`%c${prefix} ${message}`, styles[level]);
        }

        // For errors, also log stack trace
        if (level === 'ERROR' && data instanceof Error) {
            console.error(data);
        }

        // Queue for server logging (INFO and above)
        if (levelNum >= this.serverLogLevel) {
            this.queueForServer(level, tag, message, data);
        }
    },

    /**
     * Queue log entry for server
     */
    queueForServer(level, tag, message, data) {
        const username = localStorage.getItem('bgu_username') || null;

        // Build server log entry matching AppLogEntry DTO
        const serverEntry = {
            timestamp: Date.now(),
            level: level,
            tag: tag,
            message: message,
            source: 'WEB',
            bguUsername: username,
            userFullName: this.getUserFullName(),
            userRole: this.getUserRole(),
            deviceInfo: this.getDeviceInfo(),
            appVersion: 'web-1.0.0'
        };

        // Add exception info for errors
        if (level === 'ERROR' && data) {
            if (data instanceof Error) {
                serverEntry.exceptionType = data.name || 'Error';
                serverEntry.stackTrace = data.stack || '';
            } else if (data.stack) {
                serverEntry.exceptionType = data.exceptionType || 'Error';
                serverEntry.stackTrace = data.stack;
            }
        }

        this.serverQueue.push(serverEntry);

        // Trim queue if too large
        if (this.serverQueue.length > this.maxServerQueue) {
            this.serverQueue.shift();
        }

        // Start flush timer if not running
        if (!this.flushTimer) {
            this.startFlushTimer();
        }

        // Immediate flush for errors
        if (level === 'ERROR') {
            this.flushToServer();
        }
    },

    /**
     * Get user full name from stored user data
     */
    getUserFullName() {
        try {
            // Try semscan_user_data first (presenter data)
            const userData = localStorage.getItem('semscan_user_data');
            if (userData) {
                const user = JSON.parse(userData);
                if (user.presenter && user.presenter.name) {
                    return user.presenter.name;
                }
                if (user.name) {
                    return user.name;
                }
            }
            // Try user_data as fallback
            const fallbackData = localStorage.getItem('user_data');
            if (fallbackData) {
                const user = JSON.parse(fallbackData);
                if (user.name) return user.name;
                if (user.fullName) return user.fullName;
                if (user.firstName && user.lastName) {
                    return `${user.firstName} ${user.lastName}`;
                }
            }
        } catch (e) {
            // Ignore parse errors
        }
        return null;
    },

    /**
     * Get user role from stored user data
     */
    getUserRole() {
        try {
            const userData = localStorage.getItem('user_data');
            if (userData) {
                const user = JSON.parse(userData);
                if (user.isPresenter && user.isParticipant) return 'BOTH';
                if (user.isPresenter) return 'PRESENTER';
                if (user.isParticipant) return 'PARTICIPANT';
            }
        } catch (e) {
            // Ignore parse errors
        }
        return 'UNKNOWN';
    },

    /**
     * Get device info for web browser
     */
    getDeviceInfo() {
        const ua = navigator.userAgent;
        let browser = 'Unknown';
        let os = 'Unknown';

        // Detect browser
        if (ua.includes('Chrome') && !ua.includes('Edg')) browser = 'Chrome';
        else if (ua.includes('Safari') && !ua.includes('Chrome')) browser = 'Safari';
        else if (ua.includes('Firefox')) browser = 'Firefox';
        else if (ua.includes('Edg')) browser = 'Edge';

        // Detect OS
        if (ua.includes('iPhone')) os = 'iPhone';
        else if (ua.includes('iPad')) os = 'iPad';
        else if (ua.includes('Android')) os = 'Android';
        else if (ua.includes('Windows')) os = 'Windows';
        else if (ua.includes('Mac')) os = 'macOS';
        else if (ua.includes('Linux')) os = 'Linux';

        return `${browser} (${os})`;
    },

    /**
     * Start the flush timer
     */
    startFlushTimer() {
        if (this.flushTimer) return;
        this.flushTimer = setInterval(() => {
            this.flushToServer();
        }, this.flushInterval);
    },

    /**
     * Stop the flush timer
     */
    stopFlushTimer() {
        if (this.flushTimer) {
            clearInterval(this.flushTimer);
            this.flushTimer = null;
        }
    },

    /**
     * Flush queued logs to server
     */
    async flushToServer() {
        if (this.isFlushing || this.serverQueue.length === 0) {
            return;
        }

        this.isFlushing = true;

        // Take current queue and clear it
        const logsToSend = [...this.serverQueue];
        this.serverQueue = [];

        try {
            // Use CONFIG if available, fallback to hardcoded URL
            const baseUrl = (typeof CONFIG !== 'undefined' && CONFIG.API_BASE)
                ? CONFIG.API_BASE
                : 'http://132.72.50.53:8080/api/v1';

            const response = await fetch(`${baseUrl}/logs`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ logs: logsToSend })
            });

            if (!response.ok) {
                // Put logs back in queue on failure
                this.serverQueue = [...logsToSend, ...this.serverQueue];
                console.warn('[Logger] Failed to send logs to server:', response.status);
            }
        } catch (error) {
            // Put logs back in queue on error
            this.serverQueue = [...logsToSend, ...this.serverQueue];
            console.warn('[Logger] Error sending logs to server:', error.message);
        } finally {
            this.isFlushing = false;
        }
    },

    debug(tag, message, data = null) {
        this.log('DEBUG', tag, message, data);
    },

    info(tag, message, data = null) {
        this.log('INFO', tag, message, data);
    },

    warn(tag, message, data = null) {
        this.log('WARN', tag, message, data);
    },

    error(tag, message, data = null) {
        this.log('ERROR', tag, message, data);
    },

    /**
     * Log API request
     */
    apiRequest(method, endpoint, body = null) {
        const tag = this.getApiTag(endpoint) + '_API_REQUEST';
        this.info(tag, `${method} ${endpoint}`, body ? { body } : null);
    },

    /**
     * Log API response
     */
    apiResponse(method, endpoint, status, data) {
        const tag = this.getApiTag(endpoint) + '_API_RESPONSE';
        const level = status >= 400 ? 'ERROR' : 'INFO';
        this.log(level, tag, `${method} ${endpoint} -> ${status}`, { status, data });
    },

    /**
     * Log API error
     */
    apiError(method, endpoint, error) {
        const tag = this.getApiTag(endpoint) + '_API_ERROR';
        this.error(tag, `${method} ${endpoint} FAILED`, {
            message: error.message,
            stack: error.stack
        });
    },

    /**
     * Get API tag from endpoint
     */
    getApiTag(endpoint) {
        if (endpoint.includes('/auth/login')) return 'AUTH_LOGIN';
        if (endpoint.includes('/register')) return 'REGISTRATION';
        if (endpoint.includes('/attendance')) return 'ATTENDANCE';
        if (endpoint.includes('/waiting-list')) return 'WAITING_LIST';
        if (endpoint.includes('/home')) return 'PRESENTER_HOME';
        if (endpoint.includes('/slots')) return 'SLOTS';
        if (endpoint.includes('/sessions')) return 'SESSIONS';
        return 'API';
    },

    /**
     * Log page lifecycle
     */
    pageLoad(pageName) {
        this.info('PAGE_LOAD', `${pageName} loaded`);
    },

    pageInit(pageName) {
        this.info('PAGE_INIT', `${pageName} initializing...`);
    },

    pageReady(pageName) {
        this.info('PAGE_READY', `${pageName} ready`);
    },

    /**
     * Log user action
     */
    userAction(action, details = null) {
        this.info('USER_ACTION', action, details);
    },

    /**
     * Log UI state change
     */
    uiState(component, state, details = null) {
        this.debug('UI_STATE', `${component}: ${state}`, details);
    },

    /**
     * Get all logs (for debugging)
     */
    getLogs() {
        return this.logs;
    },

    /**
     * Export logs as text
     */
    exportLogs() {
        return this.logs.map(l =>
            `${l.timestamp} [${l.level}] [${l.source}] [${l.tag}] ${l.message}` +
            (l.data ? ` | ${JSON.stringify(l.data)}` : '')
        ).join('\n');
    },

    /**
     * Clear logs
     */
    clearLogs() {
        this.logs = [];
        this.info('LOGGER', 'Logs cleared');
    },

    /**
     * Initialize logger - set up page unload handler
     */
    init() {
        // Flush logs before page unload
        window.addEventListener('beforeunload', () => {
            this.stopFlushTimer();
            // Use sendBeacon for reliable delivery during page unload
            if (this.serverQueue.length > 0) {
                const baseUrl = (typeof CONFIG !== 'undefined' && CONFIG.API_BASE)
                    ? CONFIG.API_BASE
                    : 'http://132.72.50.53:8080/api/v1';
                const blob = new Blob(
                    [JSON.stringify({ logs: this.serverQueue })],
                    { type: 'application/json' }
                );
                navigator.sendBeacon(`${baseUrl}/logs`, blob);
            }
        });

        // Also flush on visibility change (when tab becomes hidden)
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'hidden') {
                this.flushToServer();
            }
        });
    }
};

// Initialize logger
Logger.init();

// Make Logger available globally for debugging
window.Logger = Logger;

/**
 * SemScan Web App - Utility Functions
 */

const Utils = {
    /**
     * Show toast notification
     * @param {string} message - Message to display
     * @param {string} type - 'success', 'error', or 'info'
     * @param {number} duration - Duration in ms (optional)
     */
    showToast(message, type = 'info', duration = null) {
        // Get or create toast container
        let container = document.querySelector('.toast-container');
        if (!container) {
            container = document.createElement('div');
            container.className = 'toast-container';
            document.body.appendChild(container);
        }

        // Set default duration based on type
        if (!duration) {
            duration = type === 'error' ? CONFIG.TOAST_ERROR :
                       type === 'success' ? CONFIG.TOAST_SUCCESS :
                       CONFIG.TOAST_INFO;
        }

        // Create toast element
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <span>${this.escapeHtml(message)}</span>
        `;

        container.appendChild(toast);

        // Auto remove after duration
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(20px)';
            setTimeout(() => toast.remove(), 300);
        }, duration);

        // Allow click to dismiss
        toast.addEventListener('click', () => {
            toast.remove();
        });
    },

    /**
     * Show loading overlay
     */
    showLoading() {
        let overlay = document.querySelector('.loading-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.className = 'loading-overlay';
            overlay.innerHTML = '<div class="spinner"></div>';
            document.body.appendChild(overlay);
        }
        overlay.classList.remove('hidden');
    },

    /**
     * Hide loading overlay
     */
    hideLoading() {
        const overlay = document.querySelector('.loading-overlay');
        if (overlay) {
            overlay.classList.add('hidden');
        }
    },

    /**
     * Escape HTML to prevent XSS
     * @param {string} text - Text to escape
     * @returns {string} - Escaped text
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    /**
     * Format date for display
     * @param {string} dateStr - Date string (YYYY-MM-DD)
     * @returns {string} - Formatted date
     */
    formatDate(dateStr) {
        const date = new Date(dateStr + 'T00:00:00');
        const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
        return date.toLocaleDateString('he-IL', options);
    },

    /**
     * Format time for display
     * @param {string} timeStr - Time string (HH:MM:SS)
     * @returns {string} - Formatted time (HH:MM)
     */
    formatTime(timeStr) {
        if (!timeStr) return '';
        return timeStr.substring(0, 5);
    },

    /**
     * Format date and time together
     * @param {string} dateStr - Date string
     * @param {string} startTime - Start time
     * @param {string} endTime - End time
     * @returns {string} - Formatted string
     */
    formatSlotDateTime(dateStr, startTime, endTime) {
        const date = this.formatDate(dateStr);
        const start = this.formatTime(startTime);
        const end = this.formatTime(endTime);
        return `${date} | ${start} - ${end}`;
    },

    /**
     * Get slot status class
     * @param {string} status - 'FREE', 'SEMI', 'FULL'
     * @returns {string} - CSS class
     */
    getSlotStatusClass(status) {
        switch (status?.toUpperCase()) {
            case 'FREE': return 'available';
            case 'SEMI': return 'partial';
            case 'FULL': return 'full';
            default: return 'available';
        }
    },

    /**
     * Get slot status text
     * @param {string} status - 'FREE', 'SEMI', 'FULL'
     * @param {number} registered - Number of registered users
     * @param {number} capacity - Slot capacity
     * @returns {string} - Status text
     */
    getSlotStatusText(status, registered = 0, capacity = 3) {
        switch (status?.toUpperCase()) {
            case 'FREE': return 'Available';
            case 'SEMI': return `${registered}/${capacity} registered`;
            case 'FULL': return 'Full';
            default: return 'Available';
        }
    },

    /**
     * Check if user is logged in
     * @returns {boolean}
     */
    isLoggedIn() {
        return !!localStorage.getItem(CONFIG.SESSION_KEY);
    },

    /**
     * Get current username
     * @returns {string|null}
     */
    getUsername() {
        return localStorage.getItem(CONFIG.USERNAME_KEY);
    },

    /**
     * Get stored user data
     * @returns {object|null}
     */
    getUserData() {
        const data = localStorage.getItem(CONFIG.USER_DATA_KEY);
        return data ? JSON.parse(data) : null;
    },

    /**
     * Require authentication - redirect to login if not logged in
     */
    requireAuth() {
        if (!this.isLoggedIn()) {
            window.location.href = 'index.html';
            return false;
        }
        return true;
    },

    /**
     * Logout - clear session and redirect
     */
    logout() {
        localStorage.removeItem(CONFIG.SESSION_KEY);
        localStorage.removeItem(CONFIG.USERNAME_KEY);
        localStorage.removeItem(CONFIG.USER_DATA_KEY);
        window.location.href = 'index.html';
    },

    /**
     * Parse query string parameters
     * @returns {object} - Query parameters
     */
    getQueryParams() {
        const params = {};
        const search = window.location.search.substring(1);
        if (search) {
            search.split('&').forEach(pair => {
                const [key, value] = pair.split('=');
                params[decodeURIComponent(key)] = decodeURIComponent(value || '');
            });
        }
        return params;
    },

    /**
     * Debounce function
     * @param {Function} func - Function to debounce
     * @param {number} wait - Wait time in ms
     * @returns {Function}
     */
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
};
