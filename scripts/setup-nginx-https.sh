#!/bin/bash
# Setup script for nginx HTTPS reverse proxy for SemScan API
# This eliminates browser warnings by using standard port 443

set -e

echo "=========================================="
echo "SemScan API - Nginx HTTPS Setup"
echo "=========================================="
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

# Check if nginx is installed
if ! command -v nginx &> /dev/null; then
    echo "Installing nginx..."
    if command -v apt-get &> /dev/null; then
        apt-get update
        apt-get install -y nginx
    elif command -v yum &> /dev/null; then
        yum install -y nginx
    else
        echo "Error: Cannot determine package manager. Please install nginx manually."
        exit 1
    fi
fi

# Create SSL directories
echo "Creating SSL directories..."
mkdir -p /etc/ssl/certs
mkdir -p /etc/ssl/private

# Check if SSL certificate exists
if [ ! -f /etc/ssl/certs/semscan-api.crt ] || [ ! -f /etc/ssl/private/semscan-api.key ]; then
    echo ""
    echo "SSL certificate not found. Choose an option:"
    echo "1) Use Let's Encrypt (requires domain name)"
    echo "2) Create self-signed certificate (for IP address only)"
    read -p "Enter choice [1-2]: " choice
    
    case $choice in
        1)
            if ! command -v certbot &> /dev/null; then
                echo "Installing certbot..."
                if command -v apt-get &> /dev/null; then
                    apt-get install -y certbot python3-certbot-nginx
                elif command -v yum &> /dev/null; then
                    yum install -y certbot python3-certbot-nginx
                fi
            fi
            read -p "Enter your domain name: " domain
            certbot --nginx -d $domain -d www.$domain
            ;;
        2)
            echo "Creating self-signed certificate..."
            read -p "Enter server IP or hostname [132.72.50.53]: " server_name
            server_name=${server_name:-132.72.50.53}
            
            openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
                -keyout /etc/ssl/private/semscan-api.key \
                -out /etc/ssl/certs/semscan-api.crt \
                -subj "/C=IL/ST=Israel/L=Beer-Sheva/O=BGU/CN=$server_name"
            
            chmod 600 /etc/ssl/private/semscan-api.key
            chmod 644 /etc/ssl/certs/semscan-api.crt
            echo "Self-signed certificate created."
            ;;
        *)
            echo "Invalid choice. Exiting."
            exit 1
            ;;
    esac
else
    echo "SSL certificate already exists."
fi

# Copy nginx configuration
echo ""
echo "Setting up nginx configuration..."

# Look for nginx config in multiple locations
NGINX_CONF=""
if [ -f "/opt/semscan-api/config/nginx-semscan.conf" ]; then
    NGINX_CONF="/opt/semscan-api/config/nginx-semscan.conf"
elif [ -f "~/nginx-semscan.conf" ]; then
    NGINX_CONF="~/nginx-semscan.conf"
elif [ -f "nginx-semscan.conf" ]; then
    NGINX_CONF="nginx-semscan.conf"
elif [ -f "./nginx-semscan.conf" ]; then
    NGINX_CONF="./nginx-semscan.conf"
fi

if [ -n "$NGINX_CONF" ] && [ -f "$NGINX_CONF" ]; then
    echo "Found nginx config at: $NGINX_CONF"
    cp "$NGINX_CONF" /etc/nginx/sites-available/semscan-api
    
    # Create symlink if it doesn't exist
    if [ ! -L /etc/nginx/sites-enabled/semscan-api ]; then
        ln -s /etc/nginx/sites-available/semscan-api /etc/nginx/sites-enabled/
    fi
    
    # Remove default nginx site if it exists
    if [ -L /etc/nginx/sites-enabled/default ]; then
        rm /etc/nginx/sites-enabled/default
    fi
else
    echo "Error: nginx-semscan.conf not found!"
    echo "Searched in:"
    echo "  - /opt/semscan-api/config/nginx-semscan.conf"
    echo "  - ~/nginx-semscan.conf"
    echo "  - ./nginx-semscan.conf"
    echo "  - nginx-semscan.conf"
    exit 1
fi

# Test nginx configuration
echo ""
echo "Testing nginx configuration..."
nginx -t

if [ $? -eq 0 ]; then
    echo "Nginx configuration is valid."
    
    # Reload nginx
    echo "Reloading nginx..."
    systemctl reload nginx
    
    # Enable nginx to start on boot
    systemctl enable nginx
    
    echo ""
    echo "=========================================="
    echo "Setup complete!"
    echo "=========================================="
    echo ""
    echo "Nginx is now configured to:"
    echo "  - Listen on port 80 (HTTP) and redirect to HTTPS"
    echo "  - Listen on port 443 (HTTPS) with SSL"
    echo "  - Forward requests to SemScan API on port 8080"
    echo ""
    echo "Test the setup:"
    echo "  curl -k https://132.72.50.53/api/v1/info/config"
    echo ""
    echo "IMPORTANT: Make sure port 8080 is NOT exposed to the internet!"
    echo "Only ports 80 and 443 should be accessible from outside."
    echo ""
else
    echo "Error: Nginx configuration test failed!"
    exit 1
fi

