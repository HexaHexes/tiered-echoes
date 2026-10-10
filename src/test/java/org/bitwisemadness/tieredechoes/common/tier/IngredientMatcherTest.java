package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientMatcherTest {

    @Test
    void nullComponentsAreRejectedUpFront() {
        assertThrows(NullPointerException.class, () -> new IngredientMatcher(null, TestTiers.id("x")));
        assertThrows(NullPointerException.class, () -> new IngredientMatcher(IngredientMatcher.Kind.ITEM, null));
    }

    @Test
    void displayKeyMarksTagsSoDatapackAuthorsCanTellThemApart() {
        assertTrue(TestTiers.item("diamond").displayKey().equals("minecraft:diamond"));
        assertTrue(TestTiers.tag("swords").displayKey().equals("#minecraft:swords"));
    }

    @Test
    void itemMatcherResolvesAgainstTheRegistry() {
        TestBootstrap.init();
        final IngredientMatcher matcher = TestTiers.item("diamond");

        assertTrue(matcher.matches(new ItemStack(Items.DIAMOND)));
        assertFalse(matcher.matches(new ItemStack(Items.EMERALD)));
    }

    @Test
    void anItemThatIsNotRegisteredDegradesToANeverMatchingIngredientRatherThanThrowing() {
        TestBootstrap.init();
        final IngredientMatcher matcher = new IngredientMatcher(IngredientMatcher.Kind.ITEM,
                new net.minecraft.resources.ResourceLocation("tieredechoes", "no_such_item"));

        assertFalse(matcher.matches(new ItemStack(Items.DIAMOND)));
        assertFalse(matcher.matches(ItemStack.EMPTY));
    }

    /** Empty stacks are checked before any registry or tag work, so this needs no bootstrap. */
    @Test
    void emptyStacksNeverMatch() {
        assertFalse(TestTiers.item("diamond").matches(ItemStack.EMPTY));
        assertFalse(TestTiers.tag("swords").matches(ItemStack.EMPTY));
    }

    @Test
    void buildingAnIngredientIsLazySoTagReloadsArePickedUp() {
        TestBootstrap.init();
        // Touching toIngredient() must not throw even though no tag manager has been run; the tag
        // itself resolves later, when the smithing menu tests it.
        assertNotNull(TestTiers.tag("swords").toIngredient());
        assertNotNull(TestTiers.item("diamond").toIngredient());
    }
}
