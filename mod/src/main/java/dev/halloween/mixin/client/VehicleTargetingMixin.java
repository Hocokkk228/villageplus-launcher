package dev.halloween.mixin.client;
import dev.halloween.VehicleTargeting;
import java.util.function.Predicate;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ClientPlayerEntity.class)
public abstract class VehicleTargetingMixin {
 @Redirect(method="getCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;",at=@At(value="INVOKE",target="Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"))
 private static EntityHitResult actualVehicleShape(Entity camera,Vec3d start,Vec3d end,Box area,Predicate<Entity> predicate,double max){return VehicleTargeting.raycast(camera,start,end,area,predicate,max);}
}
