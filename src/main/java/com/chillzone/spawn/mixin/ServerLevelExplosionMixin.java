package com.chillzone.spawn.mixin;

import com.chillzone.spawn.SpawnProtection;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ExplosionParticleInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelExplosionMixin {
    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V", at = @At("HEAD"), cancellable = true)
    private void chillzone$cancelExplosionsTouchingSpawn(Entity source, DamageSource damageSource,
            ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire,
            Level.ExplosionInteraction interactionType, ParticleOptions smallParticles, ParticleOptions largeParticles,
            WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> explosionSound, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (SpawnProtection.explosionTouches(level, x, z, radius)) ci.cancel();
    }
}
