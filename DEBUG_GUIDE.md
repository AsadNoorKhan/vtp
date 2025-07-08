# VTPL App Debugging Guide

## Common Issues and Solutions

### 1. App Crashes After Permission Request

**Symptoms:** App closes immediately after requesting location/phone permissions

**Possible Causes:**
- Missing FOREGROUND_SERVICE permission (Android 9+)
- Service initialization errors
- IMEI access permission issues

**Solutions Applied:**
- ✅ Added FOREGROUND_SERVICE and FOREGROUND_SERVICE_LOCATION permissions
- ✅ Added try-catch blocks around service initialization
- ✅ Improved IMEI retrieval with fallback to Android ID
- ✅ Added comprehensive error logging

### 2. Network Connection Issues

**Symptoms:** "CLEARTEXT communication not permitted" error in logs

**Cause:** Android 9+ blocks HTTP connections by default

**Solution Applied:**
- ✅ Added network security configuration to allow HTTP connections to avl.valstracking.com
- ✅ Created `network_security_config.xml` file

### 3. IMEI Access Issues

**Symptoms:** "Cannot access IMEI due to permissions" warning in logs

**Cause:** Modern Android versions restrict access to device identifiers

**Solution Applied:**
- ✅ Added fallback to Android ID when IMEI is not available
- ✅ Improved error handling for device identification
- ✅ Added detailed logging for debugging

### 4. Permission Issues

**Check if permissions are granted:**
1. Go to Settings > Apps > VTPL > Permissions
2. Ensure Location and Phone permissions are enabled

**If permissions are denied:**
1. Uninstall and reinstall the app
2. Grant permissions when prompted

### 5. Service Not Starting

**Check the app UI:**
- The app now shows status information:
  - "Permissions: Granted/Denied"
  - "Service: Running/Stopped"
  - Overall status message

**If service fails to start:**
1. Check logcat for error messages
2. Ensure all permissions are granted
3. Restart the app

### 6. Debugging Steps

**To view logs:**
1. Connect device to computer
2. Run: `adb logcat | grep -E "(MainActivity|DataCollectionService|VTPL)"`

**Common log messages:**
- `"Permissions granted"` - All permissions are working
- `"Background service started"` - Service started successfully
- `"Data sent successfully"` - Data collection is working
- `"Cannot access IMEI due to permissions, using Android ID"` - Using fallback device ID (normal)
- `"Sending data: IMEI=..., Lat=..., Lng=..., Battery=...%"` - Data being sent

### 7. Testing the App

1. **Install the app**
2. **Grant permissions when prompted:**
   - Location (Fine and Coarse)
   - Phone state
3. **Check the UI status:**
   - Should show "Data collection active"
   - Service should show "Running"
4. **Check notification:**
   - Should see "VTPL Data Collection" notification
5. **Monitor logs for data collection**

### 8. If App Still Crashes

1. **Check Android version compatibility**
2. **Ensure device has internet connection**
3. **Try on a different device**
4. **Check if any security apps are blocking the service**

## Recent Fixes Applied

1. **Added missing permissions** for foreground services
2. **Improved error handling** in service initialization
3. **Added fallback for IMEI access** (uses Android ID if not available)
4. **Enhanced UI feedback** to show app status
5. **Added comprehensive logging** for debugging
6. **Fixed network security** to allow HTTP connections
7. **Improved device identification** with Android ID fallback

## Current Status

Based on your logs, the app is now working correctly:
- ✅ App no longer crashes after permissions
- ✅ Service starts successfully
- ✅ Location updates are working
- ✅ IMEI fallback to Android ID is working
- ✅ Network security policy is now fixed

The app should now successfully send data to the server once location is available. 