package dev.halloween;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Local broadcast appliances: original animated CRT channels and original radio loops. */
final class IndustrialElectronics {
 static net.minecraft.block.entity.BlockEntityType<ApplianceEntity> APPLIANCE_TYPE;
 static Block TELEVISION,RADIO;static final SoundEvent[] STATIONS=new SoundEvent[3];
 static void register(){
  for(int i=0;i<STATIONS.length;i++){var id=Identifier.of("halloween","radio.station_"+i);STATIONS[i]=Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));}
  TELEVISION=block("television",false);RADIO=block("table_radio",true);
  APPLIANCE_TYPE=Registry.register(Registries.BLOCK_ENTITY_TYPE,Identifier.of("halloween","appliance"),
      net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(ApplianceEntity::new,TELEVISION,RADIO).build());
 }
 static Block block(String name,boolean radio){var id=Identifier.of("halloween",name);var key=RegistryKey.of(RegistryKeys.BLOCK,id);var block=Registry.register(Registries.BLOCK,key,new Appliance(AbstractBlock.Settings.create().registryKey(key).strength(1.5f).nonOpaque().sounds(BlockSoundGroup.WOOD).luminance(s->s.get(Appliance.ON)?radio?2:9:0),radio));
  var item=Registry.register(Registries.ITEM,id,new BlockItem(block,new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,id)).useBlockPrefixedTranslationKey()));ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));return block;
 }
 static class Appliance extends BlockWithEntity {
  static final com.mojang.serialization.MapCodec<Appliance> CODEC=
      com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i->i.group(
          createSettingsCodec(),com.mojang.serialization.Codec.BOOL.fieldOf("radio").forGetter(a->a.radio)).apply(i,Appliance::new));
  @Override protected com.mojang.serialization.MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
  @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
  @Override public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ApplianceEntity(pos,state);}
  @Override public <T extends net.minecraft.block.entity.BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
      World world,BlockState state,net.minecraft.block.entity.BlockEntityType<T> type){
   if(!world.isClient()||type!=APPLIANCE_TYPE)return null;
   return (w,p,s,entity)->{if(w.getTime()%5==0)ApplianceEntity.clientTick.accept((ApplianceEntity)entity);};
  }
  static final EnumProperty<Direction> FACING=Properties.HORIZONTAL_FACING;static final BooleanProperty ON=Properties.POWERED;static final IntProperty CHANNEL=IntProperty.of("channel",0,3);final boolean radio;
  Appliance(Settings s,boolean radio){super(s);this.radio=radio;setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(ON,false).with(CHANNEL,0));}
  @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,ON,CHANNEL);}
  @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());}
  @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return radio?Block.createCuboidShape(2,0,3,14,9,13):Block.createCuboidShape(0,0,2,16,14,14);}
  @Override protected ActionResult onUseWithItem(ItemStack stack,BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){return use(s,w,p,player);}
  @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){return use(s,w,p,player);}
  ActionResult use(BlockState state,World world,BlockPos pos,PlayerEntity player){if(world.isClient())return ActionResult.SUCCESS;
   var next=player.isSneaking()?state.cycle(ON):state.get(ON)?state.with(CHANNEL,(state.get(CHANNEL)+1)%(radio?3:4)):state.with(ON,true);
   world.setBlockState(pos,next,Block.NOTIFY_ALL);world.playSound(null,pos,SoundEvents.BLOCK_LEVER_CLICK,SoundCategory.BLOCKS,.35f,.8f+next.get(CHANNEL)*.08f);
   var channels=radio?new String[]{"Ретроэфир","Ночной джаз","Дорожная волна"}:new String[]{"Новости поселения","Испытательная таблица","Природа","Погода"};
   player.sendMessage(Text.literal((radio?"Радио":"Телевизор")+": "+(next.get(ON)?channels[next.get(CHANNEL)]:"выключен")+" · Shift + ПКМ — питание"),true);
   if(!radio&&next.get(ON))world.scheduleBlockTick(pos,this,1);return ActionResult.SUCCESS;
  }
  @Override protected void scheduledTick(BlockState state,ServerWorld world,BlockPos pos,net.minecraft.util.math.random.Random random){if(!state.get(ON))return;
   if(!radio&&state.get(CHANNEL)==0)world.playSound(null,pos,SoundEvents.ENTITY_VILLAGER_AMBIENT,SoundCategory.RECORDS,.20f,.75f);
   if(!radio)world.scheduleBlockTick(pos,this,240);
  }
 }
 private IndustrialElectronics(){}
}
