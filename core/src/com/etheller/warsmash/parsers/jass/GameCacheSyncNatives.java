package com.etheller.warsmash.parsers.jass;

import java.util.List;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCacheSync;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;

/**
 * The sync natives of a script: SyncStored* carry this machine's game cache value to every machine as a player
 * command, which every machine, a replay and a saved game apply at the same step as any other command. A game with
 * one machine has nothing to carry the value to, so there they change nothing.
 *
 * The game is lockstep: every machine runs the same script on the same commands in the same step, and a selection is a
 * command too. So when SyncSelections or TriggerSyncStart / TriggerSyncReady run, every machine is already at the same
 * point and there is nothing to wait for; they return at once and never block the script thread.
 */
final class GameCacheSyncNatives {
	private final JassProgram program;
	private final War3MapViewer viewer;
	private final WarsmashUI ui;

	private GameCacheSyncNatives(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		this.program = program;
		this.viewer = viewer;
		this.ui = ui;
	}

	static void register(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		final GameCacheSyncNatives natives = new GameCacheSyncNatives(program, viewer, ui);
		natives.add("SyncStoredInteger", CGameCacheSync::integer);
		natives.add("SyncStoredReal", CGameCacheSync::real);
		natives.add("SyncStoredBoolean", CGameCacheSync::bool);
		natives.add("SyncStoredString", CGameCacheSync::string);
		natives.add("SyncStoredUnit", CGameCacheSync::unit);
		for (final String name : new String[] { "SyncSelections", "TriggerSyncStart", "TriggerSyncReady" }) {
			natives.program.getJassNativeManager().createNative(name, (arguments, globalScope, triggerScope) -> null);
		}
	}

	private interface Encoder {
		List<String> encode(CGameCache cache, String mission, String key);
	}

	private void add(final String name, final Encoder encoder) {
		final JassFunction function = (arguments, globalScope, triggerScope) -> {
			if (!this.viewer.isNetworkGame() || (this.ui == null)) {
				return null;
			}
			final CPlayerUnitOrderListener sink = this.ui.getUnitOrderListener();
			final JassValue cacheArgument = arguments.get(0);
			final CGameCache cache = cacheArgument == null ? null
					: cacheArgument.visit(ObjectJassValueVisitor.<CGameCache>getInstance());
			if ((sink != null) && (cache != null)) {
				for (final String chunk : encoder.encode(cache, string(arguments, 1), string(arguments, 2))) {
					sink.syncData(CGameCacheSync.PREFIX, chunk);
				}
			}
			return null;
		};
		this.program.getJassNativeManager().createNative(name, function);
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}
}
