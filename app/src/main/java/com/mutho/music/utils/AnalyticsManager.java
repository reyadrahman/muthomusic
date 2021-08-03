package com.mutho.music.utils;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mutho.music.BuildConfig;
import com.mutho.music.MuthoMusicApplication;

public class AnalyticsManager {

    private static final String TAG = "AnalyticsManager";

    private static boolean analyticsEnabled() {
        return !BuildConfig.DEBUG;
    }

    public @interface UpgradeType {
        String NAG = "Nag";
        String FOLDER = "Folder";
        String UPGRADE = "Upgrade";
    }

    public static void logChangelogViewed() {
        if (!analyticsEnabled()) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "changelog");
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "0");

        FirebaseAnalytics.getInstance(MuthoMusicApplication.getInstance())
                .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
        Answers.getInstance().logCustom(new CustomEvent("Changelog Viewed"));
    }

    public static void logScreenName(Activity activity, String name) {
        if (!analyticsEnabled()) {
            return;
        }

        CrashlyticsCore.getInstance().log(String.format("Screen: %s", name));
        FirebaseAnalytics.getInstance(MuthoMusicApplication.getInstance()).setCurrentScreen(activity, name, null);
    }

    public static void logInitialTheme(ThemeUtils.Theme theme) {
        if (!analyticsEnabled()) {
            return;
        }

        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.ITEM_ID, String.valueOf(theme.id));
        params.putString(FirebaseAnalytics.Param.ITEM_NAME, String.format("%s-%s-%s", theme.primaryColorName, theme.accentColorName, theme.isDark));
        params.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "themes");
        FirebaseAnalytics.getInstance(MuthoMusicApplication.getInstance()).logEvent(FirebaseAnalytics.Event.VIEW_ITEM, params);
    }

    public static void logRateShown() {
        if (!analyticsEnabled()) {
            return;
        }

        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.ITEM_ID, "show_rate_snackbar");
        params.putString(FirebaseAnalytics.Param.ITEM_NAME, "show_rate_snackbar");
        params.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "rate_app");

        FirebaseAnalytics.getInstance(MuthoMusicApplication.getInstance())
                .logEvent(FirebaseAnalytics.Event.VIEW_ITEM, params);
    }

    public static void logRateClicked() {
        if (!analyticsEnabled()) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "rate_snackbar");
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "0");

        FirebaseAnalytics.getInstance(MuthoMusicApplication.getInstance())
                .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

    public static void didSnow() {
        if (!analyticsEnabled()) {
            return;
        }

        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.ITEM_ID, "show_snow");
        params.putString(FirebaseAnalytics.Param.ITEM_NAME, "show_snow");
        params.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "easter_eggs");

        FirebaseAnalytics.getInstance(MuthoMusicApplication.getInstance())
                .logEvent(FirebaseAnalytics.Event.VIEW_ITEM, params);
    }

    public static void dropBreadcrumb(String tag, String breadCrumb) {

        Log.i(tag, breadCrumb);

        if (!analyticsEnabled()) {
            return;
        }

        CrashlyticsCore.getInstance().log(String.format("%s | %s", tag, breadCrumb));
    }
}