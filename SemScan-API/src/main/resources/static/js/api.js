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

        const defaultHeaders = {
            'Content-Type': 'application/json',
            'X-Device-Info': this.getDeviceInfo(),
            'X-App-Version': '1.0.0-web'
        };

        // Add auth token if exists
        const token = localStorage.getItem(CONFIG.SESSION_KEY);
        if (token) {
            defaultHeaders['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...options,
            headers: {
                ...defaultHeaders,
                ...options.headers
            }
        };

        try {
            const response = await fetch(url, config);

            // Handle session expiry
            if (response.status === 401 || response.status === 403) {
                this.handleSessionExpired();
                throw new Error('Session expired');
            }

            // Parse response
            const contentType = response.headers.get('content-type');
            let data;
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                data = await response.text();
            }

            if (!response.ok) {
                throw new Error(data.message || data || 'Request failed');
            }

            return data;
        } catch (error) {
            console.error('API Error:', error);
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

        return `${browser} on ${os} (Web)`;
    },

    /**
     * Handle session expiry - redirect to login
     */
    handleSessionExpired() {
        localStorage.removeItem(CONFIG.SESSION_KEY);
        localStorage.removeItem(CONFIG.USERNAME_KEY);
        localStorage.removeItem(CONFIG.USER_DATA_KEY);

        if (!window.location.pathname.includes('index.html')) {
            window.location.href = 'index.html?expired=1';
        }
    },

    // ============ Auth Endpoints ============

    /**
     * Login with BGU credentials
     */
    async login(username, password) {
        return this.post('/auth/login', { username, password });
    },

    // ============ Presenter Endpoints ============

    /**
     * Get presenter home data (slots)
     */
    async getPresenterHome(username) {
        return this.get(`/presenters/${username}/home`);
    },

    /**
     * Register for a slot
     */
    async registerForSlot(username, slotId, data) {
        return this.post(`/presenters/${username}/home/slots/${slotId}/register`, data);
    },

    /**
     * Cancel registration
     */
    async cancelRegistration(username, slotId) {
        return this.delete(`/presenters/${username}/home/slots/${slotId}/register`);
    },

    /**
     * Open attendance window (get QR code)
     */
    async openAttendance(username, slotId) {
        return this.post(`/presenters/${username}/home/slots/${slotId}/attendance/open`, {});
    },

    /**
     * Get QR code for open session
     */
    async getQrCode(username, slotId) {
        return this.get(`/presenters/${username}/home/slots/${slotId}/attendance/qr`);
    },

    // ============ Waiting List Endpoints ============

    /**
     * Join waiting list
     */
    async joinWaitingList(slotId, data) {
        return this.post(`/slots/${slotId}/waiting-list`, data);
    },

    /**
     * Leave waiting list
     */
    async leaveWaitingList(slotId, username) {
        return this.delete(`/slots/${slotId}/waiting-list?username=${username}`);
    },

    // ============ Participant Endpoints ============

    /**
     * Get open sessions for attendance
     */
    async getOpenSessions() {
        return this.get('/sessions/open');
    },

    /**
     * Submit QR attendance
     */
    async submitAttendance(qrData) {
        return this.post('/attendance', qrData);
    }
};
