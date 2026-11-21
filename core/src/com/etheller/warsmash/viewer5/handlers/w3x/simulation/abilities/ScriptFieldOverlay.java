package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;

/**
 * The object data of an ability with what a script set on that one ability (the ability field natives) in front of
 * it. An ability reads its numbers through this when it is filled from its data, so a set takes effect the next time
 * it is filled and every other unit's ability of the same type keeps the data's values.
 *
 * A script value is kept under {@code <column>#<index>} for one element of a column and {@code <column>#list} for
 * a whole list; columns compare without regard to case, like the data's own.
 */
public final class ScriptFieldOverlay implements GameObject {
	private final GameObject base;
	private final Map<String, String> own;

	private ScriptFieldOverlay(final GameObject base, final Map<String, String> own) {
		this.base = base;
		this.own = own;
	}

	/** The data itself when the ability has no script values, so an untouched ability reads exactly as before. */
	public static GameObject of(final AbstractCAbility ability, final GameObject base) {
		final Map<String, String> fields = ability.getScriptFields();
		if (fields.isEmpty() || (base instanceof ScriptFieldOverlay)) {
			return base;
		}
		final Map<String, String> lowerCase = new HashMap<>();
		for (final Map.Entry<String, String> entry : fields.entrySet()) {
			lowerCase.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
		}
		return new ScriptFieldOverlay(base, lowerCase);
	}

	private String ownValue(final String field, final int index) {
		return this.own.get(field.toLowerCase(Locale.ROOT) + "#" + Math.max(0, index));
	}

	private String ownList(final String field) {
		return this.own.get(field.toLowerCase(Locale.ROOT) + "#list");
	}

	private boolean touched(final String field) {
		final String prefix = field.toLowerCase(Locale.ROOT) + "#";
		for (final String key : this.own.keySet()) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public List<String> getFieldAsList(final String field) {
		if (!touched(field)) {
			return this.base.getFieldAsList(field);
		}
		final List<String> merged = new ArrayList<>();
		final String list = ownList(field);
		if (list != null) {
			if (!list.isEmpty()) {
				Collections.addAll(merged, list.split(",", -1));
			}
		}
		else {
			final List<String> baseList = this.base.getFieldAsList(field);
			if (baseList != null) {
				merged.addAll(baseList);
			}
		}
		final String prefix = field.toLowerCase(Locale.ROOT) + "#";
		for (final Map.Entry<String, String> entry : this.own.entrySet()) {
			if (entry.getKey().startsWith(prefix)) {
				final String suffix = entry.getKey().substring(prefix.length());
				if (!"list".equals(suffix)) {
					final int index = parseIndex(suffix);
					if (index >= 0) {
						while (merged.size() <= index) {
							// A gap reads as the data does, which repeats its last element.
							merged.add(this.base.getField(field, merged.size()));
						}
						merged.set(index, entry.getValue());
					}
				}
			}
		}
		return merged;
	}

	private static int parseIndex(final String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (final NumberFormatException exc) {
			return -1;
		}
	}

	@Override
	public String getField(final String field) {
		if (!touched(field)) {
			return this.base.getField(field);
		}
		return String.join(",", getFieldAsList(field));
	}

	@Override
	public String getField(final String field, final int index) {
		if (!touched(field)) {
			return this.base.getField(field, index);
		}
		final List<String> merged = getFieldAsList(field);
		if (merged.isEmpty()) {
			return "";
		}
		// The data's own lists repeat their last element for an index past the end.
		return merged.get(Math.min(Math.max(0, index), merged.size() - 1));
	}

	@Override
	public int getFieldValue(final String field) {
		return getFieldValue(field, 0);
	}

	@Override
	public int getFieldValue(final String field, final int index) {
		if (!touched(field)) {
			return this.base.getFieldValue(field, index);
		}
		final String text = getField(field, index).trim();
		try {
			return Integer.parseInt(text);
		}
		catch (final NumberFormatException exc) {
			try {
				return (int) Long.parseLong(text);
			}
			catch (final NumberFormatException exc2) {
				return (int) parseNumber(text);
			}
		}
	}

	@Override
	public float getFieldFloatValue(final String field) {
		return getFieldFloatValue(field, 0);
	}

	@Override
	public float getFieldFloatValue(final String field, final int index) {
		if (!touched(field)) {
			return this.base.getFieldFloatValue(field, index);
		}
		return parseNumber(getField(field, index));
	}

	private static float parseNumber(final String text) {
		try {
			return Float.parseFloat(text.trim());
		}
		catch (final NumberFormatException exc) {
			return 0f;
		}
	}

	@Override
	public void setField(final String field, final String value) {
		this.base.setField(field, value);
	}

	@Override
	public void setField(final String slk, final String field, final String value) {
		this.base.setField(slk, field, value);
	}

	@Override
	public void setField(final String slk, final String field, final String value, final int index) {
		this.base.setField(slk, field, value, index);
	}

	@Override
	public void clearFieldList(final String slk, final String field) {
		this.base.clearFieldList(slk, field);
	}

	@Override
	public List<? extends GameObject> getFieldAsList(final String field, final ObjectData objectData) {
		return this.base.getFieldAsList(field, objectData);
	}

	@Override
	public String getId() {
		return this.base.getId();
	}

	@Override
	public ObjectData getTable() {
		return this.base.getTable();
	}

	@Override
	public String getName() {
		return this.base.getName();
	}

	@Override
	public String getLegacyName() {
		return this.base.getLegacyName();
	}

	@Override
	public Set<String> keySet() {
		return this.base.keySet();
	}
}
