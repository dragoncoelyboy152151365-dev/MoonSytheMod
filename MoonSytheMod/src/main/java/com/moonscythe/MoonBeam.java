package com.moonscythe;

import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One running Moon Beam: a burst of white particle lasers that only hurts the chosen opponent. */
public final class MoonBeam {
	// ---- Tweak these ----
	static final int DURATION_TICKS = 80;       // 4 seconds
	static final int DAMAGE_INTERVAL = 5;       // damage every 5 ticks
	static final float DAMAGE_PER_HIT = 2.0f;   // 16 hits x 2 = 32 damage (16 hearts) total
	static final int LASERS_PER_BURST = 7;      // random lasers converging on the opponent
	static final double LASER_RADIUS = 12.0;
	static final double SKY_HEIGHT = 32.0;

	private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(0xFFFFFF, 1.6f);

	private final ServerPlayer owner;
	private final UUID targetId;
	private final ServerLevel level;
	private int age = 0;

	public MoonBeam(ServerPlayer owner, LivingEntity target, ServerLevel level) {
		this.owner = owner;
		this.targetId = target.getUUID();
		this.level = level;
	}

	/** @return true when this beam is finished and should be removed. */
	public boolean tick() {
		Entity found = level.getEntity(targetId);
		if (!(found instanceof LivingEntity target) || !target.isAlive() || owner.isRemoved()) {
			return true;
		}

		Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
		RandomSource random = level.getRandom();

		if (age == 0) {
			level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0f, 1.8f);
			level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.4f);
			// Big white explosion of light
			level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 150, 0, 0, 0, 0.9);
		}
		if (age % 10 == 0 && age > 0) {
			level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3.0f, 2.0f);
			level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 60, 0, 0, 0, 0.7);
		}

		// 1) The moon beam itself: a thick white column from the sky down onto the opponent
		for (double y = SKY_HEIGHT; y >= 0; y -= 0.6) {
			double px = center.x + random.nextGaussian() * 0.12;
			double pz = center.z + random.nextGaussian() * 0.12;
			level.sendParticles(ParticleTypes.END_ROD, px, center.y + y, pz, 1, 0, 0, 0, 0);
			if (y < 6.0) {
				level.sendParticles(WHITE_DUST, px, center.y + y, pz, 2, 0.25, 0, 0.25, 0);
			}
		}

		// 2) Lasers firing in from everywhere, all converging on the opponent
		if (age % 2 == 0) {
			for (int i = 0; i < LASERS_PER_BURST; i++) {
				double theta = random.nextDouble() * Math.PI * 2;
				double up = random.nextDouble() * 1.4 - 0.2; // mostly above, some low angles
				double r = LASER_RADIUS * (0.6 + random.nextDouble() * 0.4);
				Vec3 origin = center.add(Math.cos(theta) * r, up * r * 0.8, Math.sin(theta) * r);
				drawLine(origin, center);
			}
			// Two lasers straight from the player's scythe to the opponent
			Vec3 handPos = owner.getEyePosition().add(0, -0.3, 0);
			drawLine(handPos, center);
			drawLine(handPos.add(0, 0.4, 0), center);
		}

		// 3) Damage - ONLY the opponent
		if (age % DAMAGE_INTERVAL == 0) {
			target.invulnerableTime = 0;
			MoonScytheMod.applyingBeamDamage = true;
			try {
				target.hurtServer(level, level.damageSources().indirectMagic(owner, owner), DAMAGE_PER_HIT);
			} finally {
				MoonScytheMod.applyingBeamDamage = false;
			}
		}

		age++;
		if (age >= DURATION_TICKS) {
			level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0f, 1.6f);
			level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 100, 0, 0, 0, 1.0);
			return true;
		}
		return false;
	}

	private void drawLine(Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 0.001) {
			return;
		}
		Vec3 step = delta.scale(0.9 / length);
		int points = (int) (length / 0.9);
		double x = from.x, y = from.y, z = from.z;
		for (int i = 0; i <= points; i++) {
			level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
			level.sendParticles(WHITE_DUST, x, y, z, 1, 0.05, 0.05, 0.05, 0);
			x += step.x;
			y += step.y;
			z += step.z;
		}
	}
}
