package com.etheller.warsmash.lan;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * The host's check of a player's command before it is put in a turn: it must decode completely, hold no coordinate
 * that is not a number or is infinite, and be none of the commands a shared game does not take from a player (cheats,
 * saving). Ownership of the units is the executor's check, on every machine alike.
 */
public final class LanCommandValidator implements LanHost.CommandValidator {
	/** BlzSendSyncData takes no longer prefix or data; a command carrying more came from no engine. */
	private static final int MAX_SYNC_BYTES = 255;
	/** Orders for more units than a selection can hold are no player's. */
	private static final int MAX_UNITS = 1024;

	@Override
	public boolean accept(final int slot, final byte[] command) {
		if (command.length == 0) {
			return false;
		}
		final Check check = new Check(slot >= LanProtocol.WATCHER_SLOT_BASE);
		try {
			CPlayerCommandCodec.decode(command, check);
		}
		catch (final IllegalArgumentException | NegativeArraySizeException e) {
			return false;
		}
		return check.valid;
	}

	private static final class Check implements CPlayerUnitOrderListener {
		boolean valid = true;
		/** A referee gives the pause and nothing else: it plays nothing. */
		private final boolean referee;

		Check(final boolean referee) {
			this.referee = referee;
		}

		private void notForReferee() {
			if (this.referee) {
				this.valid = false;
			}
		}

		private void number(final float value) {
			if (Float.isNaN(value) || Float.isInfinite(value)) {
				this.valid = false;
			}
		}

		private void units(final int count) {
			if (count > MAX_UNITS) {
				this.valid = false;
			}
		}

		@Override
		public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final int targetHandleId, final boolean queue) {
			notForReferee();
		}

		@Override
		public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final float x, final float y, final boolean queue) {
			notForReferee();
			number(x);
			number(y);
		}

		@Override
		public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
				final float[] xs, final float[] ys, final boolean queue) {
			notForReferee();
			units(unitHandleIds.length);
			for (final float x : xs) {
				number(x);
			}
			for (final float y : ys) {
				number(y);
			}
		}

		@Override
		public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final int targetHandleId, final float x, final float y, final boolean queue) {
			notForReferee();
			number(x);
			number(y);
		}

		@Override
		public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
			notForReferee();
		}

		@Override
		public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
				final boolean queue) {
			notForReferee();
		}

		@Override
		public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
			notForReferee();
		}

		@Override
		public void issueGuiPlayerEvent(final int eventId) {
			notForReferee();
		}

		@Override
		public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
			notForReferee();
			units(deselectedUnitHandleIds.length);
			units(selectedUnitHandleIds.length);
		}

		@Override
		public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
			notForReferee();
			if ((recipient < 0) || (recipient > 3) || (message == null)
					|| (message.length() > LanProtocol.MAX_CHAT)) {
				this.valid = false;
			}
		}

		@Override
		public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
			notForReferee();
		}

		@Override
		public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
			notForReferee();
		}

		@Override
		public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
			notForReferee();
			if ((gold < 0) || (lumber < 0)) {
				this.valid = false;
			}
		}

		@Override
		public void fireGameEvent(final int eventId) {
			notForReferee();
		}

		@Override
		public void saveGame(final String saveName) {
			this.valid = false;
		}

		@Override
		public void leaveGame() {
			// Leaving travels as the client's own message, so the host knows who left.
			this.valid = false;
		}

		@Override
		public void cheat(final String cheat) {
			this.valid = false;
		}

		@Override
		public void setGamePaused(final boolean paused) {
		}

		@Override
		public void minimapSignal(final float x, final float y) {
			notForReferee();
			number(x);
			number(y);
		}

		@Override
		public void trackableEvent(final int trackableHandleId, final boolean hit) {
			notForReferee();
		}

		@Override
		public void mouseEvent(final int eventId, final float x, final float y, final int button) {
			notForReferee();
			number(x);
			number(y);
		}

		@Override
		public void syncData(final String prefix, final String data) {
			notForReferee();
			if ((prefix == null) || prefix.isEmpty() || (utf8Length(prefix) > MAX_SYNC_BYTES) || (data == null)
					|| (utf8Length(data) > MAX_SYNC_BYTES)) {
				this.valid = false;
			}
		}

		@Override
		public void keyEvent(final int key, final int metaKey, final boolean down) {
			notForReferee();
		}

		@Override
		public void frameEvent(final int frameId, final int eventType, final float value, final String text) {
			notForReferee();
			number(value);
			if ((frameId < 0) || (eventType < 0)) {
				this.valid = false;
			}
		}
	}

	private static int utf8Length(final String text) {
		return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}
}
