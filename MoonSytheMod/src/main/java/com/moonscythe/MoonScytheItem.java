package com.moonscythe;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The Moon Scythe is a vanilla MACE with special data components. Because it is
 * a real mace, it keeps all mace behaviour (smash attack, fall damage bonus,
 * mace sounds) and vanilla clients can see and use it without any client mod.
 */
public final class MoonScytheItem {
	/** Marker stored in the custom_data component. */
	public static final String MARKER = "moon_scythe";

	/** Total displayed attack damage = 1 (player base) + 11. */
	private static final double DAMAGE_BONUS = 11.0;
	/** Netherite axe attack speed is 1.0 (4.0 - 3.0). Same swing cooldown as an axe. */
	private static final double SPEED_MODIFIER = -3.0;

	private MoonScytheItem() {}

	public static boolean isScythe(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.MACE)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARKER);
	}

	private static ItemEnchantments lockedEnchantments(HolderLookup.Provider registries) {
		Holder<Enchantment> breach = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BREACH);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(breach, 3);
		return mutable.toImmutable();
	}

	public static ItemStack create(HolderLookup.Provider registries) {
		ItemStack stack = new ItemStack(Items.MACE);

		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARKER, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		stack.set(DataComponents.ITEM_NAME, Component.literal("Moon Scythe").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Strength II while held").withStyle(ChatFormatting.GRAY),
			Component.literal("3 crits in a row unleash the Moon Beam").withStyle(ChatFormatting.GRAY)
		)));
		stack.set(DataComponents.RARITY, Rarity.EPIC);

		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE,
				new AttributeModifier(Identifier.withDefaultNamespace("base_attack_damage"), DAMAGE_BONUS, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED,
				new AttributeModifier(Identifier.withDefaultNamespace("base_attack_speed"), SPEED_MODIFIER, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.build());

		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		stack.set(DataComponents.ENCHANTMENTS, lockedEnchantments(registries));
		// Not enchantable in an enchanting table.
		stack.remove(DataComponents.ENCHANTABLE);
		return stack;
	}

	/**
	 * Forces the stack back to "Breach III and nothing else" and keeps it
	 * unbreakable. Run every tick, so anvils, grindstones, enchanting tables,
	 * commands, etc. cannot change the enchantments for longer than a tick.
	 */
	public static void enforce(ItemStack stack, HolderLookup.Provider registries) {
		Holder<Enchantment> breach = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BREACH);
		ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (current.size() != 1 || current.getLevel(breach) != 3) {
			stack.set(DataComponents.ENCHANTMENTS, lockedEnchantments(registries));
		}
		if (stack.get(DataComponents.UNBREAKABLE) == null) {
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		if (stack.getDamageValue() != 0) {
			stack.setDamageValue(0);
		}
		if (stack.get(DataComponents.ENCHANTABLE) != null) {
			stack.remove(DataComponents.ENCHANTABLE);
		}
	}
}
