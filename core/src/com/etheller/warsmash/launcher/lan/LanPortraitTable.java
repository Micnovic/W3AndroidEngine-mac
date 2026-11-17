package com.etheller.warsmash.launcher.lan;

import java.util.HashMap;
import java.util.Map;

/**
 * Which Reforged profile portrait shows the same character as which classic command button, so that players of the
 * two skins see each other's portrait as the art of their own skin. A pair is the Reforged number (from 1, as in
 * {@code r:17}) and the classic button's name (as in {@code c:BTNArthas}); a portrait without a pair stays a colored
 * initial on the other skin. Pairs are confirmed against the two skins' pictures, never assumed: the stylised Reforged portraits (p068 on)
 * repeat the classic button art, so those were matched by picture similarity and checked by eye; the realistic hero
 * busts before them have no classic twin and stay unpaired, except Arthas.
 */
final class LanPortraitTable {
	/** {@code "r:<number>", "<button name>"}, one pair after another. */
	private static final String[] PAIRS = {
		"r:7", "BTNArthas", "r:68", "BTNPeon", "r:69", "BTNFootman", "r:70", "BTNKnight",
		"r:71", "BTNHeroArchMage", "r:72", "BTNMedivh", "r:73", "BTNGrunt", "r:74", "BTNTauren",
		"r:75", "BTNHeroFarSeer", "r:76", "BTNThrall", "r:77", "BTNArcher", "r:78", "BTNDruidOfTheClaw",
		"r:79", "BTNPriestessOfTheMoon", "r:80", "BTNFurion", "r:81", "BTNGhoul", "r:82", "BTNAbomination",
		"r:83", "BTNHeroLich", "r:84", "BTNTichondrius", "r:89", "BTNRifleman", "r:90", "BTNSorceress",
		"r:91", "BTNSpellBreaker", "r:92", "BTNHeroBloodElfPrince", "r:93", "BTNJaina", "r:94", "BTNHeadhunter",
		"r:95", "BTNShaman", "r:96", "BTNSpiritWalker", "r:97", "BTNShadowHunter", "r:98", "BTNRexxar",
		"r:99", "BTNHuntress", "r:100", "BTNDruidOfTheTalon", "r:101", "BTNDryad", "r:102", "BTNKeeperOfTheGrove",
		"r:103", "BTNWarden2", "r:104", "BTNCryptFiend", "r:105", "BTNBanshee", "r:106", "BTNDestroyer",
		"r:107", "BTNHeroCryptLord", "r:108", "BTNBansheeRanger", "r:109", "BTNNagaMyrmidon",
		"r:112", "BTNNagaSeaWitch", "r:113", "BTNEvilIllidan", "r:116", "BTNFelguard", "r:117", "BTNInfernal",
		"r:118", "BTNDoomGuard", "r:119", "BTNPitLord", "r:120", "BTNArchimonde" };

	private static final Map<Integer, String> CLASSIC_FOR_REFORGED = new HashMap<>();
	private static final Map<String, Integer> REFORGED_FOR_CLASSIC = new HashMap<>();

	static {
		add(PAIRS);
	}

	private LanPortraitTable() {
	}

	private static void add(final String[] pairs) {
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			final int number = Integer.parseInt(pairs[i].substring(2));
			CLASSIC_FOR_REFORGED.putIfAbsent(number, pairs[i + 1]);
			REFORGED_FOR_CLASSIC.putIfAbsent(pairs[i + 1], number);
		}
	}

	static String[] pairs() {
		return PAIRS.clone();
	}

	/** The classic button that shows the Reforged portrait, or null. */
	static String classicFor(final int reforgedNumber) {
		return CLASSIC_FOR_REFORGED.get(reforgedNumber);
	}

	/** The Reforged number (from 1) of the portrait that shows the classic button, or 0. */
	static int reforgedFor(final String classicKey) {
		final Integer number = REFORGED_FOR_CLASSIC.get(classicKey);
		return number == null ? 0 : number;
	}
}
