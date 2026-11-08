package dev.halloween;
import net.minecraft.registry.*;
import net.minecraft.sound.*;
import net.minecraft.util.Identifier;
final class TransportSounds {
 static SoundEvent STEAM,ROLLING,WHISTLE,BRAKE,SEA,CREAK;
 static SoundEvent sound(String name){var id=Identifier.of("halloween","transport."+name);return Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));}
 static void register(){STEAM=sound("steam");ROLLING=sound("rolling");WHISTLE=sound("whistle");BRAKE=sound("brake");SEA=sound("sea");CREAK=sound("creak");}
 static void tick(net.minecraft.server.world.ServerWorld w,RailVehicleEntity t){
  if(t.isShip()){if(t.age%180==0)w.playSound(null,t.getX(),t.getY()+2,t.getZ(),CREAK,SoundCategory.AMBIENT,.48f,.86f+w.random.nextFloat()*.24f);return;}
  if(t.kind()!=0)return;
  if(t.dwell==300||t.dwell==240||t.dwell==35){w.playSound(null,t.getX(),t.getY()+4,t.getZ(),WHISTLE,SoundCategory.BLOCKS,1.6f,t.dwell==35?1.03f:.92f);if(t.dwell>35){w.playSound(null,t.getX(),t.getY()+1,t.getZ(),BRAKE,SoundCategory.BLOCKS,.8f,1);for(int side:new int[]{-1,1}){var p=t.getEntityPos().add(RailGeometry.rotate(new net.minecraft.util.math.Vec3d(side*1.3,1,2),t.getYaw()));w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,p.x,p.y,p.z,12,.13,.1,.4,.02);}}}
  if(t.speed>.025&&t.age%8==0)for(int side:new int[]{-1,1}){var p=t.getEntityPos().add(RailGeometry.rotate(new net.minecraft.util.math.Vec3d(side*1.3,1,3),t.getYaw()));w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,p.x,p.y,p.z,2,.08,.04,.15,.015);}
 }
}
