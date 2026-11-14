#!/bin/bash

# Configuration
RELEASE_PKG="dev.yoanndev90.statusbarhider"
DEBUG_PKG="dev.yoanndev90.statusbarhider.debug"

# Function to grant permissions to a specific package
grant_permissions() {
    local PKG=$1

    # Check if package is installed
    if ! adb shell pm list packages | grep -q "$PKG"; then
        echo "Skipping $PKG (not installed)"
        return
    fi

    echo "Granting permissions for $PKG..."

    # Runtime Permissions (essential for status bar hider)
    adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
    adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS

    # Usage Stats (required by PACKAGE_USAGE_STATS declared in manifest)
    adb shell appops set $PKG GET_USAGE_STATS allow

    # DND Access (Notification Policy)
    adb shell cmd notification allow_dnd $PKG

    # Enable Services
    # Notification Listener
    LISTENER="$PKG/overlay.NotifListenerService"
    adb shell settings put secure enabled_notification_listeners $LISTENER

    # Accessibility Service
    ACCESSIBILITY="$PKG/overlay.LockScreenOverlayService"
    adb shell settings put secure enabled_accessibility_services $ACCESSIBILITY
    adb shell settings put secure accessibility_enabled 1

    echo "Finished $PKG"
}

# Run for both
grant_permissions "$RELEASE_PKG"
grant_permissions "$DEBUG_PKG"

echo "All tasks complete! You might need to restart the apps for some changes to take effect."
