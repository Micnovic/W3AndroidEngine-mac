package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;

/**
 * The simulation's text tag while the drawn one is made on the render thread. It is a {@link TextTag} itself, so the
 * simulation keeps reading and counting its own copy (handle id, receiving player, game age); every change is also
 * recorded as an event naming {@link #getProxyId()}, which the {@link RenderEventPlayer} applies to the real tag.
 */
public final class ProxyTextTag extends TextTag {
	public static final int FIELD_TEXT = 0;
	public static final int FIELD_POSITION = 1;
	public static final int FIELD_COLOR = 2;
	public static final int FIELD_VELOCITY = 3;
	public static final int FIELD_VISIBLE = 4;
	public static final int FIELD_PERMANENT = 5;
	public static final int FIELD_CENTERED = 6;
	public static final int FIELD_CENTERED_ON_POINT = 7;
	public static final int FIELD_RECEIVING_PLAYER = 8;
	public static final int FIELD_FADE_START = 9;
	public static final int FIELD_FONT_HEIGHT = 10;
	public static final int FIELD_LIFETIME = 11;
	public static final int FIELD_LIFETIME_DURATION = 12;
	public static final int FIELD_SUSPENDED = 13;
	public static final int FIELD_HANDLE_ID = 14;

	private final BufferingRenderController controller;
	private final int proxyId;
	/** False while the constructor runs, so setting up the proxy records nothing. */
	private boolean live;

	ProxyTextTag(final BufferingRenderController controller, final int proxyId, final float lifetimeDuration,
			final float fontHeight, final boolean permanent) {
		super(new Vector3(), new Vector2(), "", Color.WHITE, lifetimeDuration, 0, fontHeight, -1);
		this.controller = controller;
		this.proxyId = proxyId;
		super.setPermanent(permanent);
		this.live = true;
	}

	public int getProxyId() {
		return this.proxyId;
	}

	private RenderEvent set(final int field) {
		final RenderEvent event = this.controller.begin(RenderCallCodes.TEXT_TAG_SET);
		event.a = this.proxyId;
		event.b = field;
		return event;
	}

	@Override
	public void setText(final String text) {
		super.setText(text);
		if (this.live) {
			set(FIELD_TEXT).o0 = text;
			this.controller.emitPending();
		}
	}

	@Override
	public void setFadeStart(final float fadeStart) {
		super.setFadeStart(fadeStart);
		if (this.live) {
			set(FIELD_FADE_START).f0 = fadeStart;
			this.controller.emitPending();
		}
	}

	@Override
	public void setFontHeight(final float fontHeight) {
		super.setFontHeight(fontHeight);
		if (this.live) {
			set(FIELD_FONT_HEIGHT).f0 = fontHeight;
			this.controller.emitPending();
		}
	}

	@Override
	public void setLifetime(final float lifetime) {
		super.setLifetime(lifetime);
		if (this.live) {
			set(FIELD_LIFETIME).f0 = lifetime;
			this.controller.emitPending();
		}
	}

	@Override
	public void setLifetimeDuration(final float lifetimeDuration) {
		super.setLifetimeDuration(lifetimeDuration);
		if (this.live) {
			set(FIELD_LIFETIME_DURATION).f0 = lifetimeDuration;
			this.controller.emitPending();
		}
	}

	@Override
	public void setSuspended(final boolean suspended) {
		super.setSuspended(suspended);
		if (this.live) {
			set(FIELD_SUSPENDED).c = suspended ? 1 : 0;
			this.controller.emitPending();
		}
	}

	@Override
	public void setHandleId(final int handleId) {
		super.setHandleId(handleId);
		if (this.live) {
			set(FIELD_HANDLE_ID).c = handleId;
			this.controller.emitPending();
		}
	}

	@Override
	public void setPosition(final float x, final float y, final float z) {
		super.setPosition(x, y, z);
		if (this.live) {
			final RenderEvent event = set(FIELD_POSITION);
			event.f0 = x;
			event.f1 = y;
			event.f2 = z;
			this.controller.emitPending();
		}
	}

	@Override
	public void setColor(final int r, final int g, final int b, final float alpha) {
		super.setColor(r, g, b, alpha);
		if (this.live) {
			final RenderEvent event = set(FIELD_COLOR);
			event.f0 = r;
			event.f1 = g;
			event.f2 = b;
			event.f3 = alpha;
			this.controller.emitPending();
		}
	}

	@Override
	public void setVelocity(final float vx, final float vy) {
		super.setVelocity(vx, vy);
		if (this.live) {
			final RenderEvent event = set(FIELD_VELOCITY);
			event.f0 = vx;
			event.f1 = vy;
			this.controller.emitPending();
		}
	}

	@Override
	public void setVisible(final boolean visible) {
		super.setVisible(visible);
		if (this.live) {
			set(FIELD_VISIBLE).c = visible ? 1 : 0;
			this.controller.emitPending();
		}
	}

	@Override
	public void setPermanent(final boolean permanent) {
		super.setPermanent(permanent);
		if (this.live) {
			set(FIELD_PERMANENT).c = permanent ? 1 : 0;
			this.controller.emitPending();
		}
	}

	@Override
	public void setCentered(final boolean centered) {
		super.setCentered(centered);
		if (this.live) {
			set(FIELD_CENTERED).c = centered ? 1 : 0;
			this.controller.emitPending();
		}
	}

	@Override
	public void setCenteredOnPoint(final boolean centeredOnPoint) {
		super.setCenteredOnPoint(centeredOnPoint);
		if (this.live) {
			set(FIELD_CENTERED_ON_POINT).c = centeredOnPoint ? 1 : 0;
			this.controller.emitPending();
		}
	}

	@Override
	public void setReceivingPlayer(final int receivingPlayer) {
		super.setReceivingPlayer(receivingPlayer);
		if (this.live) {
			set(FIELD_RECEIVING_PLAYER).c = receivingPlayer;
			this.controller.emitPending();
		}
	}
}
