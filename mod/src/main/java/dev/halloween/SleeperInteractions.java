package dev.halloween;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
/** A furniture click is selected from the exact ray hit, from either side of a compartment. */
final class SleeperInteractions {
 static double hit(Box box,Vec3d start,Vec3d end){return box.raycast(start,end).map(p->p.squaredDistanceTo(start)).orElse(Double.POSITIVE_INFINITY);}
 static int door(RailVehicleEntity coach,Vec3d start,Vec3d end){double nearest=Double.POSITIVE_INFINITY;int selected=-1;for(var part:RailGeometry.parts(4)){var box=part.group().startsWith("compartment_")?RailGeometry.compartmentBox(part,coach.compartments()):RailGeometry.partBox(part,coach.doorsOpen());double d=hit(box,start,end);if(d<nearest-1e-7){nearest=d;selected=(part.group().startsWith("compartment_")||part.group().startsWith("door_handle_"))?Integer.parseInt(part.group().substring(12)):-1;}}return selected;}
 static ActionResult interact(RailVehicleEntity coach,PlayerEntity player,Hand hand){var start=RailPassengerNetwork.toLocal(coach,player.getEyePos());var end=start.add(RailGeometry.rotate(player.getRotationVec(1).multiply(player.getEntityInteractionRange()),-coach.getYaw()));int index=door(coach,start,end);if(index>=0){coach.toggleCompartment(index);coach.getEntityWorld().playSound(null,player.getX(),player.getY(),player.getZ(),net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,net.minecraft.sound.SoundCategory.BLOCKS,.5f,1);return ActionResult.SUCCESS;}
  // Only lower berth upholstery accepts seating. Looking at a wall, table,
  // upper bunk or empty doorway can never select the nearest seat implicitly.
  double nearest=Double.POSITIVE_INFINITY;RailGeometry.Part selected=null;for(var p:RailGeometry.parts(4)){var b=p.group().startsWith("compartment_")?RailGeometry.compartmentBox(p,coach.compartments()):RailGeometry.partBox(p,coach.doorsOpen());double d=hit(b,start,end);if(d<nearest){nearest=d;selected=p;}}
  if(selected==null||!selected.state().isOf(net.minecraft.block.Blocks.RED_WOOL)||selected.box().minY>2.05)return ActionResult.PASS;
  var point=start.lerp(end,Math.sqrt(nearest)/start.distanceTo(end));var seat=coach.seats().stream().min(Comparator.comparingDouble(p->p.squaredDistanceTo(point))).orElseThrow();var goal=RailGeometry.rotate(seat,coach.getYaw()).add(coach.getEntityPos());if(!coach.getEntityWorld().getOtherEntities(coach,new Box(goal,goal).expand(.4),e->e instanceof RailSeatEntity&&e.hasPassengers()).isEmpty())return ActionResult.FAIL;
  var anchor=RailContent.SEAT.create((ServerWorld)coach.getEntityWorld(),SpawnReason.EVENT);if(anchor==null)return ActionResult.FAIL;anchor.owner=coach.getUuid();anchor.local=seat;anchor.setPosition(goal);coach.getEntityWorld().spawnEntity(anchor);if(!player.startRiding(anchor,true,false)){anchor.discard();return ActionResult.FAIL;}anchor.syncToTrain();return ActionResult.SUCCESS;
 }
}
