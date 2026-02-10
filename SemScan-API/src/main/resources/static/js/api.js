/**
 * SemScan API Client
 * Handles all HTTP requests to the backend API
 */
const API = {
    /**
     * Make an API request
     * @param {string} endpoint - API endpoint (e.g., '/auth/login')
     * @param {object} options - Fetch options
     * @returns {Promise<object>} - Response data
     */
    async request(endpoint, options = {}) {
        const url = CONFIG.API_BASE + endpoint;
        const method = options.method || 'GET';

        Logger.apiRequest(method, endpoint, options.body ? JSON.parse(options.body) : null);

        const defaultHeaders = {
            'Content-Type': 'application/json',
            'X-Device-Info': this.getDeviceInfo(),
            'X-App-Version': '1.0.0-web'
        };

        // Add auth token if exists
        const token = localStorage.getItem(CONFIG.SESSION_KEY);
        if (token) {
            defaultHeaders['Authorization'] = `Bearer ${token}`;
            Logger.debug('API_AUTH', 'Using stored auth token');
        } else {
            Logger.debug('API_AUTH', 'No auth token found');
        }

        const config = {
            ...options,
            headers: {
                ...defaultHeaders,
                ...options.headers
            }
        };

        try {
            Logger.debug('API_FETCH', `Fetching ${url}...`);
            const response = await fetch(url, config);
            Logger.debug('API_FETCH', `Response received: ${response.status} ${response.statusText}`);

            // Handle session expiry (but NOT for login endpoint - that's just wrong credentials)
            if (response.status === 401 || response.status === 403) {
                const isLoginEndpoint = endpoint.includes('/auth/login');
                if (isLoginEndpoint) {
                    // Login failure - let it fall through to normal error handling
                    Logger.warn('AUTH_FAILED', `Authentication failed (${response.status})`, { endpoint });
                    // Don't throw here - let response parsing handle the error message
                } else {
                    // Other endpoints - session expired
                    Logger.warn('SESSION_EXPIRED', `Session expired (${response.status})`, { endpoint });
                    this.handleSessionExpired();
                    throw new Error('Session expired');
                }
            }

            // Parse response
            const contentType = response.headers.get('content-type');
            let data;

            Logger.debug('API_PARSE', `Content-Type: ${contentType}`);

            if (contentType && contentType.includes('application/json')) {
                const text = await response.text();
                Logger.debug('API_PARSE', `Raw response: ${text.substring(0, 500)}...`);
                try {
                    data = JSON.parse(text);
                } catch (parseError) {
                    Logger.error('API_PARSE_ERROR', 'Failed to parse JSON response', {
                        error: parseError.message,
                        text: text.substring(0, 200)
                    });
                    throw new Error('Invalid JSON response from server');
                }
            } else {
                data = await response.text();
                Logger.debug('API_PARSE', `Text response: ${data.substring(0, 200)}`);
            }

            // Log response - use WARN for login auth failures (expected behavior), ERROR for other failures
            const isLoginEndpoint = endpoint.includes('/auth/login');
            const isAuthFailure = isLoginEndpoint && (response.status === 401 || response.status === 403);
            Logger.apiResponse(method, endpoint, response.status, data, isAuthFailure);

            if (!response.ok) {
                const errorMsg = data.message || data.error || data || 'Request failed';
                if (isAuthFailure) {
                    // Login failure is expected behavior, not an error
                    Logger.warn('AUTH_INVALID_CREDENTIALS', `${errorMsg}`, { status: response.status });
                } else {
                    Logger.error('API_ERROR', `Request failed: ${errorMsg}`, { status: response.status, data });
                }
                throw new Error(errorMsg);
            }

            return data;
        } catch (error) {
            // Don't log as error for session expired or login auth failures
            const isLoginEndpoint = endpoint.includes('/auth/login');
            const isExpectedFailure = error.message === 'Session expired' ||
                (isLoginEndpoint && error.message.includes('Invalid'));
            if (!isExpectedFailure) {
                Logger.apiError(method, endpoint, error);
            }
            throw error;
        }
    },

    /**
     * GET request
     */
    async get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    },

    /**
     * POST request
     */
    async post(endpoint, body) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(body)
        });
    },

    /**
     * DELETE request
     */
    async delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    },

    /**
     * Get device info string for logging
     */
    getDeviceInfo() {
        const ua = navigator.userAgent;
        const platform = navigator.platform || 'Unknown';

        // Detect browser
        let browser = 'Unknown';
        if (ua.includes('Safari') && !ua.includes('Chrome')) {
            browser = 'Safari';
        } else if (ua.includes('Chrome')) {
            browser = 'Chrome';
        } else if (ua.includes('Firefox')) {
            browser = 'Firefox';
        } else if (ua.includes('Edge')) {
            browser = 'Edge';
        }

        // Detect OS
        let os = 'Unknown';
        if (ua.includes('iPhone') || ua.includes('iPad')) {
            os = 'iOS';
        } else if (ua.includes('Android')) {
            os = 'Android';
        } else if (ua.includes('Windows')) {
            os = 'Windows';
        } else if (ua.includes('Mac')) {
            os = 'macOS';
        }

        const deviceInfo = `${browser} on ${os} (Web)`;
        Logger.debug('DEVICE_INFO', deviceInfo);
        return deviceInfo;
    },

    /**
     * Handle session expiry - redirect to login
     */
    handleSessionExpired() {
        Logger.warn('SESSION_HANDLER', 'Clearing session and redirecting to login');

        localStorage.removeItem(CONFIG.SESSION_KEY);
        localStorage.removeItem(CONFIG.USERNAME_KEY);
        localStorage.removeItem(CONFIG.USER_DATA_KEY);
        // Also clear legacy keys
        localStorage.removeItem('bgu_username');
        localStorage.removeItem('user_data');

        if (!window.location.pathname.includes('index.html')) {
            Logger.info('SESSION_HANDLER', 'Redirecting to login page');
            window.location.href = 'index.html?expired=1';
        }
    },

    // ============ Auth Endpoints ============

    /**
     * Login with BGU credentials
     */
    async login(username, password) {
        Logger.info('AUTH', `Attempting login for user: ${username}`);
        try {
            const result = await this.post('/auth/login', { username, password });
            Logger.info('AUTH_LOGIN_SUCCESS', `Login successful for ${username}`);
            return result;
        } catch (error) {
            // Login failure with wrong credentials is expected behavior, use WARN not ERROR
            Logger.warn('AUTH_LOGIN_FAILED', `Login failed for ${username}`, { error: error.message });
            throw error;
        }
    },

    // ============ Presenter Endpoints ============

    /**
     * Get presenter home data (slots)
     */
    async getPresenterHome(username) {
        Logger.info('PRESENTER_HOME', `Loading presenter home for: ${username}`);
        try {
            const result = await this.get(`/presenters/${username}/home`);
            Logger.info('PRESENTER_HOME_LOADED', `Loaded ${result.slotCatalog?.length || 0} slots`, {
                hasMySlot: !!result.mySlot,
                hasWaitingList: !!result.myWaitingListSlot
            });
            return result;
        } catch (error) {
            Logger.error('PRESENTER_HOME_FAILED', `Failed to load presenter home`, { error: error.message });
            throw error;
        }
    },

    /**
     * Register for a slot
     */
    async registerForSlot(username, slotId, data) {
        Logger.info('REGISTRATION', `Registering ${username} for slot ${slotId}`, data);
        try {
            const result = await this.post(`/presenters/${username}/home/slots/${slotId}/register`, data);
            Logger.info('REGISTRATION_SUCCESS', `Registration successful`, result);
            return result;
        } catch (error) {
            Logger.error('REGISTRATION_FAILED', `Registration failed`, { error: error.message });
            throw error;
        }
    },

    /**
     * Cancel registration
     */
    async cancelRegistration(username, slotId) {
        Logger.info('REGISTRATION_CANCEL', `Cancelling registration for ${username} slot ${slotId}`);
        try {
            const result = await this.delete(`/presenters/${username}/home/slots/${slotId}/register`);
            Logger.info('REGISTRATION_CANCEL_SUCCESS', `Cancellation successful`);
            return result;
        } catch (error) {
            Logger.error('REGISTRATION_CANCEL_FAILED', `Cancellation failed`, { error: error.message });
            throw error;
        }
    },

    /**
     * Open attendance window (get QR code)
     */
    async openAttendance(username, slotId) {
        Logger.info('ATTENDANCE_OPEN', `Opening attendance for ${username} slot ${slotId}`);
        try {
            const result = await this.post(`/presenters/${username}/home/slots/${slotId}/attendance/open`, {});
            Logger.info('ATTENDANCE_OPEN_SUCCESS', `Attendance opened`, {
                hasQrCode: !!result.qrCodeBase64 || !!result.qrCodeUrl
            });
            return result;
        } catch (error) {
            Logger.error('ATTENDANCE_OPEN_FAILED', `Failed to open attendance`, { error: error.message });
            throw error;
        }
    },

    /**
     * Get QR code for open session
     */
    async getQrCode(username, slotId) {
        Logger.info('QR_CODE', `Fetching QR code for ${username} slot ${slotId}`);
        return this.get(`/presenters/${username}/home/slots/${slotId}/attendance/qr`);
    },

    // ============ Waiting List Endpoints ============

    /**
     * Join waiting list
     */
    async joinWaitingList(slotId, data) {
        Logger.info('WAITING_LIST_JOIN', `Joining waiting list for slot ${slotId}`, data);
        try {
            const result = await this.post(`/slots/${slotId}/waiting-list`, data);
            Logger.info('WAITING_LIST_JOIN_SUCCESS', `Joined waiting list`);
            return result;
        } catch (error) {
            Logger.error('WAITING_LIST_JOIN_FAILED', `Failed to join waiting list`, { error: error.message });
            throw error;
        }
    },

    /**
     * Leave waiting list
     */
    async leaveWaitingList(slotId, username) {
        Logger.info('WAITING_LIST_LEAVE', `Leaving waiting list for slot ${slotId}`);
        try {
            const result = await this.delete(`/slots/${slotId}/waiting-list?username=${encodeURIComponent(username)}`);
            Logger.info('WAITING_LIST_LEAVE_SUCCESS', `Left waiting list`);
            return result;
        } catch (error) {
            Logger.error('WAITING_LIST_LEAVE_FAILED', `Failed to leave waiting list`, { error: error.message });
            throw error;
        }
    },

    // ============ Participant Endpoints ============

    /**
     * Get open sessions for attendance
     */
    async getOpenSessions() {
        Logger.info('SESSIONS', `Fetching open sessions`);
        try {
            const result = await this.get('/sessions/open');
            Logger.info('SESSIONS_LOADED', `Found ${result.length || 0} open sessions`);
            return result;
        } catch (error) {
            Logger.error('SESSIONS_FAILED', `Failed to fetch sessions`, { error: error.message });
            throw error;
        }
    },

    /**
     * Submit QR attendance
     */
    async submitAttendance(qrData) {
        Logger.info('ATTENDANCE_SUBMIT', `Submitting attendance`, qrData);
        try {
            const result = await this.post('/attendance', qrData);
            Logger.info('ATTENDANCE_SUBMIT_SUCCESS', `Attendance submitted successfully`);
            return result;
        } catch (error) {
            Logger.error('ATTENDANCE_SUBMIT_FAILED', `Failed to submit attendance`, { error: error.message });
            throw error;
        }
    },

    // ============ User Endpoints ============

    /**
     * Update user profile/details
     */
    async upsertUser(username, data) {
        Logger.info('USER_UPSERT', `Updating user details for: ${username}`, data);
        try {
            // POST to /users endpoint (same as Android app)
            // bguUsername is included in the body, not the URL
            const result = await this.post('/users', data);
            Logger.info('USER_UPSERT_SUCCESS', `User details updated`);
            return result;
        } catch (error) {
            Logger.error('USER_UPSERT_FAILED', `Failed to update user details`, { error: error.message });
            throw error;
        }
    }
};

// Log API module initialization
Logger.info('API_INIT', `API module loaded, base URL: ${CONFIG.API_BASE}`);
