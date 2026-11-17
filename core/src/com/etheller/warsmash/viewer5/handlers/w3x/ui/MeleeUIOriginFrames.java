package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;

/**
 * The frames of the game's interface under the ORIGIN_FRAME_* numbers of Reforged, which a map script hides, moves and
 * resizes through the Frame API. The minimap and the portrait are not frames here (a texture area and a model scene),
 * so each has a place holder frame that the script moves and the minimap and the portrait follow.
 */
final class MeleeUIOriginFrames {
	static final int GAME_UI = 0;
	static final int COMMAND_BUTTON = 1;
	static final int HERO_BAR = 2;
	static final int HERO_BUTTON = 3;
	static final int HERO_HP_BAR = 4;
	static final int HERO_MANA_BAR = 5;
	static final int HERO_BUTTON_INDICATOR = 6;
	static final int ITEM_BUTTON = 7;
	static final int MINIMAP = 8;
	static final int MINIMAP_BUTTON = 9;
	static final int SYSTEM_BUTTON = 10;
	static final int TOOLTIP = 11;
	static final int UBERTOOLTIP = 12;
	static final int CHAT_MSG = 13;
	static final int UNIT_MSG = 14;
	static final int TOP_MSG = 15;
	static final int PORTRAIT = 16;
	static final int PORTRAIT_HP_TEXT = 19;
	static final int PORTRAIT_MANA_TEXT = 20;
	static final int UNIT_PANEL_BUFF_BAR = 21;
	private static final List<String> SYSTEM_BUTTONS = Collections.unmodifiableList(Arrays.asList(
			"UpperButtonBarQuestsButton", "UpperButtonBarMenuButton", "UpperButtonBarAlliesButton", "UpperButtonBarChatButton"));
	private static final List<String> MINIMAP_BUTTONS = Collections.unmodifiableList(Arrays.asList(
			"MinimapSignalButton", "MiniMapTerrainButton", "MiniMapAllyButton", "MiniMapCreepButton", "MinimapFormationButton"));

	private final MeleeUI ui;
	private SimpleFrame minimapProxy;
	private SimpleFrame portraitProxy;
	private final Rectangle portraitFollowed = new Rectangle();
	private boolean portraitFollowedVisible = true;
	private final Rectangle tempBounds = new Rectangle();

	MeleeUIOriginFrames(final MeleeUI ui) {
		this.ui = ui;
	}

	UIFrame get(final int type, final int index) {
		if (this.ui.rootFrame == null) {
			return null;
		}
		switch (type) {
		case GAME_UI:
			return this.ui.rootFrame;
		case COMMAND_BUTTON:
			return (index >= 0) && (index < (MeleeUI.COMMAND_CARD_WIDTH * MeleeUI.COMMAND_CARD_HEIGHT))
					? this.ui.commandCard[index / MeleeUI.COMMAND_CARD_WIDTH][index % MeleeUI.COMMAND_CARD_WIDTH]
					: null;
		case HERO_BAR:
			return this.ui.heroBar;
		case HERO_BUTTON:
			return this.ui.groups.getHeroBarIcon(index);
		case HERO_HP_BAR: {
			final HeroBarIcon icon = this.ui.groups.getHeroBarIcon(index);
			return icon == null ? null : icon.getHpBarFrame();
		}
		case HERO_MANA_BAR: {
			final HeroBarIcon icon = this.ui.groups.getHeroBarIcon(index);
			return icon == null ? null : icon.getManaBarFrame();
		}
		case HERO_BUTTON_INDICATOR: {
			final HeroBarIcon icon = this.ui.groups.getHeroBarIcon(index);
			return icon == null ? null : icon.getPointsOverlay();
		}
		case ITEM_BUTTON: {
			final int width = this.ui.inventoryWidth;
			return (index >= 0) && (index < (width * MeleeUI.INVENTORY_HEIGHT))
					? this.ui.inventoryIcons[index / width][index % width]
					: null;
		}
		case MINIMAP:
			return minimapProxy();
		case MINIMAP_BUTTON:
			return (index >= 0) && (index < MINIMAP_BUTTONS.size())
					? this.ui.rootFrame.getFrameByName(MINIMAP_BUTTONS.get(index), 0)
					: null;
		case SYSTEM_BUTTON:
			return (index >= 0) && (index < SYSTEM_BUTTONS.size())
					? this.ui.rootFrame.getFrameByName(SYSTEM_BUTTONS.get(index), 0)
					: null;
		// The tooltip of the interface is one box holding the name, the text and the costs.
		case TOOLTIP:
		case UBERTOOLTIP:
			return this.ui.tooltipFrame;
		case CHAT_MSG:
			return this.ui.messages.messageProxy(true);
		case UNIT_MSG:
			return this.ui.messages.messageProxy(false);
		case TOP_MSG:
			return this.ui.messages.getErrorMessageFrame();
		case PORTRAIT:
			return portraitProxy();
		case PORTRAIT_HP_TEXT:
			return this.ui.unitLifeText;
		case PORTRAIT_MANA_TEXT:
			return this.ui.unitManaText;
		case UNIT_PANEL_BUFF_BAR:
			return this.ui.buffBarIcons[0] == null ? null : this.ui.buffBarIcons[0].getParent();
		default:
			return null;
		}
	}

	private SimpleFrame placeHolder(final String name, final Rectangle area) {
		final SimpleFrame proxy = new SimpleFrame(name, this.ui.rootFrame);
		proxy.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.ui.rootFrame, FramePoint.BOTTOMLEFT,
				area.x - this.ui.rootFrame.getFramePointX(FramePoint.LEFT),
				area.y - this.ui.rootFrame.getFramePointY(FramePoint.BOTTOM)));
		proxy.setWidth(area.width);
		proxy.setHeight(area.height);
		this.ui.rootFrame.add(proxy);
		proxy.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		return proxy;
	}

	private UIFrame minimapProxy() {
		if ((this.minimapProxy == null) && (this.ui.meleeUIMinimap != null)) {
			this.minimapProxy = placeHolder("MinimapOrigin", this.ui.meleeUIMinimap.getDisplayArea(this.tempBounds));
		}
		return this.minimapProxy;
	}

	private UIFrame portraitProxy() {
		if (this.portraitProxy == null) {
			this.portraitProxy = placeHolder("PortraitOrigin",
					this.ui.infoPanel.defaultPortraitBounds(this.tempBounds));
			this.portraitFollowed.set(this.portraitProxy.getRenderBounds());
		}
		return this.portraitProxy;
	}

	/** Where a map script put the portrait, or null while it keeps its place. */
	Rectangle scriptPortraitBounds() {
		return this.portraitProxy == null ? null : this.portraitProxy.getRenderBounds();
	}

	boolean isPortraitScriptVisible() {
		return (this.portraitProxy == null) || this.portraitProxy.isVisible();
	}

	/** Every frame: the minimap and the portrait go where their place holders are. */
	void update() {
		if (this.minimapProxy != null) {
			this.minimapProxy.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
			final Rectangle bounds = this.minimapProxy.getRenderBounds();
			this.ui.meleeUIMinimap.setDisplayArea(bounds.x, bounds.y, bounds.width, bounds.height);
			this.ui.meleeUIMinimap.setScriptVisible(this.minimapProxy.isVisible());
		}
		if (this.portraitProxy != null) {
			this.portraitProxy.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
			final Rectangle bounds = this.portraitProxy.getRenderBounds();
			final boolean visible = this.portraitProxy.isVisible();
			if (!bounds.equals(this.portraitFollowed) || (visible != this.portraitFollowedVisible)) {
				this.portraitFollowed.set(bounds);
				this.portraitFollowedVisible = visible;
				this.ui.positionPortrait();
			}
		}
	}

	/** BlzHideOriginFrames hides the place holders too, so they are made first. */
	void makePlaceHolders() {
		if (this.ui.rootFrame != null) {
			minimapProxy();
			portraitProxy();
		}
	}
}
