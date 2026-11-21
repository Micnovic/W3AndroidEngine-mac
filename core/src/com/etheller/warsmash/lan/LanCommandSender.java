package com.etheller.warsmash.lan;

import java.util.function.Consumer;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * What the local player's interface gives a LAN game: nothing reaches the game from here. Each input becomes a
 * numbered command for the host, and comes back, with everyone else's, in a turn.
 */
public final class LanCommandSender implements CPlayerUnitOrderListener {
	private final Consumer<byte[]> output;
	private final LanClient client;
	private final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();
	private final boolean watcher;
	private final boolean referee;

	/**
	 * @param watcher the player has no slot: the host takes no commands from it, but the pause of a referee
	 */
	public LanCommandSender(final LanClient client, final boolean watcher, final boolean referee) {
		this.client = client;
		this.output = client::sendCommand;
		this.watcher = watcher;
		this.referee = referee;
	}

	/** A sender whose commands go to the given output, for a test. */
	public LanCommandSender(final Consumer<byte[]> output) {
		this.client = null;
		this.output = output;
		this.watcher = false;
		this.referee = false;
	}

	private void send() {
		send(false);
	}

	private void send(final boolean pause) {
		final byte[] command = this.encoder.take();
		// What the player does while the game is still being played through to the present is about a past view.
		if ((!this.watcher || (pause && this.referee)) && (this.client == null || !this.client.isCatchingUp())) {
			this.output.accept(command);
		}
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		this.encoder.issueTargetOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, queue);
		send();
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
		this.encoder.issuePointOrder(unitHandleId, abilityHandleId, orderId, x, y, queue);
		send();
	}

	@Override
	public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
		this.encoder.issueGroupPointOrder(unitHandleIds, abilityHandleIds, orderId, xs, ys, queue);
		send();
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
		this.encoder.issueDropItemAtPointOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, x, y, queue);
		send();
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		this.encoder.issueDropItemAtTargetOrder(unitHandleId, abilityHandleId, orderId, targetItemHandleId,
				targetHeroHandleId, queue);
		send();
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
		this.encoder.issueImmediateOrder(unitHandleId, abilityHandleId, orderId, queue);
		send();
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		this.encoder.unitCancelTrainingItem(unitHandleId, cancelIndex);
		send();
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
		this.encoder.issueGuiPlayerEvent(eventId);
		send();
	}

	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.encoder.selectUnits(deselectedUnitHandleIds, selectedUnitHandleIds);
		send();
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		// The host refuses a longer line, and counts it against the player as an invalid command.
		final String line = clip(message, LanProtocol.MAX_CHAT);
		if (this.watcher) {
			// A watcher has no slot to put a command under: its line goes to the host as chat, which routes it.
			if (this.client != null) {
				this.client.chat(recipient == 0 && this.referee ? LanProtocol.CHAT_ALL : LanProtocol.CHAT_OBSERVERS,
						line);
			}
			return;
		}
		this.encoder.chatMessage(recipient, recipientPlayerIndex, line);
		send();
	}

	static String clip(final String text, final int maxChars) {
		if ((text == null) || (text.length() <= maxChars)) {
			return text;
		}
		final int end = Character.isHighSurrogate(text.charAt(maxChars - 1)) ? maxChars - 1 : maxChars;
		return text.substring(0, end);
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.encoder.clickDialogButton(dialogHandleId, buttonHandleId);
		send();
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		this.encoder.setAlliance(otherPlayerIndex, allianceType, value);
		send();
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		this.encoder.transferResources(otherPlayerIndex, gold, lumber);
		send();
	}

	@Override
	public void fireGameEvent(final int eventId) {
		this.encoder.fireGameEvent(eventId);
		send();
	}

	/** A shared game has no save of one player's making. */
	@Override
	public void saveGame(final String saveName) {
	}

	/** Leaving is the client's own message to the host, which puts it in a turn. */
	@Override
	public void leaveGame() {
		if (this.client != null) {
			this.client.leave(net.warsmash.lan.protocol.LanProtocol.LEAVE_QUIT);
		}
	}

	@Override
	public void cheat(final String cheat) {
	}

	/** The pause travels as a command; the host then sends turns without steps until a command ends it. */
	@Override
	public void setGamePaused(final boolean paused) {
		this.encoder.setGamePaused(paused);
		send(true);
	}

	@Override
	public void minimapSignal(final float x, final float y) {
		this.encoder.minimapSignal(x, y);
		send();
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
		this.encoder.trackableEvent(trackableHandleId, hit);
		send();
	}

	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
		this.encoder.mouseEvent(eventId, x, y, button);
		send();
	}

	@Override
	public void syncData(final String prefix, final String data) {
		this.encoder.syncData(prefix, data);
		send();
	}

	@Override
	public void keyEvent(final int key, final int metaKey, final boolean down) {
		this.encoder.keyEvent(key, metaKey, down);
		send();
	}

	@Override
	public void frameEvent(final int frameId, final int eventType, final float value, final String text) {
		this.encoder.frameEvent(frameId, eventType, value, text);
		send();
	}
}
