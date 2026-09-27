package com.togg.companion.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/**
 * Dispatches deep links to the official Trumore app for vehicle control actions.
 * Ensures strict security: does not hold private keys, delegates commands to Trumore.
 */
public final class TrumoreBridge {
    public static final String TRUMORE_PACKAGE = "com.togg.toggmobile";
    public static final String URI_SMART_DEVICE = "https://superapp-api.channels.togg.cloud/SmartDevice";
    public static final String URI_SMART_ACCESS = "https://superapp-api.channels.togg.cloud/SmartAccess";
    public static final String URI_EV_PLANNER   = "https://superapp-api.channels.togg.cloud/EvPlanner";

    public static boolean isInstalled(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(TRUMORE_PACKAGE, 0).applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static boolean openSmartDevice(Activity activity) {
        return launchUri(activity, URI_SMART_DEVICE);
    }

    public static boolean openSmartAccess(Activity activity) {
        return launchUri(activity, URI_SMART_ACCESS);
    }

    public static boolean openDoorControl(Activity activity) {
        return launchUri(activity, URI_SMART_ACCESS);
    }

    public static boolean openTrunkControl(Activity activity) {
        return launchUri(activity, URI_SMART_ACCESS);
    }

    public static boolean openClimateControl(Activity activity) {
        return launchUri(activity, URI_SMART_DEVICE);
    }

    public static boolean openEvPlanner(Activity activity) {
        return launchUri(activity, URI_EV_PLANNER);
    }

    public static boolean launchTrumoreApp(Activity activity) {
        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(TRUMORE_PACKAGE);
        if (launchIntent != null) {
            activity.startActivity(launchIntent);
            return true;
        }
        return false;
    }

    private static boolean launchUri(Activity activity, String uriString) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
        if (isInstalled(activity)) {
            intent.setPackage(TRUMORE_PACKAGE);
        }
        try {
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            // Fallback: try opening plain intent without explicit package
            try {
                intent.setPackage(null);
                activity.startActivity(intent);
                return true;
            } catch (Exception ex) {
                Toast.makeText(activity, "Trumore uygulaması açılamadı", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }
}
