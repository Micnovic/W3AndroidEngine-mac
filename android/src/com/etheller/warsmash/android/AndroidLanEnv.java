package com.etheller.warsmash.android;

import java.io.File;
import java.util.concurrent.Executor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.lan.LanGameStart;
import com.etheller.warsmash.lan.LanSessions;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.lan.LanLauncherModel;
import com.etheller.warsmash.launcher.lan.LanPortraitId;

import net.warsmash.lan.FileRejoinStore;
import net.warsmash.lan.RejoinStore;
import net.warsmash.lan.maps.LanMapIndex;
import net.warsmash.lan.transport.LanNetwork;

/**
 * What the LAN pages get from this device. The model lives as long as the process, as the LAN session does, so the
 * launcher's activity can be made again while a lobby stands; the activity it works with is the one shown now.
 */
final class AndroidLanEnv implements LanLauncherModel.Env {
	private final Context context;
	private final Handler main = new Handler(Looper.getMainLooper());
	private LanMapIndex index;
	private volatile long dataChecksum;

	private RejoinStore rejoinStore;

	AndroidLanEnv(final Context context) {
		this.context = context.getApplicationContext();
	}

	void setDataChecksum(final long checksum) {
		this.dataChecksum = checksum;
	}

	@Override
	public LanNetwork network() {
		return LanSessions.network();
	}

	@Override
	public synchronized LanMapIndex mapIndex() {
		if (this.index == null) {
			final File maps = new File(this.context.getFilesDir(), "Maps");
			final File download = new File(maps, "Download");
			download.mkdirs();
			final LanMapIndex made = new LanMapIndex(new File(this.context.getCacheDir(), "lan-maps.txt"), download);
			made.addRoot(maps);
			// A download nobody continued within a week is left over; this is the one place that clears them.
			made.cleanStaleParts();
			this.index = made;
		}
		return this.index;
	}

	@Override
	public String playerName() {
		return GameCatalog.currentProfile(this.context);
	}

	@Override
	public String portrait() {
		return LanPortraitId.encode(new Profiles(this.context,
				this.context.getSharedPreferences(com.etheller.warsmash.launcher.LauncherKeys.PREFERENCES, Context.MODE_PRIVATE))
						.portrait(playerName()), reforgedSkin());
	}

	/** The skin the launcher shows, chosen as {@code LauncherActivity.chosenSkin} does. */
	@Override
	public boolean reforgedSkin() {
		final String skin = this.context.getSharedPreferences(com.etheller.warsmash.launcher.LauncherKeys.PREFERENCES,
				Context.MODE_PRIVATE).getString(com.etheller.warsmash.launcher.LauncherKeys.KEY_SKIN,
						SkinArt.CLASSIC_SKIN);
		return SkinArt.REFORGED_SKIN.equals(skin) && SkinCache.hasReforged(this.context);
	}

	@Override
	public int controlScheme() {
		return LanSessions.protocolScheme(new EngineSettings(this.context).controlScheme(ControlScheme.TOUCH));
	}

	@Override
	public String engineVersion() {
		return LanGameStart.localVersion();
	}

	@Override
	public String buildCommit() {
		return LanGameStart.localCommit();
	}

	@Override
	public boolean official() {
		return LanGameStart.localOfficial();
	}

	@Override
	public long dataChecksum() {
		return this.dataChecksum;
	}

	@Override
	public Executor ui() {
		return this.main::post;
	}

	@Override
	public void launch(final LaunchRequest request) {
		final LauncherActivity activity = LauncherActivity.current();
		if (activity != null) {
			activity.launchGame(request, LauncherActivity.PAGE_LAN);
		}
	}

	@Override
	public void mapIssue(final File map, final java.util.function.Consumer<MapIssue> done) {
		final LauncherActivity activity = LauncherActivity.current();
		if ((map == null) || (activity == null) || (activity.catalog == null)) {
			done.accept(MapIssue.NONE);
			return;
		}
		activity.catalog.mapIssue(map.getAbsolutePath(), done);
	}

	@Override
	public boolean hasMapNamed(final String name) {
		final LauncherActivity activity = LauncherActivity.current();
		return (activity == null) || activity.lanPage.hasMapNamed(name);
	}

	@Override
	public synchronized RejoinStore rejoinStore() {
		if (this.rejoinStore == null) {
			this.rejoinStore = new FileRejoinStore(new File(this.context.getFilesDir(), "Lan/Rejoin"));
			LanSessions.setRejoinStore(this.rejoinStore);
		}
		return this.rejoinStore;
	}

	@Override
	public boolean hostMigration() {
		return true;
	}
}
