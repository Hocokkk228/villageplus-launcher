package dev.halloween;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.*;
import net.minecraft.util.math.*;
public final class RailTrackEntity extends BlockEntity {
 public List<Vec3d> points=List.of(new Vec3d(.5,0,.5),new Vec3d(.5,0,1),new Vec3d(.5,0,1.5),new Vec3d(.5,0,2),new Vec3d(.5,0,2.5),new Vec3d(.5,0,3),new Vec3d(.5,0,3.5),new Vec3d(.5,0,4),new Vec3d(.5,0,4.5));
 public RailTrackEntity(BlockPos p,BlockState state){super(RailContent.TRACK_ENTITY,p,state);}
 public void setPoints(List<Vec3d> points){this.points=List.copyOf(points);markDirty();if(world!=null&&!world.isClient())world.updateListeners(pos,getCachedState(),getCachedState(),3);}
 @Override protected void writeData(WriteView v){super.writeData(v);v.put("points",Vec3d.CODEC.listOf(),points);}
 @Override protected void readData(ReadView v){super.readData(v);points=v.read("points",Vec3d.CODEC.listOf()).orElse(points);}
 @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup){return createNbt(lookup);}
}
