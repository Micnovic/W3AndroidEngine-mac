package com.etheller.warsmash.parsers.jass.triggers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;

import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * Measured on 1.29: a removed unit leaves every group it was in, so ForGroup,
 * CountUnitsInGroup, FirstOfGroup and IsUnitInGroup no longer see it. Removed members are dropped on read.
 */
public class UnitGroup extends ArrayList<CUnit> implements CAllocatedHandle {
	/**
	 * Counts the units removed from the game. Only a change signal: it lets a group skip a pass over its members that
	 * could not find anything new; what a pass finds depends on the members alone.
	 */
	private static long removalEpoch = 1;

	public static void unitRemoved() {
		removalEpoch++;
	}

	private int handleId;
	private boolean handleReleased;
	private boolean handleIdKept;
	private int referenceCount;
	/** How many times each unit is in the list, for contains; read only by key, never iterated. */
	@SavedTransient
	private Map<CUnit, Integer> counts;
	/** The list's modCount the counts match; any change made around the overrides below makes them stale. */
	@SavedTransient
	private int countsModCount;
	/** The removal epoch of the last pass that dropped removed units; 0 when none is known to be current. */
	@SavedTransient
	private long prunedEpoch;

	public UnitGroup(int handleId) {
		this.handleId = handleId;
	}

	@Override
	public int getHandleId() {
		return handleId;
	}

	@Override
	public void setHandleId(final int handleId) {
		this.handleId = handleId;
	}

	@Override
	public boolean releaseHandle() {
		if (this.handleReleased) {
			return false;
		}
		this.handleReleased = true;
		return true;
	}

	@Override
	public void keepHandleId() {
		this.handleIdKept = true;
	}

	@Override
	public boolean isHandleIdKept() {
		return this.handleIdKept;
	}

	@Override
	public int changeReferenceCount(final int delta) {
		this.referenceCount += delta;
		return this.referenceCount;
	}

	@Override
	public int getReferenceCount() {
		return this.referenceCount;
	}

	/**
	 * Measured on 1.29: a group keeps its units in a fixed order, whatever the order they were added in; ForGroup and
	 * FirstOfGroup follow it. Units made after the game started come first, newest first (by handle id). Then come the
	 * units made while the map was loading (the map's own units): grouped by unit type, the type of the unit made last
	 * first, and inside a type in the order they were made.
	 */
	@Override
	public boolean add(final CUnit unit) {
		int index = super.size();
		if (unit != null) {
			for (int i = 0; i < super.size(); i++) {
				final CUnit other = super.get(i);
				if ((other == null) || precedes(unit, other)) {
					index = i;
					break;
				}
			}
		}
		final boolean indexed = countsValid();
		super.add(index, unit);
		if ((unit != null) && unit.isRemovedFromGame()) {
			this.prunedEpoch = 0;
		}
		if (indexed) {
			increment(unit);
			this.countsModCount = this.modCount;
		}
		return true;
	}

	@Override
	public void add(final int index, final CUnit unit) {
		this.prunedEpoch = 0;
		super.add(index, unit);
	}

	@Override
	public boolean addAll(final int index, final Collection<? extends CUnit> units) {
		this.prunedEpoch = 0;
		return super.addAll(index, units);
	}

	private boolean countsValid() {
		return (this.counts != null) && (this.countsModCount == this.modCount);
	}

	private void increment(final CUnit unit) {
		this.counts.merge(unit, 1, Integer::sum);
	}

	private void decrement(final Object unit) {
		this.counts.computeIfPresent((CUnit) unit, (key, count) -> (count > 1) ? count - 1 : null);
	}

	private Map<CUnit, Integer> counts() {
		if (!countsValid()) {
			this.counts = new HashMap<>();
			for (int i = 0; i < super.size(); i++) {
				increment(super.get(i));
			}
			this.countsModCount = this.modCount;
		}
		return this.counts;
	}

	@Override
	public boolean remove(final Object o) {
		if (!counts().containsKey(o)) {
			return false;
		}
		super.remove(o);
		decrement(o);
		this.countsModCount = this.modCount;
		return true;
	}

	@Override
	public CUnit remove(final int index) {
		final boolean indexed = countsValid();
		final CUnit removed = super.remove(index);
		if (indexed) {
			decrement(removed);
			this.countsModCount = this.modCount;
		}
		return removed;
	}

	@Override
	public boolean removeAll(final Collection<?> units) {
		if (units == this) {
			final boolean changed = !super.isEmpty();
			clear();
			return changed;
		}
		counts();
		final boolean changed = super.removeIf(units::contains);
		if (changed) {
			for (final Object unit : units) {
				this.counts.remove(unit);
			}
			this.countsModCount = this.modCount;
		}
		return changed;
	}

	@Override
	public void clear() {
		super.clear();
		this.counts = null;
	}

	@Override
	public CUnit set(final int index, final CUnit unit) {
		this.counts = null;
		this.prunedEpoch = 0;
		return super.set(index, unit);
	}

	public static boolean precedes(final CUnit unit, final CUnit other) {
		final boolean unitFromStart = unit.isMadeBeforeStart();
		final boolean otherFromStart = other.isMadeBeforeStart();
		if (unitFromStart != otherFromStart) {
			return otherFromStart;
		}
		if (!unitFromStart) {
			return other.getHandleId() < unit.getHandleId();
		}
		final int unitTypeLast = unit.getStartTypeLastIndex();
		final int otherTypeLast = other.getStartTypeLastIndex();
		if (unitTypeLast != otherTypeLast) {
			return unitTypeLast > otherTypeLast;
		}
		return unit.getStartCreationIndex() < other.getStartCreationIndex();
	}

	@Override
	public boolean addAll(final Collection<? extends CUnit> units) {
		for (final CUnit unit : units) {
			add(unit);
		}
		return !units.isEmpty();
	}

	private void dropRemovedUnits() {
		if (this.prunedEpoch == removalEpoch) {
			return;
		}
		this.prunedEpoch = removalEpoch;
		final boolean indexed = countsValid();
		final boolean changed = super.removeIf(unit -> {
			if ((unit != null) && unit.isRemovedFromGame()) {
				if (indexed) {
					decrement(unit);
				}
				return true;
			}
			return false;
		});
		if (changed && indexed) {
			this.countsModCount = this.modCount;
		}
	}

	@Override
	public int size() {
		dropRemovedUnits();
		return super.size();
	}

	@Override
	public boolean isEmpty() {
		dropRemovedUnits();
		return super.isEmpty();
	}

	@Override
	public CUnit get(final int index) {
		dropRemovedUnits();
		return super.get(index);
	}

	@Override
	public boolean contains(final Object o) {
		dropRemovedUnits();
		return counts().containsKey(o);
	}

	@Override
	public int indexOf(final Object o) {
		dropRemovedUnits();
		return counts().containsKey(o) ? super.indexOf(o) : -1;
	}

	@Override
	public Iterator<CUnit> iterator() {
		dropRemovedUnits();
		return super.iterator();
	}

	@Override
	public ListIterator<CUnit> listIterator() {
		dropRemovedUnits();
		return super.listIterator();
	}

	@Override
	public ListIterator<CUnit> listIterator(final int index) {
		dropRemovedUnits();
		return super.listIterator(index);
	}

	@Override
	public Spliterator<CUnit> spliterator() {
		dropRemovedUnits();
		return super.spliterator();
	}

	@Override
	public void forEach(final Consumer<? super CUnit> action) {
		dropRemovedUnits();
		super.forEach(action);
	}

	@Override
	public Object[] toArray() {
		// ForGroup walks a copy (new ArrayList<>(group)), which reads toArray.
		dropRemovedUnits();
		return super.toArray();
	}

	@Override
	public <T> T[] toArray(final T[] a) {
		dropRemovedUnits();
		return super.toArray(a);
	}

}
