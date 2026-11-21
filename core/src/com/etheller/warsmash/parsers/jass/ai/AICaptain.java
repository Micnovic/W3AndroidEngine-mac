package com.etheller.warsmash.parsers.jass.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * One of the two groups a computer player commands: the attack captain leads the waves the script forms with
 * InitAssault/AddAssault, the defense captain holds every other fighter at home.
 */
public class AICaptain {
	private final List<CUnit> members = new ArrayList<>();
	@SavedTransient
	private Set<CUnit> memberSet;
	private final List<CUnit> previousMembers = new ArrayList<>();
	private final Map<CUnit, Integer> orderedGeneration = new HashMap<>();
	private boolean full = true;
	private float homeX;
	private float homeY;
	private boolean homeSet;
	private CUnit goalUnit;
	private float goalX;
	private float goalY;
	private boolean hasGoal;
	private boolean retreating;
	private int generation;

	public List<CUnit> getMembers() {
		return Collections.unmodifiableList(this.members);
	}

	public boolean isMember(final Object unit) {
		if (this.memberSet == null) {
			this.memberSet = new HashSet<>(this.members);
		}
		return this.memberSet.contains(unit);
	}

	public void addMember(final CUnit unit) {
		this.members.add(unit);
		if (this.memberSet != null) {
			this.memberSet.add(unit);
		}
	}

	public boolean removeMember(final CUnit unit) {
		final boolean removed = this.members.remove(unit);
		if (removed) {
			this.memberSet = null;
		}
		return removed;
	}

	public void removeMembersIf(final Predicate<CUnit> filter) {
		if (this.members.removeIf(filter)) {
			this.memberSet = null;
		}
	}

	/** Iterates the members in order; remove() on it keeps the membership lookup in step. */
	public Iterator<CUnit> memberIterator() {
		final Iterator<CUnit> inner = this.members.iterator();
		return new Iterator<CUnit>() {
			@Override
			public boolean hasNext() {
				return inner.hasNext();
			}

			@Override
			public CUnit next() {
				return inner.next();
			}

			@Override
			public void remove() {
				inner.remove();
				AICaptain.this.memberSet = null;
			}
		};
	}

	public List<CUnit> getPreviousMembers() {
		return this.previousMembers;
	}

	/** Units removed from the game cannot be taken into an assault again. */
	public void forgetRemovedPreviousMembers() {
		this.previousMembers.removeIf(CUnit::isRemovedFromGame);
	}

	public void beginAssault() {
		this.previousMembers.clear();
		this.previousMembers.addAll(this.members);
		this.members.clear();
		this.memberSet = null;
		this.full = true;
	}

	public void addRequestResult(final boolean satisfied) {
		this.full &= satisfied;
	}

	public boolean isRequestsFull() {
		return this.full;
	}

	public float getHomeX() {
		return this.homeX;
	}

	public float getHomeY() {
		return this.homeY;
	}

	public boolean isHomeSet() {
		return this.homeSet;
	}

	public void setHome(final float x, final float y) {
		this.homeX = x;
		this.homeY = y;
		this.homeSet = true;
	}

	public CUnit getGoalUnit() {
		return this.goalUnit;
	}

	public float getGoalX() {
		return this.goalUnit != null ? this.goalUnit.getX() : this.goalX;
	}

	public float getGoalY() {
		return this.goalUnit != null ? this.goalUnit.getY() : this.goalY;
	}

	public boolean hasGoal() {
		return this.hasGoal;
	}

	public void setGoal(final CUnit unit, final float x, final float y) {
		if (!this.hasGoal || (this.goalUnit != unit) || (unit == null && ((x != this.goalX) || (y != this.goalY)))) {
			this.generation++;
		}
		this.goalUnit = unit;
		this.goalX = x;
		this.goalY = y;
		this.hasGoal = true;
		this.retreating = false;
	}

	public void clearGoal() {
		if (this.hasGoal) {
			this.generation++;
		}
		this.goalUnit = null;
		this.hasGoal = false;
	}

	public int getGeneration() {
		return this.generation;
	}

	public boolean isRetreating() {
		return this.retreating;
	}

	public void setRetreating(final boolean retreating) {
		this.retreating = retreating;
	}

	public boolean needsOrder(final CUnit unit) {
		final Integer ordered = this.orderedGeneration.get(unit);
		return (ordered == null) || (ordered != this.generation);
	}

	public void markOrdered(final CUnit unit) {
		this.orderedGeneration.put(unit, this.generation);
	}

	public void clearOrders() {
		this.orderedGeneration.clear();
	}

	public void forgetOrders() {
		this.orderedGeneration.keySet().removeIf((unit) -> !isMember(unit));
	}

	public float getCenterX() {
		float sum = 0;
		for (final CUnit unit : this.members) {
			sum += unit.getX();
		}
		return this.members.isEmpty() ? this.homeX : sum / this.members.size();
	}

	public float getCenterY() {
		float sum = 0;
		for (final CUnit unit : this.members) {
			sum += unit.getY();
		}
		return this.members.isEmpty() ? this.homeY : sum / this.members.size();
	}
}
