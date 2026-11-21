package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

final class CUnitAggroState {
	int nextCallForHelpTick;
	CUnit pendingAttackBackSource;
	CUnit pendingSpellAnswerSource;
	CUnit spellMissileSource;
	boolean pendingAttackBackWasHit;
	boolean pendingAttackBackHelp;
	// The current fight began as an answer to a call for help, not as the unit's own acquisition.
	boolean fightFromHelpCall;
	boolean pendingAttackBackFromArea;
	int fleeMoveResetCount = -1;
	CUnit.CreepCamp creepCamp;
	boolean guarding;
	boolean guardPositionRemoved;
	float guardX;
	float guardY;
	int lastCombatTick;
	int lastHurtTick = Integer.MIN_VALUE / 2;
	CUnit hitCastSource;
	int hitCastTick = Integer.MIN_VALUE / 2;
	int guardReturn = CUnit.GUARD_RETURN_NONE;
	boolean guardEngaged;
	int guardAwayTick = -1;
	boolean wanderer;
	boolean postSet;
	boolean postFight;
	boolean postFightFromArea;
}
