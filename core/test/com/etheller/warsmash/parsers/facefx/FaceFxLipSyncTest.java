package com.etheller.warsmash.parsers.facefx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class FaceFxLipSyncTest {
	private static final String HUMAN01 = "war3.w3mod\\_locales\\enus.w3mod\\sound\\dialogue\\faceanimation\\human01\\facialanimation\\Speaker.animset_ingame";
	private static final String HUMAN02 = "war3.w3mod\\_hd.w3mod\\_locales\\enus.w3mod\\sound\\dialogue\\faceanimation\\human02\\facialanimation\\speaker.animset_ingame";

	private static final class CountingLoader implements FaceFxAnimIndex.Loader {
		final List<String> loaded = new ArrayList<>();
		private final String withLine;

		CountingLoader(final String withLine) {
			this.withLine = withLine;
		}

		@Override
		public byte[] load(final String path) throws IOException {
			this.loaded.add(path);
			return path.equals(this.withLine) ? FaceFxTest.animSet() : null;
		}
	}

	@Test
	void readsAListedPathByWhatFollowsItsPackages() {
		assertEquals("sound\\dialogue\\faceanimation\\human01\\facialanimation\\Speaker.animset_ingame",
				FaceFxAnimIndex.logicalPath(HUMAN01));
		assertEquals("sound\\dialogue\\faceanimation\\human02\\facialanimation\\speaker.animset_ingame",
				FaceFxAnimIndex.logicalPath(HUMAN02));
		assertEquals("facialanimation\\goblinsapper.animset_ingame",
				FaceFxAnimIndex.logicalPath("war3.w3mod/_de.w3mod/facialanimation/goblinsapper.animset_ingame"));
		assertEquals("units\\a.facefx_ingame", FaceFxAnimIndex.logicalPath("units/a.facefx_ingame"));
	}

	@Test
	void namesTheSpeakerOfALine() {
		assertEquals("footman", FaceFxAnimIndex.speakerOfLine("H03Footman47"));
		assertEquals("arthas", FaceFxAnimIndex.speakerOfLine("H01Arthas08"));
		assertEquals("arthas", FaceFxAnimIndex.speakerOfLine("H01Arthas08b"));
		assertNull(FaceFxAnimIndex.speakerOfLine("Line01"));
		assertNull(FaceFxAnimIndex.speakerOfLine(null));
	}

	@Test
	void findsTheChapterFolderOfAnAnimset() {
		assertEquals("human01", FaceFxAnimIndex.chapterOf(FaceFxAnimIndex.logicalPath(HUMAN01)));
		assertNull(FaceFxAnimIndex.chapterOf("facialanimation\\goblinsapper.animset_ingame"));
	}

	@Test
	void triesTheChapterOfTheSoundFirst() {
		final FaceFxAnimIndex index = new FaceFxAnimIndex(Arrays.asList(HUMAN01, HUMAN02, "units\\a.facefx_ingame"),
				new CountingLoader(null));
		final String human01 = FaceFxAnimIndex.logicalPath(HUMAN01);
		final String human02 = FaceFxAnimIndex.logicalPath(HUMAN02);
		assertEquals(Arrays.asList(human01, human02), index.candidates("speaker", null));
		assertEquals(Arrays.asList(human02, human01),
				index.candidates("SPEAKER", "Sound/Dialogue/Human02/Line01.flac"));
		assertEquals(0, index.candidates("nobody", null).size());
		assertEquals(0, index.candidates(null, null).size());
	}

	@Test
	void listsAnAnimsetOncePerLogicalPath() {
		final FaceFxAnimIndex index = new FaceFxAnimIndex(
				Arrays.asList(HUMAN02, HUMAN02.replace("_hd.w3mod", "_de.w3mod")), new CountingLoader(null));
		assertEquals(1, index.candidates("speaker", null).size());
	}

	@Test
	void findsTheAnimationOfALineInTheSpeakersAnimsets() {
		final CountingLoader loader = new CountingLoader(FaceFxAnimIndex.logicalPath(HUMAN02));
		final FaceFxAnimIndex index = new FaceFxAnimIndex(Arrays.asList(HUMAN01, HUMAN02), loader);
		final FaceFxAnimSet.Anim anim = index.find("speaker", "Sound\\Dialogue\\Human02\\Line01.flac");
		assertNotNull(anim);
		assertEquals("Line01", anim.name);
		assertEquals(Arrays.asList(FaceFxAnimIndex.logicalPath(HUMAN02)), loader.loaded);

		assertNotNull(index.find("speaker", "Line01.wav"));
		assertEquals(1, loader.loaded.size());
	}

	@Test
	void triesTheOtherAnimsetsAndRemembersWhatIsMissing() {
		final CountingLoader loader = new CountingLoader(FaceFxAnimIndex.logicalPath(HUMAN02));
		final FaceFxAnimIndex index = new FaceFxAnimIndex(Arrays.asList(HUMAN01, HUMAN02), loader);
		assertNotNull(index.find("speaker", "Line01.wav"));
		assertEquals(2, loader.loaded.size());
		assertNull(index.find("speaker", "NoSuchLine.wav"));
		final int loads = loader.loaded.size();
		assertNull(index.find("speaker", "NoSuchLine.wav"));
		assertNull(index.find("other", "Line01.wav"));
		assertEquals(loads, loader.loaded.size());
	}

	@Test
	void fallsBackToTheSpeakerTheLineNamesGive() {
		final String human02 = FaceFxAnimIndex.logicalPath(HUMAN02);
		final FaceFxAnimIndex.Loader loader = path -> path.equals(human02) ? lineSet("H01Speaker08") : null;
		final FaceFxAnimIndex index = new FaceFxAnimIndex(Arrays.asList(HUMAN01, HUMAN02), loader);
		assertNotNull(index.find("arthaswithsword", "Sound/Dialogue/H01Speaker08.flac"));
		assertNotNull(index.find(null, "H01Speaker08.wav"));
		assertNull(index.find("arthaswithsword", "Line01.wav"));
		assertNull(index.find(null, ""));
	}

	private static byte[] lineSet(final String line) {
		final FaceFxTest.Builder b = new FaceFxTest.Builder(new String[] { "FxObject", "FxAnim", "FxAnimCurve" },
				new String[] { "Speaker", line, "FxAnim", "open", "wide" });
		b.putInt(3);
		b.putInt(0x150);
		b.putInt(1);
		b.putInt(1);
		b.putInt(3);
		b.putInt(2);
		b.putKey(0f, 0f, 0f, 0f);
		b.putKey(1f, 1f, 0f, 0f);
		return b.bytes();
	}

	@Test
	void givesTheActorFileOfAPortraitModel() {
		assertEquals("Units\\Human\\Footman\\Footman.facefx_ingame",
				FaceFxLibrary.actorPathForModel("Units/Human/Footman/Footman_Portrait.mdx"));
		assertEquals("units\\human\\footman\\footman.facefx_ingame",
				FaceFxLibrary.actorPathForModel("units\\human\\footman\\footman_portrait.mdx"));
		assertEquals("Units\\Human\\Footman\\Footman.facefx_ingame",
				FaceFxLibrary.actorPathForModel("Units\\Human\\Footman\\Footman.mdx"));
		assertEquals("footman", FaceFxLibrary.speakerOfActor("Units\\Human\\Footman\\Footman.facefx_ingame"));
		assertNull(FaceFxLibrary.actorPathForModel(""));
		assertNull(FaceFxLibrary.actorPathForModel(null));
	}

	@Test
	void mapsTheBonesOfAModelByName() throws IOException {
		final FaceFxActor actor = FaceFxActor.read(FaceFxTest.actor());
		final List<String> nodes = Arrays.asList("bone_head", "unit:LIP", "other:jaw", "weapon", null);
		assertArrayEquals(new int[] { -1, 1, 0, -1, -1 }, FaceFxBoneMap.map(actor, nodes, 2));
		assertNull(FaceFxBoneMap.map(actor, nodes, 3));
		assertNull(FaceFxBoneMap.map(actor, nodes));
	}

	@Test
	void turnsThePoseOffsetsIntoModelAxes() throws IOException {
		final FaceFxActor.Bone jaw = FaceFxActor.read(FaceFxTest.actor()).findBone("jaw");
		final float[] work = new float[10];
		final float[] translation = new float[3];
		final float[] rotation = new float[4];
		final float[] scale = new float[3];

		jaw.solveModelDelta(new float[] { 0f, 0f }, work, translation, rotation, scale);
		assertArrayEquals(new float[] { 0f, 0f, 0f }, translation, 1e-6f);
		assertArrayEquals(new float[] { 0f, 0f, 0f, 1f }, rotation, 1e-6f);
		assertArrayEquals(new float[] { 1f, 1f, 1f }, scale, 1e-6f);

		// The character's y (up) is the model's z, its z (forward) the model's x, its x (left) the model's y.
		jaw.solveModelDelta(new float[] { 1f, 0f }, work, translation, rotation, scale);
		assertArrayEquals(new float[] { 0f, 0f, -0.5f }, translation, 1e-6f);
		final float inverse = (float) (1 / Math.sqrt(1.25));
		assertArrayEquals(new float[] { 0f, 0.5f * inverse, 0f, inverse }, rotation, 1e-6f);

		jaw.solveModelDelta(new float[] { 0f, 2f }, work, translation, rotation, scale);
		assertArrayEquals(new float[] { 0f, 2f, 0f }, translation, 1e-6f);
		assertArrayEquals(new float[] { 1f, 1.5f, 1f }, scale, 1e-6f);
	}
}
