package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

/**
 * The command card of the local player's active unit group: up to {@link #SLOTS} buttons, as computed by the simulation
 * at the end of a step. Writer: the simulation thread while the frame is built. Reader: the render thread, which
 * draws the card and answers hotkeys from it; the final check of a clicked order stays in the simulation (a refusal
 * comes back as an event).
 */
public final class CommandCardModel {
	public static final int COLUMNS = 4;
	public static final int ROWS = 3;
	public static final int SLOTS = COLUMNS * ROWS;

	/** Bumped by the simulation whenever the card's layout changes, so the interface can skip rebuilding. */
	public int layoutVersion;
	/** Handle of the unit whose card this is (the active unit of the selection), 0 for none. */
	public int unitHandle;
	/** Number of used entries in {@link #buttons}. */
	public int buttonCount;
	public final CommandButtonModel[] buttons = new CommandButtonModel[SLOTS];
	/** Skill points of the card's unit when it is a hero, else 0. */
	public int heroSkillPoints;
	/** Handle of the card unit's hero ability, 0 when it has none; the buttons of that ability are the hero's own. */
	public int heroAbilityHandle;
	/** Raw codes of the skills the hero may still learn, for a script's learn-skill button effect. */
	public int[] heroSkillIds = new int[8];
	public int heroSkillCount;

	public CommandCardModel() {
		for (int i = 0; i < SLOTS; i++) {
			buttons[i] = new CommandButtonModel();
		}
	}

	public void clear() {
		unitHandle = 0;
		buttonCount = 0;
		heroSkillPoints = 0;
		heroAbilityHandle = 0;
		heroSkillCount = 0;
		for (final CommandButtonModel button : buttons) {
			button.clear();
		}
	}

	/** Next free button, cleared; the simulation fills it. Null when the card is full. */
	public CommandButtonModel nextButton() {
		return buttonCount < SLOTS ? buttons[buttonCount++] : null;
	}

	public void addHeroSkill(final int skillId) {
		if (heroSkillCount == heroSkillIds.length) {
			heroSkillIds = java.util.Arrays.copyOf(heroSkillIds, heroSkillCount * 2);
		}
		heroSkillIds[heroSkillCount++] = skillId;
	}

	public void copyFrom(final CommandCardModel other) {
		layoutVersion = other.layoutVersion;
		unitHandle = other.unitHandle;
		buttonCount = other.buttonCount;
		heroSkillPoints = other.heroSkillPoints;
		heroAbilityHandle = other.heroAbilityHandle;
		heroSkillCount = 0;
		for (int i = 0; i < other.heroSkillCount; i++) {
			addHeroSkill(other.heroSkillIds[i]);
		}
		for (int i = 0; i < SLOTS; i++) {
			buttons[i].copyFrom(other.buttons[i]);
		}
	}
}
