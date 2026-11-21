package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.Locale;

import com.hiveworkshop.rms.parsers.mdlx.AnimationMap;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAttachment;

public class Attachment extends GenericObject {
	protected String name;
	protected final String path;
	protected final int attachmentId;
	protected MdxModel internalModel;

	public Attachment(final MdxModel model, final MdlxAttachment attachment, final int index) {
		super(model, attachment, index);

		final String path = attachment.getPath().toLowerCase(Locale.ROOT).replace(".mdl", ".mdx");

		this.name = attachment.getName().toLowerCase(Locale.US);
		this.path = path;
		this.attachmentId = attachment.getAttachmentId();
		this.internalModel = null;

		// Second condition is against custom resources using arbitrary paths
		if (!path.equals("") && (path.indexOf(".mdx") != -1)) {
			// While the game goes on, the model of a building's birth animation or the like is read and built a few
			// units a frame like the model that attaches it, which waits for it before its instances load.
			final boolean deferBefore = model.viewer.setDeferModelLoads(model.viewer.isDeferModelTextures());
			final MdxModel internalModel;
			try {
				internalModel = (MdxModel) model.viewer.load(path, model.pathSolver, model.solverParams);
			}
			finally {
				model.viewer.setDeferModelLoads(deferBefore);
			}
			// Some DE birth models name themselves by their source path; an instance of such a model would
			// attach itself without end.
			if (internalModel != model) {
				this.internalModel = internalModel;
			}
		}
	}

	@Override
	public int getVisibility(final float[] out, final int sequence, final int frame, final int counter) {
		return this.getScalarValue(out, AnimationMap.KATV.getWar3id(), sequence, frame, counter, 1);
	}

	public String getName() {
		return this.name;
	}

	public int getAttachmentId() {
		return this.attachmentId;
	}
}
