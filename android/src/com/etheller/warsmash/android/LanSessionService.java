package com.etheller.warsmash.android;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Runs while a LAN session lives, so the process and the network thread of the game stay alive (and keep releasing
 * turns, answering the host and telling it the app is away) when the player switches to another app. The system
 * allows this only with a visible notification; if the user turned notifications off the service still runs.
 * <p>
 * The type is special use: the service is neither a media, location nor data transfer task, and the app is not
 * distributed through a store that reviews the declaration. Connected device needs a Bluetooth, USB or similar
 * device, and data sync is cut off after six hours on Android 15.
 */
public final class LanSessionService extends Service {
	private static final String CHANNEL = "lan-session";
	private static final int NOTIFICATION_ID = 6112;

	private static final Object STATE = new Object();
	private static boolean wanted;
	private static LanSessionService running;
	private static int lastStartId;

	/** Starts the service; the system gives it a few seconds to show its notification. */
	static void start(final Context context) {
		synchronized (STATE) {
			wanted = true;
		}
		try {
			context.startForegroundService(new Intent(context, LanSessionService.class));
		}
		catch (final RuntimeException e) {
			synchronized (STATE) {
				wanted = false;
			}
			throw e;
		}
	}

	/**
	 * A service stopped before the system created it makes the system complain that it never went foreground. So a stop
	 * that comes too early only clears the wish: the service still shows its notification, then ends by itself.
	 */
	static void stop(final Context context) {
		synchronized (STATE) {
			wanted = false;
			if (running != null) {
				running.stopSelf(lastStartId);
			}
		}
	}

	/** Whether Android would show the notification (before Android 13 it always does). */
	public static boolean notificationsAllowed(final Context context) {
		return (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) || (context
				.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
	}

	/** Asks for the notification permission (Android 13+) once the player opens LAN play; nothing else depends on it. */
	public static void requestNotificationPermission(final Activity activity, final int requestCode) {
		if (!notificationsAllowed(activity)) {
			activity.requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, requestCode);
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		synchronized (STATE) {
			running = this;
		}
		// Before anything can stop it: a service started as foreground that never shows its notification crashes the app.
		showNotification();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		showNotification();
		synchronized (STATE) {
			lastStartId = startId;
			if (!wanted) {
				stopSelf(startId);
			}
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		synchronized (STATE) {
			if (running == this) {
				running = null;
			}
		}
		super.onDestroy();
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}

	private void showNotification() {
		final NotificationManager manager = getSystemService(NotificationManager.class);
		manager.createNotificationChannel(
				new NotificationChannel(CHANNEL, getString(R.string.lan_channel_name), NotificationManager.IMPORTANCE_LOW));
		// The launch intent of the app brings its task back to the front, whatever page it was left on.
		final Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
		final PendingIntent tap = launch == null ? null
				: PendingIntent.getActivity(this, 0, launch,
						PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		final Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_lan)
				.setContentTitle(getString(R.string.lan_notification_title))
				.setContentText(getString(R.string.lan_notification_text)).setContentIntent(tap).setOngoing(true)
				.setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).build();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		}
		else {
			startForeground(NOTIFICATION_ID, notification);
		}
	}
}
