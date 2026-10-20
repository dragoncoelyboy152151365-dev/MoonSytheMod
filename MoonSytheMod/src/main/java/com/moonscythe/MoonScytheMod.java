package com.moonscythe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class MoonScytheMod implements ModInitializer {
	// ---- Tweak these ----
	/** Crits needed in a row. */
	private static final int CRITS_NEEDED = 3;
	/** The streak resets this many ticks after the last crit (200 = 10 seconds). */
	private static final long STREAK_TIMEOUT_TICKS = 200;
	/** Strength level 2 = amplifier 1. */
	private static final int STRENGTH_AMPLIFIER = 1;
	/** Effect is refreshed every tick with this short duration, so it vanishes right after un-holding. */
	private static final int STRENGTH_REFRESH_TICKS = 40;

	/** Set to true while the beam is hurting someone, so it doesn't count as a normal hit. */
	static volatile boolean applyingBeamDamage = false;

	private static long tickCounter = 0;
	private static final Map<UUID, Streak> STREAKS = new HashMap<>();
	private static final Set<UUID> STRENGTH_GIVEN = new HashSet<>();
	private static final List<MoonBeam> BEAMS = new ArrayList<>();

	private static final class Streak {
		int count = 0;
		long lastCritTick = 0;
	}

	@Override
	public void onInitialize() {
		// /moonscythe [players]  - gives the weapon (the server-side replacement for the creative tab)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("moonscythe")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(ctx -> give(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))));

		ServerTickEvents.END_SERVER_TICK.register(MoonScytheMod::onServerTick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MoonScytheMod::onAfterDamage);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			STREAKS.remove(id);
			STRENGTH_GIVEN.remove(id);
		});
	}

	private static int give(CommandSourceStack source, Collection<ServerPlayer> players) {
		int given = 0;
		for (ServerPlayer player : players) {
			ItemStack stack = MoonScytheItem.create(source.getServer().registryAccess());
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			given++;
		}
		final int count = given;
		source.sendSuccess(() -> Component.literal("Gave the Moon Scythe to " + count + " player(s)."), true);
		return given;
	}

	// ------------------------------------------------------------------ tick

	private static void onServerTick(MinecraftServer server) {
		tickCounter++;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Lock enchantments / unbreakable on every scythe the player has
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (MoonScytheItem.isScythe(stack)) {
					MoonScytheItem.enforce(stack, server.registryAccess());
				}
			}
			ItemStack carried = player.containerMenu.getCarried();
			if (MoonScytheItem.isScythe(carried)) {
				MoonScytheItem.enforce(carried, server.registryAccess());
			}

			// Strength II only while holding it in the main hand
			UUID id = player.getUUID();
			if (MoonScytheItem.isScythe(player.getMainHandItem())) {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, STRENGTH_REFRESH_TICKS, STRENGTH_AMPLIFIER, true, false, false));
				STRENGTH_GIVEN.add(id);
			} else if (STRENGTH_GIVEN.remove(id)) {
				MobEffectInstance effect = player.getEffect(MobEffects.STRENGTH);
				// Only remove OUR effect, not a real strength potion
				if (effect != null && effect.getAmplifier() == STRENGTH_AMPLIFIER && effect.getDuration() <= STRENGTH_REFRESH_TICKS) {
					player.removeEffect(MobEffects.STRENGTH);
				}
			}
		}

		Iterator<MoonBeam> it = BEAMS.iterator();
		while (it.hasNext()) {
			if (it.next().tick()) {
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- combat

	private static void onAfterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (applyingBeamDamage || damageTaken <= 0) {
			return;
		}

		// "Getting hit back" resets the attacker's streak
		if (entity instanceof ServerPlayer hurtPlayer) {
			STREAKS.remove(hurtPlayer.getUUID());
		}

		if (!(source.getEntity() instanceof ServerPlayer attacker)
			|| source.getDirectEntity() != attacker
			|| entity == attacker
			|| !MoonScytheItem.isScythe(attacker.getMainHandItem())) {
			return;
		}

		if (!isCrit(attacker)) {
			return; // normal hits neither add to nor break the streak
		}

		Streak streak = STREAKS.computeIfAbsent(attacker.getUUID(), k -> new Streak());
		if (streak.count > 0 && tickCounter - streak.lastCritTick > STREAK_TIMEOUT_TICKS) {
			streak.count = 0;
		}
		streak.count++;
		streak.lastCritTick = tickCounter;

		if (streak.count >= CRITS_NEEDED) {
			STREAKS.remove(attacker.getUUID());
			attacker.displayClientMessage(Component.literal("MOON BEAM!"), true);
			BEAMS.add(new MoonBeam(attacker, entity, (ServerLevel) entity.level()));
		} else {
			attacker.displayClientMessage(Component.literal("Moon charge " + streak.count + "/" + CRITS_NEEDED), true);
		}
	}

	/** Same conditions vanilla uses for a critical hit (falling, airborne, not sprinting, fully charged). */
	private static boolean isCrit(ServerPlayer p) {
		return p.getAttackStrengthScale(0.5f) > 0.9f
			&& p.fallDistance > 0
			&& !p.onGround()
			&& !p.onClimbable()
			&& !p.isInWater()
			&& !p.hasEffect(MobEffects.BLINDNESS)
			&& !p.isPassenger()
			&& !p.isSprinting();
	}
}
