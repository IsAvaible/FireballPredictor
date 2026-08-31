package com.simonconrad.fireballpredictor.mixin;

import net.minecraft.loot.condition.AlternativeLootCondition;
import net.minecraft.loot.condition.LootCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 1.21.11 counterpart of 26.2's CompositeLootItemCondition accessor: the shared
 * "terms" field lives on AlternativeLootCondition (base of AllOf/AnyOfLootCondition).
 */
@Mixin(AlternativeLootCondition.class)
public interface CompositeLootItemConditionAccessor {
    @Accessor("terms")
    List<LootCondition> getTerms();
}
