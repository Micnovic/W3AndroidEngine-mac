package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListenerDelaying;

class ThreadedOrderListenerTest {
	/** One call of every command the codec knows, in the order of its type numbers. */
	private static void everyCommand(final CPlayerUnitOrderListener l) {
		l.issueTargetOrder(1, 2, 3, 4, true);
		l.issuePointOrder(5, 6, 7, 1.5f, -2.25f, false);
		l.issueGroupPointOrder(new int[] { 8, 9 }, new int[] { 10, 11 }, 12, new float[] { 1f, 2f },
				new float[] { 3f, 4f }, true);
		l.issueDropItemAtPointOrder(13, 14, 15, 16, 5.5f, 6.5f, false);
		l.issueDropItemAtTargetOrder(17, 18, 19, 20, 21, true);
		l.issueImmediateOrder(22, 23, 24, false);
		l.unitCancelTrainingItem(25, 3);
		l.issueGuiPlayerEvent(26);
		l.selectUnits(new int[] { 27 }, new int[] { 28, 29, 30 });
		l.chatMessage(1, 2, "hello čš");
		l.clickDialogButton(31, 32);
		l.setAlliance(3, 4, true);
		l.transferResources(2, 100, 50);
		l.fireGameEvent(33);
		l.saveGame("save name");
		l.leaveGame();
		l.cheat("whosyourdaddy");
		l.setGamePaused(true);
		l.minimapSignal(7f, 8f);
		l.trackableEvent(34, true);
		l.mouseEvent(35, 9f, 10f, 2);
		l.syncData("prefix", "data");
		l.keyEvent(36, 3, false);
		l.frameEvent(37, 38, 0.5f, "text");
	}

	private static final int COMMAND_TYPES = CPlayerCommandCodec.FRAME_EVENT;

	@Test
	void everyCommandSurvivesEncodeInboxDrainDecode() {
		final CPlayerCommandCodec.Encoder direct = new CPlayerCommandCodec.Encoder();
		everyCommand(direct);
		final byte[] expected = direct.take();

		final OrderInbox inbox = new OrderInbox();
		final ThreadedOrderListener threaded = new ThreadedOrderListener(inbox, 3);
		everyCommand(threaded);
		assertEquals(COMMAND_TYPES, inbox.pendingCount());

		final CPlayerCommandCodec.Encoder target = new CPlayerCommandCodec.Encoder();
		assertEquals(COMMAND_TYPES, threaded.drain(77, target));
		assertArrayEquals(expected, target.take());
		assertEquals(0, inbox.pendingCount());
	}

	@Test
	void commandsKeepTheirOrderAndGetTheDrainTick() {
		final OrderInbox inbox = new OrderInbox();
		final ThreadedOrderListener threaded = new ThreadedOrderListener(inbox, 0);
		threaded.issueGuiPlayerEvent(1);
		threaded.issueGuiPlayerEvent(2);
		final List<Integer> ids = new ArrayList<>();
		final List<Integer> ticks = new ArrayList<>();
		inbox.drain(12, message -> {
			ticks.add(message.appliedTick);
			CPlayerCommandCodec.decode(message.payload, 0, message.payloadLength,
					orderIdListener(ids));
		});
		assertEquals(List.of(1, 2), ids);
		assertEquals(List.of(12, 12), ticks);
	}

	@Test
	void theDelayingListenerKeepsItsRulesBehindTheDrain() {
		final List<String> calls = new ArrayList<>();
		final CPlayerUnitOrderListener recorder = (CPlayerUnitOrderListener) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { CPlayerUnitOrderListener.class },
				(proxy, method, args) -> {
					calls.add(method.getName());
					return null;
				});
		final CPlayerUnitOrderListenerDelaying delaying = new CPlayerUnitOrderListenerDelaying(recorder);
		final OrderInbox inbox = new OrderInbox();
		final ThreadedOrderListener threaded = new ThreadedOrderListener(inbox, 0);

		threaded.issueImmediateOrder(1, 2, 3, false);
		threaded.chatMessage(0, 0, "x");
		threaded.setGamePaused(true);
		threaded.drain(5, delaying);
		// The order waits for the end of the step; chat and pause act at once, as with a direct call.
		assertEquals(List.of("chatMessage", "setGamePaused"), calls);
		delaying.publishDelayedActions();
		assertEquals(List.of("chatMessage", "setGamePaused", "issueImmediateOrder"), calls);
	}

	@Test
	void messagesOfOtherKindsNeedAHandler() {
		final OrderInbox inbox = new OrderInbox();
		final ThreadedOrderListener threaded = new ThreadedOrderListener(inbox, 0);
		final OrderMessage message = inbox.obtain();
		message.kind = OrderMessage.PAUSE;
		inbox.submit(message);
		assertThrows(IllegalStateException.class, () -> threaded.drain(1, orderIdListener(new ArrayList<>())));

		final OrderMessage again = inbox.obtain();
		again.kind = OrderMessage.SPEED;
		inbox.submit(again);
		final List<Integer> kinds = new ArrayList<>();
		threaded.drain(2, orderIdListener(new ArrayList<>()), m -> kinds.add(m.kind));
		assertEquals(List.of(OrderMessage.SPEED), kinds);
	}

	@Test
	void anotherThreadCanSubmit() throws InterruptedException {
		final OrderInbox inbox = new OrderInbox();
		final ThreadedOrderListener threaded = new ThreadedOrderListener(inbox, 0);
		final Thread producer = new Thread(() -> {
			for (int i = 0; i < 1000; i++) {
				threaded.issueGuiPlayerEvent(i);
			}
		});
		producer.start();
		producer.join();
		final List<Integer> ids = new ArrayList<>();
		assertEquals(1000, threaded.drain(1, orderIdListener(ids)));
		for (int i = 0; i < 1000; i++) {
			assertEquals(i, ids.get(i));
		}
	}

	private static CPlayerUnitOrderListener orderIdListener(final List<Integer> ids) {
		return (CPlayerUnitOrderListener) java.lang.reflect.Proxy.newProxyInstance(
				ThreadedOrderListenerTest.class.getClassLoader(), new Class<?>[] { CPlayerUnitOrderListener.class },
				(proxy, method, args) -> {
					if ("issueGuiPlayerEvent".equals(method.getName())) {
						ids.add((Integer) args[0]);
					}
					return null;
				});
	}
}
