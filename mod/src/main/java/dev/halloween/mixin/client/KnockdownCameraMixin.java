package dev.halloween.mixin.client;

import dev.halloween.FightPose;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class KnockdownCameraMixin {
    @ModifyArgs(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void halloween$headNearGround(Args args,World world,Entity entity,boolean thirdPerson,boolean inverse,float delta){
        if(!(entity instanceof PlayerEntity player)||!player.isAlive())return;
        var combat=FightPose.of(player);
        int phase=combat.halloween$fightPose();
        if(!FightPose.down(phase))return;
        float progress=FightPose.recline(phase,combat.halloween$fightAge(delta));
        var feet=new Vec3d(args.get(0),MathHelper.lerp(delta,entity.lastY,entity.getY()),args.get(2));
        var head=feet.add(0,MathHelper.lerp(progress,1.62,.36),0);
        var wanted=head.add(Vec3d.fromPolar(0,combat.halloween$knockdownYaw()).multiply(-1.45*progress));
        var ray=world.raycast(new RaycastContext(head,wanted,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,entity));
        var camera=ray.getType()==net.minecraft.util.hit.HitResult.Type.MISS?wanted:ray.getPos().lerp(head,.08);
        args.set(0,camera.x);args.set(1,camera.y);args.set(2,camera.z);
    }

    @ModifyArgs(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;setRotation(FF)V",ordinal=1))
    private void halloween$lookUpWhileDown(Args args,World world,Entity entity,boolean thirdPerson,boolean inverse,float delta){
        if(thirdPerson||!(entity instanceof PlayerEntity player)||!player.isAlive())return;
        var combat=FightPose.of(player);
        float progress=FightPose.recline(combat.halloween$fightPose(),combat.halloween$fightAge(delta));
        if(progress>0)args.set(1,MathHelper.clamp((float)args.get(1)-28f*progress,-89,89));
    }
}
