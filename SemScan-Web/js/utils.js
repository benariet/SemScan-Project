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
