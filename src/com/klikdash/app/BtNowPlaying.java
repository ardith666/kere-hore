package com.klikdash.app;

import android.service.notification.NotificationListenerService;

/**
 * Kosong. Hanya dibutuhkan agar MediaSessionManager.getActiveSessions() boleh membaca sesi A2DP
 * (com.android.bluetooth/A2dpMediaBrowserService) di firmware yang tidak mengirim broadcast BT sendiri.
 */
public class BtNowPlaying extends NotificationListenerService { }
