package dev.halloween;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShapes;
public final class RailContent {
 public static Block TRACK,SLEEPER;public static Item PROFILE,HAMMER,TRAIN;public static BlockEntityType<RailTrackEntity> TRACK_ENTITY;public static EntityType<RailVehicleEntity> VEHICLE;public static EntityType<RailSeatEntity> SEAT;
 private static Identifier id(String name){return Identifier.of("halloween",name);}
 private static Item item(String name){var key=RegistryKey.of(RegistryKeys.ITEM,id(name));return Registry.register(Registries.ITEM,key,new Item(new Item.Settings().registryKey(key)));}
 public static void register(){
  RailDriver.register();
  var trackKey=RegistryKey.of(RegistryKeys.BLOCK,id("railway_track"));TRACK=Registry.register(Registries.BLOCK,trackKey,new RailTrackBlock(AbstractBlock.Settings.create().registryKey(trackKey).strength(3).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds()));
  var sleeperKey=RegistryKey.of(RegistryKeys.BLOCK,id("iron_sleeper"));SLEEPER=Registry.register(Registries.BLOCK,sleeperKey,new RailSleeperBlock(AbstractBlock.Settings.create().registryKey(sleeperKey).strength(3).sounds(BlockSoundGroup.METAL).nonOpaque()));
  for(String name:new String[]{"railway_track","iron_sleeper"}){var block=name.equals("railway_track")?TRACK:SLEEPER;var key=RegistryKey.of(RegistryKeys.ITEM,id(name));var item=Registry.register(Registries.ITEM,key,new BlockItem(block,new Item.Settings().registryKey(key).useBlockPrefixedTranslationKey()));ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));}
  TRACK_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id("railway_track"),FabricBlockEntityTypeBuilder.create(RailTrackEntity::new,TRACK).build());
  PROFILE=item("rail_profile");HAMMER=item("rail_hammer");
  var trainKey=RegistryKey.of(RegistryKeys.ITEM,id("steam_train"));TRAIN=Registry.register(Registries.ITEM,trainKey,new Item(new Item.Settings().registryKey(trainKey).maxCount(1)){
   @Override public net.minecraft.util.ActionResult useOnBlock(ItemUsageContext c){
    if(!c.getWorld().getBlockState(c.getBlockPos()).isOf(TRACK))return net.minecraft.util.ActionResult.PASS;
    if(!(c.getWorld() instanceof net.minecraft.server.world.ServerWorld w))return net.minecraft.util.ActionResult.SUCCESS;
    var network=RailwayNetwork.get(w);for(var line:network.lines)if(!line.spawned&&line.next>=(int)Math.ceil(line.path.length/16)){
     var last=VillageProjects.get(w).jobs.stream().filter(p->p.id.equals(RailwayNetwork.segmentId(line,line.next-1))).findFirst().orElse(null);
     if(last!=null&&last.done){RailwayNetwork.spawnTrain(w,line.path,-4,40,1);RailwayNetwork.spawnTrain(w,line.path,4,line.path.length-8,-1);line.spawned=true;network.markDirty();return net.minecraft.util.ActionResult.SUCCESS;}
    }
    if(c.getPlayer()!=null)c.getPlayer().sendMessage(net.minecraft.text.Text.literal("Для составов нужна достроенная линия. Для осмотра модели: /railway showcase"),false);
    return net.minecraft.util.ActionResult.FAIL;
   }
  });
  var vehicleKey=RegistryKey.of(RegistryKeys.ENTITY_TYPE,id("rail_vehicle"));VEHICLE=Registry.register(Registries.ENTITY_TYPE,vehicleKey,EntityType.Builder.<RailVehicleEntity>create(RailVehicleEntity::new,SpawnGroup.MISC).dimensions(16,6).maxTrackingRange(12).trackingTickInterval(1).build(vehicleKey));
  var seatKey=RegistryKey.of(RegistryKeys.ENTITY_TYPE,id("rail_seat"));SEAT=Registry.register(Registries.ENTITY_TYPE,seatKey,EntityType.Builder.<RailSeatEntity>create(RailSeatEntity::new,SpawnGroup.MISC).dimensions(.1f,.1f).maxTrackingRange(12).trackingTickInterval(1).build(seatKey));
  ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->{e.add(PROFILE);e.add(HAMMER);e.add(TRAIN);});
 }
}
