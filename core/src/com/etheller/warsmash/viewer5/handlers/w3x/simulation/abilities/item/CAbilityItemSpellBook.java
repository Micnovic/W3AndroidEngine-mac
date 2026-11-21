package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.menu.CAbilityMenu;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Spell Book: while the unit carries it (or has the ability), the spells of the list (Data A) are on a menu button
 * of the unit's command card. The book holds as many of them as Data C to D say, picked at random when it is
 * added.
 */
public class CAbilityItemSpellBook extends AbstractCAbilityItemInstantUse implements CAbilityMenu {
	private final List<War3ID> spellList = new ArrayList<>();
	private int minimumSpells;
	private int maximumSpells;
	private final List<CAbility> spells = new ArrayList<>();

	public CAbilityItemSpellBook(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.spellList.clear();
		for (final String spell : worldEditorAbility.getFieldAsList(AbilityFields.DATA_A + level)) {
			if (spell.length() == 4) {
				this.spellList.add(War3ID.fromString(spell));
			}
		}
		this.minimumSpells = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
		this.maximumSpells = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_D + level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemspellbook;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		final List<War3ID> pool = new ArrayList<>(this.spellList);
		final int spellCount = Math.min(pool.size(),
				this.minimumSpells + ((this.maximumSpells > this.minimumSpells)
						? game.getSeededRandom().nextInt(this.maximumSpells - this.minimumSpells + 1)
						: 0));
		while ((this.spells.size() < spellCount) && !pool.isEmpty()) {
			final War3ID spellId = pool.remove(game.getSeededRandom().nextInt(pool.size()));
			final CAbilityType<?> abilityType = game.getAbilityData().getAbilityType(spellId);
			if (abilityType == null) {
				continue;
			}
			final CAbility spell = abilityType.createAbility(game.getHandleIdAllocator().createId());
			if (spell instanceof AbstractCAbility) {
				((AbstractCAbility) spell).setContainerMenuOrderId(getBaseOrderId());
			}
			this.spells.add(spell);
			unit.add(game, spell);
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		for (final CAbility spell : this.spells) {
			unit.remove(game, spell);
		}
		this.spells.clear();
	}
}
