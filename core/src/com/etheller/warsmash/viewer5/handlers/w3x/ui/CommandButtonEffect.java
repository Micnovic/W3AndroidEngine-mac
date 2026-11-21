package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * A glow a map script put on a button of the command card (CreateCommandButtonEffect and its upgrade and learn
 * forms), for tutorials that point at the button to press. It only decides which buttons shine.
 */
public final class CommandButtonEffect {
	public enum Kind {
		ABILITY, UPGRADE, LEARN
	}

	/** Orders of units, buildings and upgrades are their four-letter code; the plain orders are far below this. */
	private static final int RAW_CODE_MIN = 0x01000000;

	private final Kind kind;
	private final int id;
	private final int orderId;

	private CommandButtonEffect(final Kind kind, final int id, final int orderId) {
		this.kind = kind;
		this.id = id;
		this.orderId = orderId;
	}

	/** The button of an ability and its order; an order of 0 matches every button of the ability. */
	public static CommandButtonEffect ability(final int abilityId, final int orderId) {
		return new CommandButtonEffect(Kind.ABILITY, abilityId, orderId);
	}

	public static CommandButtonEffect upgrade(final int upgradeId) {
		return new CommandButtonEffect(Kind.UPGRADE, upgradeId, 0);
	}

	public static CommandButtonEffect learn(final int abilityId) {
		return new CommandButtonEffect(Kind.LEARN, abilityId, 0);
	}

	public Kind getKind() {
		return this.kind;
	}

	public int getId() {
		return this.id;
	}

	/**
	 * Whether the button that starts {@code buttonOrderId} of the ability {@code buttonAlias} is the one this effect
	 * points at. Training and building name the unit as the order, which is the same button whatever ability of the
	 * race's own is behind it.
	 */
	public boolean matches(final int buttonAlias, final int buttonOrderId) {
		switch (this.kind) {
		case ABILITY:
			if (this.orderId == 0) {
				return buttonAlias == this.id;
			}
			if (this.orderId >= RAW_CODE_MIN) {
				return buttonOrderId == this.orderId;
			}
			return (buttonAlias == this.id) && (buttonOrderId == this.orderId);
		case UPGRADE:
		case LEARN:
			return (buttonOrderId != 0) && (buttonOrderId == this.id);
		default:
			return false;
		}
	}
}
