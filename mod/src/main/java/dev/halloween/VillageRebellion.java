package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** Persistent early rebellion: recruit, build a camp, then assault the king. */
final class VillageRebellion {
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageRebellion::tick);}
    static boolean startNear(ServerWorld w,ServerPlayerEntity player){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var team=memory.teams.stream().filter(t->player.squaredDistanceTo(Vec3d.ofCenter(t.anchor))<128*128)
            .min(Comparator.comparingDouble(t->player.squaredDistanceTo(Vec3d.ofCenter(t.anchor)))).orElse(null);
        if(team==null||team.revolt.stage!=0)return false;start(w,memory,team);return true;
    }
    private static void start(ServerWorld w,VillageMemory memory,VillageMemory.Team team){
        var r=team.revolt;r.stage=1;r.startedAt=w.getTimeOfDay();r.plan.clear();r.members.clear();r.index=0;r.camp=BlockPos.ORIGIN;memory.markDirty();
        announce(w,"message.halloween.rebellion_start");
    }
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<128*128))continue;
            var r=team.revolt;long day=w.getTimeOfDay();
            if(r.stage==0){
                if(r.nextAt==0){r.nextAt=day+25*24000L;memory.markDirty();}
                else if(day>=r.nextAt)start(w,memory,team);
                continue;
            }
            var all=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(128,48,128),v->v.isAlive()&&!v.isBaby());
            var king=all.stream().filter(RoyalProfession::isKing).min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(team.anchor)))).orElse(null);
            if(r.stage==1){
                if(king==null)continue;
                if(r.members.size()<3){
                    var recruit=all.stream().filter(v->!RoyalProfession.isKing(v)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                        &&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&!v.getCommandTags().contains("halloween_guard")
                        &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_rebel")&&!v.getCommandTags().contains("halloween_fighter"))
                        .min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(team.anchor)))).orElse(null);
                    if(recruit!=null){
                        recruit.setVillagerData(recruit.getVillagerData().withProfession(RebelProfession.ENTRY));recruit.setExperience(1);recruit.reinitializeBrain(w);
                        recruit.addCommandTag("halloween_rebel");recruit.setPersistent();r.members.add(recruit.getUuidAsString());memory.markDirty();
                    }
                    continue;
                }
                BlockPos site=findCamp(w,memory,team);
                if(site!=null){r.camp=site;r.plan.clear();r.plan.addAll(campPlan(site));r.index=0;r.stage=2;memory.markDirty();}
            }else if(r.stage==2){
                if(r.index>=r.plan.size()){
                    var max=r.camp.add(8,5,8);String key="rebel_camp:"+Long.toUnsignedString(team.village)+":"+r.camp.asLong();
                    if(memory.houses.stream().noneMatch(h->h.recordKey.equals(key)))memory.houses.add(new VillageMemory.House(r.camp,max,r.plan,w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"structure",key,List.of()));
                    if(day-r.startedAt<3*24000L)continue;
                    r.stage=4;memory.markDirty();continue;
                }
                buildNext(w,memory,r);
            }else if(r.stage==4){
                if(r.members.size()<15){
                    var recruit=all.stream().filter(v->!RoyalProfession.isKing(v)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                        &&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&!v.getCommandTags().contains("halloween_guard")
                        &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_rebel")&&!v.getCommandTags().contains("halloween_fighter"))
                        .min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(r.camp)))).orElse(null);
                    if(recruit==null&&w.getTime()%200==0){
                        for(int attempt=0;attempt<12;attempt++){
                            BlockPos spawn=r.camp.add(w.random.nextBetween(1,7),0,w.random.nextBetween(1,7));
                            if(!w.getBlockState(spawn).isAir()||!w.getBlockState(spawn.up()).isAir()||!w.getBlockState(spawn.down()).isSolidBlock(w,spawn.down()))continue;
                            recruit=net.minecraft.entity.EntityType.VILLAGER.create(w,net.minecraft.entity.SpawnReason.EVENT);if(recruit==null)break;
                            recruit.refreshPositionAndAngles(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,0,0);recruit.initialize(w,w.getLocalDifficulty(spawn),net.minecraft.entity.SpawnReason.EVENT,null);
                            if(!w.spawnEntity(recruit))recruit=null;break;
                        }
                    }
                    if(recruit!=null){recruit.setVillagerData(recruit.getVillagerData().withProfession(RebelProfession.ENTRY));recruit.setExperience(1);recruit.reinitializeBrain(w);recruit.addCommandTag("halloween_rebel");recruit.setPersistent();r.members.add(recruit.getUuidAsString());memory.markDirty();}
                }
                for(var rebel:members(w,r))if(rebel.squaredDistanceTo(Vec3d.ofCenter(r.camp))>100&&rebel.getNavigation().isIdle())rebel.getNavigation().startMovingTo(r.camp.getX()+4.5,r.camp.getY(),r.camp.getZ()+4.5,.6);
                if(day-r.startedAt<5*24000L||members(w,r).size()<15)continue;
                for(var rebel:members(w,r)){rebel.addCommandTag("halloween_wanted");rebel.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));rebel.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));rebel.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));}
                r.stage=3;r.startedAt=day;memory.markDirty();announce(w,"message.halloween.rebellion_attack");continue;
            }else if(r.stage==3){
                var rebels=members(w,r);if(rebels.isEmpty()){
                    if(r.members.isEmpty()){r.stage=0;r.nextAt=day+25*24000L;r.plan.clear();memory.markDirty();}
                    continue;
                }
                if(king==null){
                    if(!r.members.contains(team.royalKiller)){
                        r.stage=0;r.nextAt=day+25*24000L;r.plan.clear();r.members.clear();memory.markDirty();continue;
                    }
                    VillagerEntity ruler=rebels.getFirst();ruler.removeCommandTag("halloween_rebel");ruler.removeCommandTag("halloween_wanted");
                    ruler.setVillagerData(ruler.getVillagerData().withProfession(RoyalProfession.REBEL_ENTRY));ruler.setExperience(1);ruler.reinitializeBrain(w);
                    ruler.equipStack(EquipmentSlot.HEAD,new ItemStack(RoyalProfession.CROWN));ruler.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                    for(var rebel:rebels){rebel.removeCommandTag("halloween_wanted");rebel.removeCommandTag("halloween_rebel");}
                    team.electionStage=0;team.royalKiller="";r.stage=0;r.nextAt=day+25*24000L;r.plan.clear();r.members.clear();memory.markDirty();
                    for(var player:w.getPlayers())player.sendMessage(Text.translatable("message.halloween.rebellion_victory",ruler.getDisplayName()),false);continue;
                }
                if(day-r.startedAt>24000){for(var rebel:rebels){rebel.removeCommandTag("halloween_rebel");rebel.removeCommandTag("halloween_wanted");}
                    r.stage=0;r.nextAt=day+25*24000L;r.plan.clear();r.members.clear();memory.markDirty();continue;}
                for(var rebel:rebels){
                    var guard=all.stream().filter(v->v.getCommandTags().contains("halloween_guard")&&v.squaredDistanceTo(rebel)<100)
                        .min(Comparator.comparingDouble(rebel::squaredDistanceTo)).orElse(null);
                    var target=guard==null?king:guard;
                    if(rebel.squaredDistanceTo(target)>4){
                        rebel.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(target.getBlockPos(),.65f,1),60);
                        if(rebel.getNavigation().isIdle()||rebel.age%40==0)rebel.getNavigation().startMovingTo(target,.65);
                    }else{rebel.swingHand(Hand.MAIN_HAND);target.damage(w,w.getDamageSources().mobAttack(rebel),3);}
                }
            }
        }
    }
    private static List<VillagerEntity> members(ServerWorld w,VillageMemory.Revolt r){
        List<VillagerEntity> out=new ArrayList<>();for(String id:r.members)try{
            if(w.getEntity(UUID.fromString(id)) instanceof VillagerEntity v&&v.isAlive())out.add(v);
        }catch(IllegalArgumentException ignored){}return out;
    }
    private static BlockPos findCamp(ServerWorld w,VillageMemory memory,VillageMemory.Team team){
        for(int i=0;i<60;i++){
            double angle=w.random.nextDouble()*Math.PI*2;int dist=w.random.nextBetween(35,90);
            int x=team.anchor.getX()+(int)(Math.cos(angle)*dist),z=team.anchor.getZ()+(int)(Math.sin(angle)*dist);
            BlockPos min=new BlockPos(x,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z),max=min.add(8,5,8);
            if(!w.isChunkLoaded(new ChunkPos(min).toLong())||!w.getWorldBorder().contains(max))continue;
            if(memory.houses.stream().anyMatch(h->min.getX()-8<=h.max.getX()&&max.getX()+8>=h.min.getX()&&min.getZ()-8<=h.max.getZ()&&max.getZ()+8>=h.min.getZ()))continue;
            boolean clear=true;
            outer:for(int dx=0;dx<9;dx++)for(int dz=0;dz<9;dz++){
                BlockPos p=min.add(dx,0,dz);if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!(w.getBlockState(p.down()).isIn(net.minecraft.registry.tag.BlockTags.DIRT)||w.getBlockState(p.down()).isOf(Blocks.SAND))
                    ||!w.getBlockState(p.down()).isSolidBlock(w,p.down())){clear=false;break outer;}
                for(int y=0;y<=5;y++){var state=w.getBlockState(p.up(y));if(!state.isAir()&&!(y==0&&VillageExpansion.softPlant(state))){clear=false;break outer;}}
            }
            if(clear)return min;
        }return null;
    }
    private static List<VillageMemory.BlockRecord> campPlan(BlockPos origin){
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            if(x!=0&&x!=8&&z!=0&&z!=8)continue;if(z==0&&x>=3&&x<=5)continue;
            BlockState state=Blocks.COBBLESTONE.getDefaultState();
            for(int y=0;y<=3;y++)plan.put(origin.add(x,y,z),state);
            if((x==0||x==8)&&(z==0||z==8))plan.put(origin.add(x,4,z),Blocks.STONE_BRICK_WALL.getDefaultState());
        }
        for(int x=3;x<=5;x++)for(int y=1;y<=3;y++)plan.put(origin.add(x,y,8),(x==4||y==2?Blocks.WHITE_WOOL:Blocks.RED_WOOL).getDefaultState());
        for(int x:new int[]{1,7}){for(int y=1;y<=4;y++)plan.put(origin.add(x,y,1),Blocks.OAK_LOG.getDefaultState());
            plan.put(origin.add(x,4,2),Blocks.RED_WOOL.getDefaultState());plan.put(origin.add(x,3,2),Blocks.RED_WOOL.getDefaultState());}
        return plan.entrySet().stream().map(e->new VillageMemory.BlockRecord(e.getKey(),e.getValue())).toList();
    }
    private static void buildNext(ServerWorld w,VillageMemory memory,VillageMemory.Revolt r){
        var job=r.plan.get(r.index);BlockPos pos=job.pos();if(!w.isChunkLoaded(new ChunkPos(pos).toLong()))return;
        var old=w.getBlockState(pos);if(old.equals(job.state())){r.index++;memory.markDirty();return;}
        if(!old.isAir()&&!VillageExpansion.softPlant(old))return;
        var worker=members(w,r).stream().min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(pos)))).orElse(null);if(worker==null)return;
        if(worker.squaredDistanceTo(Vec3d.ofCenter(pos))>16){
            worker.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(pos,.5f,2),60);
            if(worker.getNavigation().isIdle()||worker.age%40==0)worker.getNavigation().startMovingTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,.5);return;
        }
        if(!w.getOtherEntities(null,new Box(pos),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())return;
        if(VillageExpansion.softPlant(old))w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
        if(!job.state().canPlaceAt(w,pos))return;
        if(w.setBlockState(pos,job.state(),Block.NOTIFY_ALL)){worker.swingHand(Hand.MAIN_HAND);r.index++;memory.markDirty();}
    }
    private static void announce(ServerWorld w,String key){for(var p:w.getPlayers())p.sendMessage(Text.translatable(key),false);}
    private VillageRebellion(){}
}
