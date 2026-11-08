package dev.halloween;

import com.google.common.collect.ImmutableSet;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;

/** Production, delivery and issue move real stacks. No virtual stock is credited for a failed transfer. */
final class VillageEconomy {
    static final RegistryKey<VillagerProfession> TREASURER=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","treasurer"));
    private static RegistryEntry<VillagerProfession> ENTRY;
    private static RegistryEntry<VillagerProfession> LUMBERJACK, MINER, PORTER;
    private static final Map<UUID,BlockPos> CROPS=new HashMap<>();
    private record Employment(VillageWorkplaceIndex index,int population){}
    private static final Map<ServerWorld,Map<Long,Employment>> EMPLOYMENT=new WeakHashMap<>();
    private static final String[] STORAGE={"Оружие и щиты","Броня","Продовольствие","Материалы","Конское снаряжение","Запас"};
    static float attackDamage(VillagerEntity guard){if(guard.getMainHandStack().isOf(VillageItems.RAPIER))return 8.5f;if(guard.getMainHandStack().isOf(VillageItems.SABRE))return 9.5f;String n=Registries.ITEM.getId(guard.getMainHandStack().getItem()).getPath();return n.startsWith("wooden_")?3:n.startsWith("copper_")?5:n.startsWith("iron_")?6:4;}
    static void register(){
        var profession=Registry.register(Registries.VILLAGER_PROFESSION,TREASURER,new VillagerProfession(Text.translatable("entity.halloween.treasurer"),e->false,e->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN));
        ENTRY=Registries.VILLAGER_PROFESSION.getEntry(profession);
        var lumberjack=Registry.register(Registries.VILLAGER_PROFESSION,Identifier.of("halloween","lumberjack"),new VillagerProfession(Text.translatable("entity.halloween.lumberjack"),e->false,e->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_TOOLSMITH));
        LUMBERJACK=Registries.VILLAGER_PROFESSION.getEntry(lumberjack);
        var miner=Registry.register(Registries.VILLAGER_PROFESSION,Identifier.of("halloween","miner"),new VillagerProfession(Text.translatable("entity.halloween.miner"),e->false,e->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_TOOLSMITH));
        MINER=Registries.VILLAGER_PROFESSION.getEntry(miner);
        var porter=Registry.register(Registries.VILLAGER_PROFESSION,Identifier.of("halloween","factory_porter"),new VillagerProfession(Text.translatable("entity.halloween.factory_porter"),e->false,e->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN));
        PORTER=Registries.VILLAGER_PROFESSION.getEntry(porter);
        ServerTickEvents.END_WORLD_TICK.register(VillageEconomy::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(entity instanceof VillagerEntity v&&VillageDefense.isGuard(v)&&v.getOffHandStack().isOf(Items.SHIELD)&&source.getAttacker()!=null&&v.canSee(source.getAttacker())&&v.getRandom().nextInt(3)==0){
                v.getEntityWorld().playSound(null,v.getBlockPos(),SoundEvents.ITEM_SHIELD_BLOCK.value(),net.minecraft.sound.SoundCategory.NEUTRAL,1,1);return false;
            }return true;
        });
    }
    static BlockPos barrel(VillageEraData.Settlement s,int index){return VillageEraPlans.vaultPos(s.vault,14,index*2-5,0);}
    private static BarrelBlockEntity inventory(ServerWorld w,VillageEraData.Settlement s,int index){return s.vault!=null&&w.isChunkLoaded(new ChunkPos(barrel(s,index)).toLong())&&w.getBlockEntity(barrel(s,index)) instanceof BarrelBlockEntity b?b:null;}
    private static int insert(Inventory inv,ItemStack stack){
        if(inv==null)return 0;int initial=stack.getCount();
        for(int i=0;i<inv.size()&&!stack.isEmpty();i++){var current=inv.getStack(i);if(!current.isEmpty()&&ItemStack.areItemsAndComponentsEqual(current,stack)){int n=Math.min(stack.getCount(),Math.min(inv.getMaxCountPerStack(),current.getMaxCount())-current.getCount());if(n>0){current.increment(n);stack.decrement(n);}}}
        for(int i=0;i<inv.size()&&!stack.isEmpty();i++)if(inv.getStack(i).isEmpty())inv.setStack(i,stack.split(Math.min(stack.getCount(),Math.min(stack.getMaxCount(),inv.getMaxCountPerStack()))));
        if(initial!=stack.getCount())inv.markDirty();return initial-stack.getCount();
    }
    private static boolean take(ServerWorld w,VillageEraData.Settlement s,Item item,int count){
        int total=0;for(int k=0;k<6;k++){var inv=inventory(w,s,k);if(inv!=null)for(int i=0;i<inv.size();i++)if(inv.getStack(i).isOf(item))total+=inv.getStack(i).getCount();}if(total<count)return false;
        for(int k=0;k<6&&count>0;k++){var inv=inventory(w,s,k);if(inv==null)continue;for(int i=0;i<inv.size()&&count>0;i++)if(inv.getStack(i).isOf(item)){int n=Math.min(count,inv.getStack(i).getCount());inv.removeStack(i,n);count-=n;inv.markDirty();}}return true;
    }
    private static void store(ServerWorld w,VillageEraData.Settlement s,ItemStack stack){insert(inventory(w,s,category(stack)),stack);if(!stack.isEmpty())insert(inventory(w,s,5),stack);}
    private static int category(ItemStack stack){String name=Registries.ITEM.getId(stack.getItem()).getPath();if(name.contains("horse_armor")||stack.isOf(Items.SADDLE))return 4;if(name.endsWith("helmet")||name.endsWith("chestplate")||name.endsWith("leggings")||name.endsWith("boots"))return 1;if(name.contains("sword")||name.contains("spear")||stack.isOf(VillageItems.RAPIER)||stack.isOf(VillageItems.SABRE)||stack.getItem() instanceof MusketItem||stack.isOf(VillageItems.MUSKET_CARTRIDGE)||stack.isOf(Items.SHIELD)||stack.isOf(Items.CROSSBOW)||stack.isOf(Items.ARROW))return 0;if(stack.isOf(NavalCannons.FIELD_GUN)||stack.isOf(NavalCannons.BALL))return 0;if(stack.contains(DataComponentTypes.FOOD))return 2;return 3;}
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var data=VillageEraData.get(w);
        for(var team:List.copyOf(memory.teams)){
            if(w.getPlayers().stream().noneMatch(p->VillageEras.horizontal(p.getBlockPos(),team.anchor)<384*384))continue;
            var s=data.settlement(team);var people=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(384,96,384),v->v.isAlive()&&!v.isBaby()&&!(v instanceof StationResidentEntity)&&VillageEras.belongs(w,v,team.village));
            if(s.plantation!=null&&w.getTime()%100==0)for(int x=0;x<=25;x+=16)for(int z=0;z<=50;z+=16)VillageProjects.keepLoaded(w,s.plantation.add(x,0,z));
            long hour=Math.floorMod(w.getTimeOfDay(),24000),day=Math.floorDiv(w.getTimeOfDay(),24000);
            if(w.getTime()%100==0){
                var index=VillageWorkplaceIndex.get(w);var assignments=EMPLOYMENT.computeIfAbsent(w,k->new HashMap<>());var last=assignments.get(team.village);
                int population=people.stream().mapToInt(v->v.getUuid().hashCode()).sum();
                if(last==null||last.index()!=index||last.population()!=population){employ(w,team,s,people);assignments.put(team.village,new Employment(index,population));}
            }
            plantationWorkers(w,team,s,people);
            for(var guard:people)if(VillageDefense.isGuard(guard))starter(guard);
            boolean ready=s.vault!=null&&VillageProjects.get(w).complete(team.village,"treasury");
            VillagerEntity treasurer=null;
            if(ready){
                var factoryProject=VillageFactory.project(w,s.village);
                if(factoryProject!=null&&factoryProject.done)resourceWorker(w,team,s,people,"factory_porter",PORTER,Items.BUNDLE);
                treasurer=treasurer(w,team,s,people); resourceWorker(w,team,s,people,"lumberjack",LUMBERJACK,Items.IRON_AXE); resourceWorker(w,team,s,people,"miner",MINER,Items.IRON_PICKAXE); seed(w,s);manageTreasurer(w,s,treasurer,hour);
                if(hour>=1000&&hour<5000&&treasurer!=null&&!treasurer.isSleeping())issue(w,s,people,day);
            }
            for(var v:people){
                if(IndustrialPlants.owns(v)||RailWorks.owns(v)||NavalWorks.owns(v)||VillageConvoys.owns(v))continue;
                // Once a day, a working resident pays one real inventory item directly to the treasurer.
                String taxRole=VillageEras.profession(v);
                if(ready&&treasurer!=null&&v!=treasurer&&!v.isSleeping()&&!VillageTrials.busy(v)&&hour>=3000&&hour<10000
                    &&(VillageEras.PROFESSIONS.contains(taxRole)||taxRole.equals("janitor"))&&VillageEras.tag(v,"halloween_tax_day_",-1)!=day&&hasDelivery(v,false)){
                    if(payTax(w,s,v,treasurer,day))continue;
                }
                if(v==treasurer||VillageTasks.attendingCourt(v)||VillageDefense.isGuard(v)||RoyalProfession.isKing(v)||v.isAiDisabled()||VillageTrials.busy(v)||v.getCommandTags().contains("halloween_wanted")||v.getCommandTags().contains("halloween_prisoner")||v.getCommandTags().contains("halloween_surrender")||v.getCommandTags().contains("halloween_royal_assassin"))continue;
                if(VillageEras.profession(v).equals("factory_porter")){if(ready&&!v.isSleeping()&&hour>=1000&&hour<11000)haulFactory(w,s,v);continue;}
                String role=VillageEras.profession(v);if(role.equals("builder")||role.equals("deputy")||role.equals("treasurer"))continue;
                if(role.equals("lumberjack")||role.equals("miner")){if(v.isSleeping())continue; if(ready&&treasurer!=null&&hasDelivery(v,false)&&(carriedResources(v)>=8||hour>=10000||!v.getInventory().canInsert(new ItemStack(role.equals("miner")?Items.COAL:Items.OAK_LOG)))){deliver(w,s,v,treasurer,false);continue;} if(hour>=1000&&hour<10000){if(role.equals("miner"))mine(w,v);else chop(w,v);}continue;}
                boolean smith=role.equals("weaponsmith")||role.equals("armorer"),farmer=role.equals("farmer");
                if(ready&&treasurer!=null&&(!farmer&&hour>=10500&&hour<13000||farmer&&hour>=500&&hour<3000)&&hasDelivery(v,farmer)){
                    deliver(w,s,v,treasurer,farmer);continue;
                }
                if(v.isSleeping()||hour<1000||hour>=11000)continue;
                if(farmer){if(VillageEras.tag(v,"halloween_farm_repair_until_",0)<=w.getTime())farm(w,s,v,people);continue;}
                if(smith&&ready&&repairGolem(w,s,v))continue;
                var factory=VillageFactory.project(w,s.village);
                if(ready&&(role.equals("weaponsmith")||role.equals("armorer"))&&factory!=null&&factory.done){
                    if(w.getTime()-VillageEras.tag(v,"halloween_crafted_at_",-1000)>=400&&manufacture(w,s,v))VillageEras.putTag(v,"halloween_crafted_at_",w.getTime());
                    continue;
                }
                var site=v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
                if(site.isEmpty()||!site.get().dimension().equals(w.getRegistryKey()))continue;
                if(v.squaredDistanceTo(Vec3d.ofCenter(site.get().pos()))>9){if(smith)VillageProjects.approach(w,v,site.get().pos());continue;}
                if(w.getTime()-VillageEras.tag(v,"halloween_crafted_at_",-1000)<400)continue;
                if(smith&&ready){if(forge(w,s,v,role))VillageEras.putTag(v,"halloween_crafted_at_",w.getTime());}
                else if(ready&&produce(w,s,v,role)){VillageEras.putTag(v,"halloween_crafted_at_",w.getTime());}
                else if(v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.LAST_WORKED_AT_POI).orElse(-1L)>VillageEras.tag(v,"halloween_work_seen_",-1)){
                    long worked=v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.LAST_WORKED_AT_POI).orElse(-1L);VillageEras.putTag(v,"halloween_work_seen_",worked);VillageEras.putTag(v,"halloween_crafted_at_",w.getTime());VillageEras.work(w,v,role,3);
                }
            }
        }
    }
    private static void resourceWorker(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,List<VillagerEntity> people,String role,RegistryEntry<VillagerProfession> profession,Item tool){
        String key="worker_"+role+"_";
        var v=people.stream().filter(p->VillageEras.profession(p).equals(role)).findFirst().orElse(null);
        if(v==null&&s.xp.containsKey(key+"x")){
            int x=s.xp.get(key+"x"),z=s.xp.get(key+"z");
            // An unloaded worker must not be replaced on every economy tick.
            if(!w.isChunkLoaded(new ChunkPos(x,z).toLong()))return;
        }
        if(v==null){v=spawn(w,VillageEraPlans.vaultPos(s.vault,10,0,0));if(v==null)return;
            v.setVillagerData(v.getVillagerData().withProfession(profession));v.setExperience(1);v.reinitializeBrain(w);
            v.addCommandTag("halloween_era_village_"+team.village);v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(tool));people.add(v);
        }
        var chunk=new ChunkPos(v.getBlockPos());
        if(!Objects.equals(s.xp.get(key+"x"),chunk.x)||!Objects.equals(s.xp.get(key+"z"),chunk.z)){
            s.xp.put(key+"x",chunk.x);s.xp.put(key+"z",chunk.z);VillageEraData.get(w).markDirty();
        }
    }
    private static int carriedResources(VillagerEntity v){int n=0;for(int i=0;i<v.getInventory().size();i++)n+=v.getInventory().getStack(i).getCount();return n;}
    private static Item oreProduct(BlockState state){
        if(state.isOf(Blocks.GRAVEL))return Items.GRAVEL;
        if(state.isOf(Blocks.DIAMOND_ORE)||state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE))return Items.DIAMOND;
        if(state.isOf(Blocks.REDSTONE_ORE)||state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE))return Items.REDSTONE;
        if(state.isOf(Blocks.COAL_ORE)||state.isOf(Blocks.DEEPSLATE_COAL_ORE))return Items.COAL;
        if(state.isOf(Blocks.IRON_ORE)||state.isOf(Blocks.DEEPSLATE_IRON_ORE))return Items.RAW_IRON;
        if(state.isOf(Blocks.COPPER_ORE)||state.isOf(Blocks.DEEPSLATE_COPPER_ORE))return Items.RAW_COPPER;
        if(state.isOf(Blocks.GOLD_ORE)||state.isOf(Blocks.DEEPSLATE_GOLD_ORE))return Items.RAW_GOLD;
        return null;
    }
    static void mine(ServerWorld w,VillagerEntity v){
        if(w.getTime()-VillageEras.tag(v,"halloween_mine_at_",-1000)<60)return;
        BlockPos target=null;
        for(BlockPos p:BlockPos.iterateOutwards(v.getBlockPos(),12,6,12)){
            if(!w.isChunkLoaded(new ChunkPos(p).toLong())||oreProduct(w.getBlockState(p))==null||protectedWood(w,p))continue;
            boolean exposed=false;for(Direction d:Direction.values()){BlockPos q=p.offset(d);if(w.isChunkLoaded(new ChunkPos(q).toLong())&&w.getBlockState(q).isAir()){exposed=true;break;}}
            if(exposed){target=p.toImmutable();break;}
        }
        if(target==null){VillageEras.putTag(v,"halloween_mine_at_",w.getTime());return;}
        if(v.squaredDistanceTo(Vec3d.ofCenter(target))>9){VillageProjects.approach(w,v,target);return;}
        var product=oreProduct(w.getBlockState(target));if(product==null)return;var stack=new ItemStack(product);if(!v.getInventory().canInsert(stack))return;
        if(w.setBlockState(target,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL)){v.getInventory().addStack(stack);v.swingHand(Hand.MAIN_HAND);w.playSound(null,target,SoundEvents.BLOCK_STONE_BREAK,net.minecraft.sound.SoundCategory.BLOCKS,0.7f,1f);VillageEras.putTag(v,"halloween_mine_at_",w.getTime());VillageEras.work(w,v,"miner",2);}
    }
    private static boolean protectedWood(ServerWorld w,BlockPos p){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        return VillageProjects.pending(w,p)||memory.houses.stream().anyMatch(h->p.getX()>=h.min.getX()-1&&p.getX()<=h.max.getX()+1&&p.getY()>=h.min.getY()-1&&p.getY()<=h.max.getY()+1&&p.getZ()>=h.min.getZ()-1&&p.getZ()<=h.max.getZ()+1);
    }
    private static final String FELLED="halloween_felled_";
    private static List<BlockPos> treeLogs(ServerWorld w,BlockPos root){
        var logs=new ArrayList<BlockPos>();var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();queue.add(root.up());
        while(!queue.isEmpty()){
            BlockPos p=queue.removeFirst();if(!seen.add(p))continue;
            if(Math.abs(p.getX()-root.getX())>5||Math.abs(p.getZ()-root.getZ())>5||p.getY()<=root.getY()||p.getY()>root.getY()+24||!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
            if(!w.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS))continue;
            if(protectedWood(w,p)||logs.size()>=128)return List.of();
            logs.add(p);for(Direction d:Direction.values())queue.add(p.offset(d));
        }return logs;
    }
    static void chop(ServerWorld w,VillagerEntity v){
        String cargo=FELLED+v.getUuidAsString();
        var drops=w.getEntitiesByClass(ItemEntity.class,new Box(v.getBlockPos()).expand(24),e->e.isAlive()&&e.getCommandTags().contains(cargo));
        drops.sort(Comparator.comparingDouble(v::squaredDistanceTo));
        for(var drop:drops){
            if(!v.getInventory().canInsert(drop.getStack()))return;
            if(v.squaredDistanceTo(drop)>9){VillageProjects.approach(w,v,drop.getBlockPos());return;}
            var rest=v.getInventory().addStack(drop.getStack().copy());drop.setStack(rest);if(rest.isEmpty())drop.discard();return;
        }
        if(w.getTime()-VillageEras.tag(v,"halloween_chop_at_",-1000)<20)return;
        long saved=VillageEras.tag(v,"halloween_tree_",Long.MIN_VALUE);
        BlockPos root=saved==Long.MIN_VALUE?null:BlockPos.fromLong(saved);
        if(root!=null&&(!w.isChunkLoaded(new ChunkPos(root).toLong())||!w.getBlockState(root.up()).isIn(net.minecraft.registry.tag.BlockTags.LOGS)||protectedWood(w,root)))root=null;
        if(root==null){
            VillageEras.putTag(v,"halloween_felling_since_",-1);
            for(BlockPos p:BlockPos.iterateOutwards(v.getBlockPos(),12,6,12)){
                if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!w.getBlockState(p).isIn(net.minecraft.registry.tag.BlockTags.LOGS)||!w.getBlockState(p.down()).isIn(net.minecraft.registry.tag.BlockTags.DIRT)||protectedWood(w,p))continue;
                var logs=treeLogs(w,p);if(logs.isEmpty())continue;
                boolean leaves=false;for(var log:logs){for(Direction d:Direction.values())if(w.getBlockState(log.offset(d)).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)){leaves=true;break;}if(leaves)break;}
                if(leaves){root=p.toImmutable();VillageEras.putTag(v,"halloween_tree_",root.asLong());break;}
            }
        }
        VillageEras.putTag(v,"halloween_chop_at_",w.getTime());
        if(root==null)return;
        BlockPos cut=root.up();
        if(v.squaredDistanceTo(Vec3d.ofCenter(cut))>9){VillageEras.putTag(v,"halloween_felling_since_",-1);VillageProjects.approach(w,v,root);return;}
        VillageTasks.hold(v);v.getNavigation().stop();v.getLookControl().lookAt(cut.getX()+.5,cut.getY()+.5,cut.getZ()+.5);v.swingHand(Hand.MAIN_HAND);
        long started=VillageEras.tag(v,"halloween_felling_since_",-1);if(started<0){started=w.getTime();VillageEras.putTag(v,"halloween_felling_since_",started);}
        int progress=(int)Math.min(9,(w.getTime()-started)*10/160);w.setBlockBreakingInfo(v.getId(),cut,progress);
        w.playSound(null,cut,SoundEvents.BLOCK_WOOD_HIT,net.minecraft.sound.SoundCategory.BLOCKS,.7f,1f);
        if(w.getTime()-started<160)return;
        var logs=treeLogs(w,root);w.setBlockBreakingInfo(v.getId(),cut,-1);
        for(var log:logs){
            var state=w.getBlockState(log);var item=new ItemEntity(w,log.getX()+.5,log.getY()+.3,log.getZ()+.5,new ItemStack(state.getBlock()));item.addCommandTag(cargo);item.setPickupDelay(10);
            if(w.spawnEntity(item))w.setBlockState(log,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        VillageEras.putTag(v,"halloween_tree_",Long.MIN_VALUE);VillageEras.putTag(v,"halloween_felling_since_",-1);
        w.playSound(null,cut,SoundEvents.BLOCK_WOOD_BREAK,net.minecraft.sound.SoundCategory.BLOCKS,1f,.7f);VillageEras.work(w,v,"lumberjack",logs.size());
    }
    private static void plantationWorkers(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,List<VillagerEntity> people){
        if(s.plantation==null||!VillageProjects.get(w).complete(team.village,"plantation"))return;
        VillageEraPlans.rebalanceComposters(w,team,s);
        var farmers=new ArrayList<>(people.stream().filter(v->VillageEras.profession(v).equals("farmer")&&!VillageTrials.busy(v)&&!v.getCommandTags().contains("halloween_prisoner")).sorted(Comparator.comparing(VillagerEntity::getUuid)).limit(10).toList());
        if(farmers.size()<10&&w.getTime()%100==0){var v=spawn(w,s.plantation.up().add(1,0,-2));if(v!=null){v.setVillagerData(v.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.get(VillagerProfession.FARMER))));v.setExperience(1);v.reinitializeBrain(w);v.addCommandTag("halloween_era_village_"+team.village);farmers.add(v);}}
        for(int i=0;i<farmers.size();i++){var v=farmers.get(i);VillageEras.putTag(v,"halloween_plantation_slot_",i);v.getBrain().remember(MemoryModuleType.JOB_SITE,GlobalPos.create(w.getRegistryKey(),(VillageProjects.get(w).complete(team.village,"plantation")&&VillageProjects.get(w).jobs.stream().anyMatch(p->p.id.equals("plantation_composters_v2:"+team.village)&&p.done)?VillageEraPlans.plantationComposter(s.plantation,i):s.plantation.add(i*2+2,1,-1))));}
    }
    private static void starter(VillagerEntity v){
        if(v.getCommandTags().contains("halloween_era_equipment"))return;v.stopRiding();v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_SWORD));v.equipStack(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))v.equipStack(slot,ItemStack.EMPTY);v.addCommandTag("halloween_era_equipment");
    }
    private static void employ(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,List<VillagerEntity> people){
        Map<String,Integer> counts=new HashMap<>();for(var v:people)counts.merge(VillageEras.profession(v),1,Integer::sum);
        int farmers=s.plantation==null?3:10;
        String[] essential={"farmer","weaponsmith","armorer","toolsmith","mason","fletcher","librarian","butcher","fisherman","shepherd","leatherworker","cartographer","cleric"};int assigned=0;
        for(var v:people){
            String role=VillageEras.profession(v);if(!role.equals("none")&&!role.equals("nitwit")){
                if(VillageEras.PROFESSIONS.contains(role)&&!role.equals("builder")&&!role.equals("guard")&&!role.equals("treasurer")&&v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE).isEmpty()&&!station(w,v,role))VillageWorkplaces.plan(w,team,role,workstation(role));
                continue;
            }
            if(v.getCommandTags().contains("halloween_prisoner")||v.getCommandTags().contains("halloween_surrender")||VillageTrials.busy(v))continue;
            String chosen=counts.getOrDefault("farmer",0)<farmers?"farmer":Arrays.stream(essential).min(Comparator.comparingInt(r->counts.getOrDefault(r,0))).orElse("farmer");
            if(!station(w,v,chosen)){VillageWorkplaces.plan(w,team,chosen,workstation(chosen));continue;}
            var site=v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE).orElseThrow();
            var profession=Registries.VILLAGER_PROFESSION.get(Identifier.of("minecraft",chosen));v.setVillagerData(v.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(profession)));v.setExperience(1);v.reinitializeBrain(w);v.getBrain().remember(MemoryModuleType.JOB_SITE,site);counts.merge(chosen,1,Integer::sum);if(++assigned>=2)break;
        }
        if(s.plantation!=null&&VillageProjects.get(w).complete(team.village,"plantation")&&counts.getOrDefault("farmer",0)<10&&w.getTime()%200==0){
            var v=spawn(w,s.plantation.up().add(1,0,-1));if(v!=null){v.setVillagerData(v.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.get(VillagerProfession.FARMER))));v.setExperience(1);v.reinitializeBrain(w);v.addCommandTag("halloween_era_village_"+team.village);station(w,v,"farmer");}
        }
    }
    static Block workstation(String role){return switch(role){case "farmer"->Blocks.COMPOSTER;case "armorer"->Blocks.BLAST_FURNACE;case "weaponsmith"->Blocks.GRINDSTONE;case "toolsmith"->Blocks.SMITHING_TABLE;case "mason"->Blocks.STONECUTTER;case "fletcher"->Blocks.FLETCHING_TABLE;case "butcher"->Blocks.SMOKER;case "fisherman"->Blocks.BARREL;case "shepherd"->Blocks.LOOM;case "leatherworker"->Blocks.CAULDRON;case "cartographer"->Blocks.CARTOGRAPHY_TABLE;case "cleric"->Blocks.BREWING_STAND;default->Blocks.LECTERN;};}
    static boolean station(ServerWorld w,VillagerEntity v,String role){
        var block=workstation(role);BlockPos chosen=null;
        Set<BlockPos> claimed=new HashSet<>();
        for(var other:w.getEntitiesByClass(VillagerEntity.class,new Box(v.getBlockPos()).expand(384,96,384),e->e!=v&&e.isAlive()))
            other.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE).filter(site->site.dimension().equals(w.getRegistryKey())).ifPresent(site->claimed.add(site.pos()));
        Set<BlockPos> built=VillageWorkplaceIndex.get(w).sites.getOrDefault(block,Set.of());
        chosen=built.stream().filter(p->p.isWithinDistance(v.getBlockPos(),192)&&!claimed.contains(p)&&!VillageProjects.pending(w,p)
            &&w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).isOf(block))
            .min(Comparator.comparingDouble(p->p.getSquaredDistance(v.getBlockPos()))).orElse(null);
        if(chosen==null)return false;
        v.getBrain().remember(MemoryModuleType.JOB_SITE,GlobalPos.create(w.getRegistryKey(),chosen));return true;
    }

    private static VillagerEntity spawn(ServerWorld w,BlockPos near){
        for(BlockPos p:BlockPos.iterateOutwards(near,5,3,5))if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()&&w.getBlockState(p.down()).isSolidBlock(w,p.down())&&w.getOtherEntities(null,new Box(p).stretch(0,1,0),e->e instanceof LivingEntity&&e.isAlive()).isEmpty()){
            var v=EntityType.VILLAGER.create(w,SpawnReason.EVENT);if(v==null)return null;v.refreshPositionAndAngles(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);v.initialize(w,w.getLocalDifficulty(p),SpawnReason.EVENT,null);v.setPersistent();return w.spawnEntity(v)?v:null;
        }return null;
    }
    static void populateHouse(ServerWorld w,VillageProjects.Project p){
        var beds=p.plan.stream().filter(c->c.after().getBlock() instanceof BedBlock&&c.after().get(BedBlock.PART)==net.minecraft.block.enums.BedPart.HEAD).toList();
        boolean sector=IndustrialCityGrowth.panelProject(p.id);
        boolean panel=sector||p.id.startsWith("industrial_district:")&&p.id.contains(":panel_");
        for(int i=0;i<2;i++){
            BlockPos home=beds.isEmpty()?p.min.up():beds.get(panel&&i==1?beds.size()-1:i%beds.size()).pos();
            var v=spawn(w,home);
            if(v!=null){
                v.addCommandTag("halloween_era_village_"+p.village);
                if(panel){
                    v.addCommandTag("halloween_panel_resident");
                    VillageIndustrial.panelNavigation(v);
                    if(sector&&i==0&&p.id.endsWith(":panel_a"))
                        v.setVillagerData(v.getVillagerData().withProfession(BuilderProfession.ENTRY));
                }
                v.getBrain().remember(MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),home));
            }
        }
    }
    private static VillagerEntity treasurer(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,List<VillagerEntity> people){
        VillagerEntity v=null;try{if(w.getEntity(UUID.fromString(s.vault.treasurer)) instanceof VillagerEntity found&&found.isAlive())v=found;}catch(IllegalArgumentException ignored){}
        if(v!=null)return v;
        v=people.stream().filter(p->p.getVillagerData().profession().matchesKey(TREASURER)).findFirst().orElse(null);
        if(v==null)v=spawn(w,VillageEraPlans.vaultPos(s.vault,10,0,0));if(v==null)return null;
        v.setVillagerData(v.getVillagerData().withProfession(ENTRY));v.setExperience(1);v.reinitializeBrain(w);v.setCustomName(Text.literal("Казначей "+s.name));v.addCommandTag("halloween_era_village_"+team.village);
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        final VillagerEntity treasuryWorker=v;var home=memory.houses.stream().filter(h->h.kind.equals("house")&&VillageEras.team(w,h.min)==team).flatMap(h->h.blueprint.stream()).filter(r->r.state().getBlock() instanceof BedBlock&&r.state().get(BedBlock.PART)==net.minecraft.block.enums.BedPart.HEAD).min(Comparator.comparingDouble(r->treasuryWorker.squaredDistanceTo(Vec3d.ofCenter(r.pos())))).map(VillageMemory.BlockRecord::pos).orElse(VillageEraPlans.vaultPos(s.vault,13,4,0));
        v.getBrain().remember(MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),home));s.vault.treasurer=v.getUuidAsString();VillageEraData.get(w).markDirty();return v;
    }
    private static void seed(ServerWorld w,VillageEraData.Settlement s){
        if(s.vault.seeded)return;for(int i=0;i<6;i++)if(inventory(w,s,i)==null)return;
        for(var item:List.of(Items.IRON_INGOT,Items.COPPER_INGOT,Items.OAK_PLANKS))for(int i=0;i<(item==Items.OAK_PLANKS?3:6);i++)store(w,s,new ItemStack(item,64));
        for(var item:List.of(Items.COBBLESTONE,Items.CLAY_BALL,Items.PAPER,Items.LEATHER,Items.BEEF,Items.COD,Items.WHITE_WOOL,Items.REDSTONE,Items.FLINT))store(w,s,new ItemStack(item,64));
        store(w,s,new ItemStack(Items.SADDLE));store(w,s,new ItemStack(Items.SADDLE));
        s.vault.seeded=true;VillageEraData.get(w).markDirty();
    }
    static void manageTreasurer(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v,long hour){
        if(v==null||VillageTasks.attendingCourt(v))return;
        if(hour>=13000&&hour<23000&&!VillageTrials.busy(v)){
            var home=v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME);if(home.isPresent()){
                BlockPos bed=home.get().pos();if(v.squaredDistanceTo(Vec3d.ofCenter(bed))<4&&w.getBlockState(bed).getBlock() instanceof BedBlock){if(!v.isSleeping())v.sleep(bed);}else VillageProjects.approach(w,v,bed);
            }return;
        }
        if(v.isSleeping())v.wakeUp();VillageTasks.hold(v);BlockPos desk=VillageEraPlans.vaultPos(s.vault,10,0,0);
        // Carry one inventory stack at a time to its category barrel; keep leftovers if full.
        for(int slot=0;slot<v.getInventory().size();slot++){
            var carried=v.getInventory().getStack(slot);if(carried.isEmpty())continue;
            int category=category(carried), destination=hasSpace(inventory(w,s,category),carried)?category:hasSpace(inventory(w,s,5),carried)?5:-1;
            if(destination<0)continue;
            BlockPos barrelPos=barrel(s,destination);
            if(v.squaredDistanceTo(Vec3d.ofCenter(barrelPos))>6.25){VillageProjects.approach(w,v,barrelPos);return;}
            int moved=insert(inventory(w,s,destination),carried);
            if(moved>0){v.getInventory().markDirty();v.swingHand(Hand.MAIN_HAND);VillageEras.work(w,v,"treasurer",1);}return;
        }
        var deliveries=w.getEntitiesByClass(ItemEntity.class,new Box(desk).expand(20),e->e.isAlive()&&e.getCommandTags().contains("halloween_delivery_"+s.village));
        deliveries.sort(Comparator.comparingDouble(v::squaredDistanceTo));
        for(var item:deliveries){
            if(!v.getInventory().canInsert(item.getStack()))continue;
            if(v.squaredDistanceTo(item)>4){VillageProjects.approach(w,v,item.getBlockPos());return;}
            ItemStack remaining=v.getInventory().addStack(item.getStack().copy());
            int accepted=item.getStack().getCount()-remaining.getCount();
            if(accepted>0){item.setStack(remaining);if(remaining.isEmpty())item.discard();v.getInventory().markDirty();v.swingHand(Hand.MAIN_HAND);return;}
        }
        if(v.squaredDistanceTo(Vec3d.ofCenter(desk))>4)VillageProjects.approach(w,v,desk);else v.getNavigation().stop();
    }
    private static boolean hasSpace(Inventory inv,ItemStack stack){
        if(inv==null)return false;
        for(int i=0;i<inv.size();i++){var existing=inv.getStack(i);if(existing.isEmpty()||ItemStack.areItemsAndComponentsEqual(existing,stack)&&existing.getCount()<Math.min(inv.getMaxCountPerStack(),existing.getMaxCount()))return true;}
        return false;
    }

    private static boolean payTax(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v,VillagerEntity treasurer,long day){
        if(treasurer.isSleeping())return false;
        if(v.squaredDistanceTo(treasurer)>9){VillageTasks.hold(v);VillageProjects.approach(w,v,treasurer.getBlockPos());return true;}
        for(int slot=0;slot<v.getInventory().size();slot++){
            var stack=v.getInventory().getStack(slot);if(stack.isEmpty())continue;
            var one=stack.split(1);var dropped=new ItemEntity(w,treasurer.getX(),treasurer.getY()+0.5,treasurer.getZ(),one);
            dropped.setPickupDelay(32767);dropped.addCommandTag("halloween_delivery_"+s.village);if(!w.spawnEntity(dropped)){stack.increment(1);return false;}
            v.getInventory().markDirty();v.swingHand(Hand.MAIN_HAND);VillageEras.putTag(v,"halloween_tax_day_",day);return true;
        }
        return false;
    }
    private static boolean hasDelivery(VillagerEntity v,boolean food){for(int i=0;i<v.getInventory().size();i++){var stack=v.getInventory().getStack(i);if(!stack.isEmpty()&&(!food||stack.contains(DataComponentTypes.FOOD)||stack.isOf(Items.WHEAT)))return true;}return false;}
    static void deliver(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v,VillagerEntity treasurer,boolean food){
        if(treasurer.isSleeping())return;VillageTasks.hold(v);if(v.squaredDistanceTo(treasurer)>9){VillageProjects.approach(w,v,treasurer.getBlockPos());return;}
        for(int i=0;i<v.getInventory().size();i++){var stack=v.getInventory().getStack(i);if(stack.isEmpty()||food&&!stack.contains(DataComponentTypes.FOOD)&&!stack.isOf(Items.WHEAT))continue;
            if(!hasSpace(inventory(w,s,category(stack)),stack)&&!hasSpace(inventory(w,s,5),stack))continue;
            ItemStack delivery=v.getInventory().removeStack(i);var item=new ItemEntity(w,treasurer.getX(),treasurer.getY()+.5,treasurer.getZ(),delivery);item.setPickupDelay(32767);item.addCommandTag("halloween_delivery_"+s.village);
            if(!w.spawnEntity(item))v.getInventory().setStack(i,delivery);else v.swingHand(Hand.MAIN_HAND);
        }
    }
    private static boolean produce(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v,String role){
        Item input,output;int cost=1,count=1;
        switch(role){
            case "toolsmith"->{input=s.era>=2?Items.IRON_INGOT:Items.COBBLESTONE;output=s.era>=2?Items.IRON_PICKAXE:Items.STONE_PICKAXE;cost=3;}
            case "mason"->{input=s.era>=2?Items.CLAY_BALL:Items.COBBLESTONE;output=s.era>=2?Items.BRICKS:Items.STONE_BRICKS;cost=4;count=4;}
            case "fletcher"->{input=Items.FLINT;output=Items.ARROW;count=16;}
            case "butcher"->{input=Items.BEEF;output=Items.COOKED_BEEF;cost=2;count=2;}
            case "fisherman"->{input=Items.COD;output=Items.COOKED_COD;cost=2;count=2;}
            case "shepherd"->{input=Items.WHITE_WOOL;output=s.era>=2?Items.WHITE_BANNER:Items.WHITE_CARPET;cost=2;count=s.era>=2?1:3;}
            case "leatherworker"->{input=Items.LEATHER;output=s.era>=2?Items.LEATHER_HORSE_ARMOR:Items.LEATHER_HELMET;cost=s.era>=2?7:5;}
            case "librarian"->{input=Items.PAPER;output=s.era>=2?Items.BOOKSHELF:Items.BOOK;cost=s.era>=2?9:3;}
            case "cartographer"->{input=Items.PAPER;output=s.era>=2?Items.COMPASS:Items.MAP;cost=8;}
            case "cleric"->{input=Items.REDSTONE;output=s.era>=2?Items.GLOWSTONE:Items.GLOWSTONE_DUST;cost=2;}
            default->{return false;}
        }
        ItemStack stack=new ItemStack(output,count);if(!v.getInventory().canInsert(stack)||!take(w,s,input,cost))return false;
        ItemStack remainder=v.getInventory().addStack(stack);if(!remainder.isEmpty())store(w,s,remainder);
        v.swingHand(Hand.MAIN_HAND);VillageEras.work(w,v,role,5);return true;
    }
    static void haulFactory(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v){
        var base=VillageFactory.base(w,s.village);if(base==null||!VillageFactory.supplyReady(w,s.village))return;
        var inputs=base.add(3,1,7);var outputs=base.add(11,1,7);
        if(!(w.getBlockEntity(inputs) instanceof net.minecraft.inventory.Inventory in)||!(w.getBlockEntity(outputs) instanceof net.minecraft.inventory.Inventory out))return;
        for(int i=0;i<v.getInventory().size();i++){
            var cargo=v.getInventory().getStack(i);if(cargo.isEmpty())continue;
            boolean finished=isFactoryProduct(cargo);
            var target=finished?barrel(s,category(cargo)):inputs;
            if(v.squaredDistanceTo(Vec3d.ofCenter(target))>9){VillageProjects.approach(w,v,target);return;}
            if(finished)store(w,s,cargo);else insert(in,cargo);v.getInventory().markDirty();return;
        }
        if(!out.isEmpty()&&VillageEras.tag(v,"halloween_porter_supply_turn_",0)==0){
            if(v.squaredDistanceTo(Vec3d.ofCenter(outputs))>9){VillageProjects.approach(w,v,outputs);return;}
            for(int i=0;i<out.size();i++)if(!out.getStack(i).isEmpty()){v.getInventory().addStack(out.removeStack(i,16));out.markDirty();VillageEras.putTag(v,"halloween_porter_supply_turn_",1);return;}
        }
        var materials=new LinkedHashSet<Item>(List.of(Items.RAW_IRON,Items.RAW_GOLD,Items.RAW_COPPER,Items.COAL,Items.IRON_INGOT,Items.STICK,Items.IRON_NUGGET,Items.GUNPOWDER,Items.PAPER,Items.SUGAR_CANE,Items.REDSTONE,Items.GRAVEL,Items.FLINT,Items.DIAMOND,Items.GOLD_INGOT));
        for(int k=0;k<6;k++){var stock=inventory(w,s,k);if(stock!=null)for(int i=0;i<stock.size();i++){var stack=stock.getStack(i);if(stack.isIn(net.minecraft.registry.tag.ItemTags.LOGS)||stack.isIn(net.minecraft.registry.tag.ItemTags.PLANKS))materials.add(stack.getItem());}}
        for(Item material:materials){
            if(in.count(material)>=16)continue;
            int available=0;for(int k=0;k<6;k++){var stock=inventory(w,s,k);if(stock!=null)available+=stock.count(material);}
            if(available==0)continue;
            var source=barrel(s,3);if(v.squaredDistanceTo(Vec3d.ofCenter(source))>9){VillageProjects.approach(w,v,source);return;}
            int n=Math.min(16,available);var stack=new ItemStack(material,n);if(v.getInventory().canInsert(stack)&&take(w,s,material,n)){v.getInventory().addStack(stack);VillageEras.putTag(v,"halloween_porter_supply_turn_",0);}return;
        }
        if(!out.isEmpty()){VillageEras.putTag(v,"halloween_porter_supply_turn_",0);if(v.squaredDistanceTo(Vec3d.ofCenter(outputs))>9){VillageProjects.approach(w,v,outputs);return;}for(int i=0;i<out.size();i++)if(!out.getStack(i).isEmpty()){v.getInventory().addStack(out.removeStack(i,16));out.markDirty();VillageEras.putTag(v,"halloween_porter_supply_turn_",1);return;}}
    }
    private static void moveOne(net.minecraft.inventory.Inventory from,net.minecraft.inventory.Inventory to,Item item,int slot){
        for(int i=0;i<from.size();i++)if(from.getStack(i).isOf(item)){to.setStack(slot,from.removeStack(i,1));from.markDirty();to.markDirty();return;}
    }
    record FactoryRecipe(Item product,int count,Map<Item,Integer> ingredients){}
    static FactoryRecipe factoryRecipe(String role,long batch){
        if(role.equals("armorer"))return switch(Math.floorMod(batch,4)){
            case 0->new FactoryRecipe(Items.DIAMOND_HELMET,1,Map.of(Items.DIAMOND,5));
            case 1->new FactoryRecipe(Items.DIAMOND_CHESTPLATE,1,Map.of(Items.DIAMOND,8));
            case 2->new FactoryRecipe(Items.DIAMOND_LEGGINGS,1,Map.of(Items.DIAMOND,7));
            default->new FactoryRecipe(Items.DIAMOND_BOOTS,1,Map.of(Items.DIAMOND,4));
        };
        if(Math.floorMod(batch,16)==8)return new FactoryRecipe(NavalCannons.BALL,8,Map.of(Items.IRON_INGOT,5));
        if(Math.floorMod(batch,16)==12)return new FactoryRecipe(NavalCannons.FIELD_GUN,1,Map.of(Items.IRON_INGOT,12,Items.STICK,8,Items.COAL,3,Items.GOLD_INGOT,1));
        return switch(Math.floorMod(batch,8)){
            case 0->new FactoryRecipe(VillageItems.MUSKET,1,Map.of(Items.IRON_INGOT,3,Items.STICK,2,Items.COAL,1));
            case 2->new FactoryRecipe(VillageItems.REVOLVER,1,Map.of(Items.IRON_INGOT,4,Items.STICK,1,Items.GOLD_INGOT,1));
            case 4->new FactoryRecipe(VillageItems.RAPIER,1,Map.of(Items.IRON_INGOT,2,Items.STICK,1,Items.GOLD_INGOT,1));
            case 6->new FactoryRecipe(VillageItems.SABRE,1,Map.of(Items.IRON_INGOT,3,Items.STICK,1,Items.GOLD_INGOT,1));
            default->new FactoryRecipe(VillageItems.MUSKET_CARTRIDGE,4,Map.of(Items.IRON_NUGGET,1,Items.GUNPOWDER,1,Items.PAPER,1));
        };
    }
    static boolean isFactoryProduct(ItemStack stack){return stack.isOf(NavalCannons.FIELD_GUN)||stack.isOf(NavalCannons.BALL)||stack.getItem() instanceof MusketItem||stack.isOf(VillageItems.RAPIER)||stack.isOf(VillageItems.SABRE)||stack.isOf(VillageItems.MUSKET_CARTRIDGE)||Registries.ITEM.getId(stack.getItem()).getPath().startsWith("diamond_");}
    static boolean supplyRecipe(Inventory stock,Item product,int count,Map<Item,Integer> recipe){
        for(var ingredient:recipe.entrySet())if(stock.count(ingredient.getKey())<ingredient.getValue())return false;
        int space=0;var result=new ItemStack(product,count);for(int i=0;i<stock.size();i++){var old=stock.getStack(i);if(old.isEmpty())space+=result.getMaxCount();else if(ItemStack.areItemsAndComponentsEqual(old,result))space+=Math.max(0,result.getMaxCount()-old.getCount());}if(space<count)return false;
        for(var ingredient:recipe.entrySet()){int remaining=ingredient.getValue();for(int i=0;i<stock.size()&&remaining>0;i++)if(stock.getStack(i).isOf(ingredient.getKey())){int n=Math.min(remaining,stock.getStack(i).getCount());stock.removeStack(i,n);remaining-=n;}}
        insert(stock,result);stock.markDirty();return true;
    }
    static boolean processFactorySupplies(Inventory stock){
        int planks=0;for(int i=0;i<stock.size();i++)if(stock.getStack(i).isIn(net.minecraft.registry.tag.ItemTags.PLANKS))planks+=stock.getStack(i).getCount();
        if(planks<16)for(int i=0;i<stock.size();i++)if(stock.getStack(i).isIn(net.minecraft.registry.tag.ItemTags.LOGS)){var wood=Registries.ITEM.getId(stock.getStack(i).getItem());String species=wood.getPath().replaceFirst("^stripped_","").replaceFirst("_(log|wood|stem|hyphae)$","");var boards=Registries.ITEM.get(Identifier.of(wood.getNamespace(),species+"_planks"));if(boards==Items.AIR)continue;return supplyRecipe(stock,boards,4,Map.of(stock.getStack(i).getItem(),1));}
        if(stock.count(Items.STICK)<16)for(int i=0;i<stock.size();i++)if(stock.getStack(i).isIn(net.minecraft.registry.tag.ItemTags.PLANKS)&&stock.getStack(i).getCount()>=2)return supplyRecipe(stock,Items.STICK,4,Map.of(stock.getStack(i).getItem(),2));
        if(stock.count(Items.PAPER)<16&&supplyRecipe(stock,Items.PAPER,3,Map.of(Items.SUGAR_CANE,3)))return true;
        if(stock.count(Items.FLINT)<16&&supplyRecipe(stock,Items.FLINT,1,Map.of(Items.GRAVEL,1)))return true;
        if(stock.count(Items.GUNPOWDER)<16&&supplyRecipe(stock,Items.GUNPOWDER,4,Map.of(Items.COAL,2,Items.REDSTONE,1,Items.FLINT,1)))return true;
        if(stock.count(Items.IRON_NUGGET)<8&&stock.count(Items.IRON_INGOT)>4)return supplyRecipe(stock,Items.IRON_NUGGET,9,Map.of(Items.IRON_INGOT,1));
        return false;
    }
    static long selectFactoryBatch(String role,long batch,Inventory carried,Inventory input){return selectFactoryBatch(role,batch,carried,input,order->true);}
    private static long selectFactoryBatch(String role,long batch,Inventory carried,Inventory input,java.util.function.Predicate<FactoryRecipe> needed){
        int length=role.equals("armorer")?4:16;
        for(int offset=0;offset<length;offset++){long candidate=batch+offset;var order=factoryRecipe(role,candidate);if(!needed.test(order))continue;boolean enough=true;for(var ingredient:order.ingredients().entrySet())if(carried.count(ingredient.getKey())+(input==null?0:input.count(ingredient.getKey()))<ingredient.getValue()){enough=false;break;}if(enough&&hasSpace(carried,new ItemStack(order.product(),order.count())))return candidate;}
        return -1;
    }
    private static boolean serviceFurnaces(ServerWorld w,BlockPos base,VillagerEntity worker,Inventory stock){
        Item[] raw={Items.RAW_IRON,Items.RAW_GOLD,Items.RAW_COPPER};int[] x={3,7,11};
        for(int station=0;station<3;station++){var pos=base.add(x[station],1,9);if(!(w.getBlockEntity(pos) instanceof Inventory furnace))continue;
            boolean output=!furnace.getStack(2).isEmpty(),refill=furnace.getStack(0).isEmpty()&&stock.count(raw[station])>0,fuel=!furnace.getStack(0).isEmpty()&&furnace.getStack(1).isEmpty()&&stock.count(Items.COAL)>0;
            if(!output&&!refill&&!fuel)continue;if(worker.squaredDistanceTo(Vec3d.ofCenter(pos))>9){VillageProjects.approach(w,worker,pos);return true;}
            int before=furnace.getStack(2).getCount();if(output)insert(stock,furnace.getStack(2));if(refill)moveOne(stock,furnace,raw[station],0);if(furnace.getStack(1).isEmpty()&&!furnace.getStack(0).isEmpty())moveOne(stock,furnace,Items.COAL,1);furnace.markDirty();stock.markDirty();worker.swingHand(Hand.MAIN_HAND);
            // A full output barrel must not monopolise every worker forever.
            if(!output||furnace.getStack(2).getCount()!=before||refill||fuel)return true;
        }return false;
    }
    static boolean harvestFactoryCane(ServerWorld w,BlockPos base,VillagerEntity worker,Inventory stock){
        for(int x=2;x<=14;x+=2){var root=base.add(x,1,11);if(!w.isChunkLoaded(new ChunkPos(root).toLong())||!w.getBlockState(root).isOf(Blocks.SUGAR_CANE))continue;
            var top=root.up(2);if(!w.getBlockState(top).isOf(Blocks.SUGAR_CANE))top=root.up();if(!w.getBlockState(top).isOf(Blocks.SUGAR_CANE)||!hasSpace(stock,new ItemStack(Items.SUGAR_CANE)))continue;
            if(worker.squaredDistanceTo(Vec3d.ofCenter(top))>9){VillageProjects.approach(w,worker,top);return true;}
            if(w.setBlockState(top,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL)){insert(stock,new ItemStack(Items.SUGAR_CANE));worker.swingHand(Hand.MAIN_HAND);}return true;
        }return false;
    }
    private static boolean factoryDemand(ServerWorld w,VillageEraData.Settlement s,FactoryRecipe order){
        int stored=0;for(int i=0;i<6;i++){var inv=inventory(w,s,i);if(inv!=null)stored+=inv.count(order.product());}
        var base=VillageFactory.base(w,s.village);if(base!=null&&w.getBlockEntity(base.add(11,1,7)) instanceof Inventory output)stored+=output.count(order.product());
        int target=order.product()==VillageItems.MUSKET_CARTRIDGE?64:order.product()==NavalCannons.BALL?32:order.product()==NavalCannons.FIELD_GUN?1:2;
        return stored<target;
    }
    private static boolean manufacture(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v){
        var works=VillageFactory.base(w,s.village);if(works!=null&&!VillageFactory.supplyReady(w,s.village))return false;
        if(works!=null&&w.getBlockEntity(works.add(3,1,7)) instanceof Inventory materials){
            if(serviceFurnaces(w,works,v,materials))return false;
            if(materials.count(Items.PAPER)<8&&materials.count(Items.SUGAR_CANE)<8&&harvestFactoryCane(w,works,v,materials))return false;
            if(v.squaredDistanceTo(Vec3d.ofCenter(works.add(3,1,7)))<=25)processFactorySupplies(materials);
        }
        long batch=VillageEras.tag(v,"halloween_factory_batch_",0);
        Inventory available=works!=null&&w.getBlockEntity(works.add(3,1,7)) instanceof Inventory in?in:null;
        long selected=selectFactoryBatch(VillageEras.profession(v),batch,v.getInventory(),available,order->factoryDemand(w,s,order));
        if(selected<0){if(works!=null&&v.squaredDistanceTo(Vec3d.ofCenter(works.add(3,1,7)))>25)VillageProjects.approach(w,v,works.add(3,1,7));return false;}batch=selected;
        var order=factoryRecipe(VillageEras.profession(v),batch);
        ItemStack product=new ItemStack(order.product(),order.count());
        if(!v.getInventory().canInsert(product))return false;
        Map<Item,Integer> recipe=order.ingredients();
        var factoryBase=VillageFactory.base(w,s.village);
        net.minecraft.inventory.Inventory input=factoryBase!=null&&w.getBlockEntity(factoryBase.add(3,1,7)) instanceof net.minecraft.inventory.Inventory stock?stock:null;
        for(var ingredient:recipe.entrySet()){
            int missing=ingredient.getValue()-v.getInventory().count(ingredient.getKey());if(missing<=0)continue;
            var cargo=new ItemStack(ingredient.getKey(),missing);if(!v.getInventory().canInsert(cargo))return false;
            if(input!=null){
                var station=factoryBase.add(3,1,7);if(v.squaredDistanceTo(Vec3d.ofCenter(station))>9){VillageProjects.approach(w,v,station);return false;}
                if(input.count(ingredient.getKey())<missing)return false;
                for(int i=0;i<input.size()&&missing>0;i++)if(input.getStack(i).isOf(ingredient.getKey())){int n=Math.min(missing,input.getStack(i).getCount());v.getInventory().addStack(input.removeStack(i,n));missing-=n;}
                input.markDirty();return false;
            }
            var source=barrel(s,3);
            if(v.squaredDistanceTo(Vec3d.ofCenter(source))>9){VillageProjects.approach(w,v,source);return false;}
            if(!take(w,s,ingredient.getKey(),missing))return false;
            var remainder=v.getInventory().addStack(cargo);if(!remainder.isEmpty())store(w,s,remainder);
            return false;
        }
        var destination=VillageFactory.base(w,s.village);if(destination==null)return false;
        destination=destination.add(2,1,3);
        if(v.squaredDistanceTo(Vec3d.ofCenter(destination))>9){VillageProjects.approach(w,v,destination);return false;}
        for(var ingredient:recipe.entrySet()){
            int remaining=ingredient.getValue();for(int i=0;i<v.getInventory().size()&&remaining>0;i++)if(v.getInventory().getStack(i).isOf(ingredient.getKey())){int n=Math.min(remaining,v.getInventory().getStack(i).getCount());v.getInventory().removeStack(i,n);remaining-=n;}
        }
        if(factoryBase!=null&&w.getBlockEntity(factoryBase.add(11,1,7)) instanceof net.minecraft.inventory.Inventory output){
            insert(output,product);if(!product.isEmpty())v.getInventory().addStack(product);
        }else v.getInventory().addStack(product);
        VillageEras.putTag(v,"halloween_factory_batch_",batch+1);
        v.swingHand(Hand.MAIN_HAND);VillageEras.work(w,v,"weaponsmith",6);return true;
    }
    private static boolean forge(ServerWorld w,VillageEraData.Settlement s,VillagerEntity v,String role){
        var factory=VillageFactory.project(w,s.village);var factoryBase=VillageFactory.base(w,s.village);
        if(role.equals("weaponsmith")&&factory!=null&&factory.done&&factoryBase!=null&&v.getBlockPos().isWithinDistance(factoryBase.add(8,1,6),12))return manufacture(w,s,v);
        int serial=(int)VillageEras.tag(v,"halloween_forged_count_",0);boolean advanced=s.era>=2;Item product;int cost;Item material;
        if(role.equals("weaponsmith")){
            if(!advanced){product=serial%2==0?Items.WOODEN_SWORD:Items.WOODEN_SPEAR;material=Items.OAK_PLANKS;cost=2;}
            else {Item[] products={Items.COPPER_SWORD,Items.IRON_SWORD,Items.COPPER_SPEAR,Items.IRON_SPEAR,Items.SHIELD,Items.CROSSBOW};product=products[serial%products.length];material=serial%2==0?Items.COPPER_INGOT:Items.IRON_INGOT;cost=product==Items.SHIELD?1:2;}
        }else{
            Item[] products=advanced?new Item[]{Items.COPPER_HELMET,Items.IRON_HELMET,Items.COPPER_CHESTPLATE,Items.IRON_CHESTPLATE,Items.COPPER_LEGGINGS,Items.IRON_LEGGINGS,Items.COPPER_BOOTS,Items.IRON_BOOTS,Items.COPPER_HORSE_ARMOR,Items.IRON_HORSE_ARMOR}
                :new Item[]{Items.CHAINMAIL_HELMET,Items.CHAINMAIL_CHESTPLATE,Items.CHAINMAIL_LEGGINGS,Items.CHAINMAIL_BOOTS};
            product=products[serial%products.length];material=advanced&&serial%2==0?Items.COPPER_INGOT:Items.IRON_INGOT;String name=Registries.ITEM.getId(product).getPath();cost=name.contains("chestplate")?8:name.contains("leggings")||name.contains("horse_armor")?7:name.contains("helmet")?5:4;
        }
        var stack=new ItemStack(product);stack.set(DataComponentTypes.LORE,new net.minecraft.component.type.LoreComponent(List.of(Text.literal("Мастерская: "+s.name),Text.literal("Мастер: "+v.getName().getString()))));if(!v.getInventory().canInsert(stack))return false;
        if(!take(w,s,material,cost))return false;
        ItemStack remainder=v.getInventory().addStack(stack);if(!remainder.isEmpty()){store(w,s,new ItemStack(material,cost));return false;}
        v.swingHand(Hand.MAIN_HAND);w.playSound(null,v.getBlockPos(),SoundEvents.BLOCK_ANVIL_USE,net.minecraft.sound.SoundCategory.NEUTRAL,.3f,1.1f);
        VillageEras.putTag(v,"halloween_forged_count_",serial+1);VillageEras.work(w,v,role,8);return true;
    }
    private static boolean repairGolem(ServerWorld w,VillageEraData.Settlement s,VillagerEntity smith){
        if(w.getTime()-VillageEras.tag(smith,"halloween_golem_repair_",-1000)<100)return false;
        var golem=w.getEntitiesByClass(IronGolemEntity.class,new Box(smith.getBlockPos()).expand(24),g->g.isAlive()&&g.getHealth()<g.getMaxHealth()).stream().min(Comparator.comparingDouble(smith::squaredDistanceTo)).orElse(null);if(golem==null)return false;
        if(smith.squaredDistanceTo(golem)>9){VillageProjects.approach(w,smith,golem.getBlockPos());return true;}
        if(!take(w,s,Items.IRON_INGOT,1))return false;golem.heal(25);smith.swingHand(Hand.MAIN_HAND);VillageEras.putTag(smith,"halloween_golem_repair_",w.getTime());VillageEras.work(w,smith,VillageEras.profession(smith),5);return true;
    }
    private static void farm(ServerWorld w,VillageEraData.Settlement s,VillagerEntity farmer,List<VillagerEntity> people){
        VillageTasks.hold(farmer);BlockPos target=CROPS.get(farmer.getUuid());
        if(target==null||!(w.getBlockState(target).getBlock() instanceof CropBlock crop)||!crop.isMature(w.getBlockState(target))){
            target=null;BlockPos focus=farmer.getBlockPos();int radius=14;
            if(s.plantation==null){var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);BlockPos nearest=null;double distance=Double.MAX_VALUE;
                for(var p:VillageWorkplaceIndex.get(w).crops)if(p.isWithinDistance(farmer.getBlockPos(),128)&&w.getBlockState(p).getBlock() instanceof CropBlock){double d=farmer.squaredDistanceTo(Vec3d.ofCenter(p));if(d<distance){distance=d;nearest=p;}}
                if(nearest!=null){focus=nearest;if(distance>28*28){VillageProjects.approach(w,farmer,focus);return;}}
            }
            if(s.plantation!=null&&VillageProjects.get(w).complete(s.village,"plantation")){
                int slot=(int)VillageEras.tag(farmer,"halloween_plantation_slot_",-1);if(slot<0||slot>=10)return;
                focus=s.plantation.add(slot%5*5+2,1,slot/5*25+12);radius=14;
                if(farmer.squaredDistanceTo(Vec3d.ofCenter(focus))>28*28){VillageProjects.approach(w,farmer,focus);return;}
            }
            for(int n=0;n<120;n++){var q=focus.add(w.random.nextBetween(-radius,radius),w.random.nextBetween(-1,1),w.random.nextBetween(-radius,radius));if(w.getBlockState(q).getBlock() instanceof CropBlock c&&c.isMature(w.getBlockState(q))){target=q;break;}}
            if(target==null){if(farmer.squaredDistanceTo(Vec3d.ofCenter(focus))>9)VillageProjects.approach(w,farmer,focus);return;}CROPS.put(farmer.getUuid(),target);
        }
        if(farmer.squaredDistanceTo(Vec3d.ofCenter(target))>9){VillageProjects.approach(w,farmer,target);return;}
        var state=w.getBlockState(target);Item item=state.isOf(Blocks.CARROTS)?Items.CARROT:state.isOf(Blocks.POTATOES)?Items.POTATO:state.isOf(Blocks.BEETROOTS)?Items.BEETROOT:Items.WHEAT;
        var harvest=new ItemStack(item,item==Items.WHEAT?1:2);if(!farmer.getInventory().canInsert(harvest))return;
        if(w.setBlockState(target,state.getBlock().getDefaultState(),Block.NOTIFY_ALL)){farmer.getInventory().addStack(harvest);farmer.swingHand(Hand.MAIN_HAND);VillageEras.work(w,farmer,"farmer",2);CROPS.remove(farmer.getUuid());}
    }
    private static int rank(ItemStack stack){String name=Registries.ITEM.getId(stack.getItem()).getPath();return stack.isEmpty()?0:name.startsWith("diamond_")?6:stack.getItem() instanceof MusketItem?6:stack.isOf(VillageItems.SABRE)||stack.isOf(VillageItems.RAPIER)?5:name.startsWith("iron_")?4:name.startsWith("copper_")?3:name.startsWith("chainmail_")?2:name.startsWith("wooden_")?1:3;}
    private static ItemStack equipment(ServerWorld w,VillageEraData.Settlement s,java.util.function.Predicate<ItemStack> accepts,int minimum){
        Inventory best=null;int slot=-1,score=minimum;
        for(int k=0;k<6;k++){var inv=inventory(w,s,k);if(inv==null)continue;for(int i=0;i<inv.size();i++){var stack=inv.getStack(i);if(!stack.isEmpty()&&accepts.test(stack)&&rank(stack)>score){score=rank(stack);best=inv;slot=i;}}}
        if(best==null)return ItemStack.EMPTY;var result=best.removeStack(slot,1);best.markDirty();return result;
    }
    private static void issue(ServerWorld w,VillageEraData.Settlement s,List<VillagerEntity> people,long day){
        boolean armor=s.era>=2||s.transitionAt>=0;
        for(var g:people){if(!VillageDefense.isGuard(g)||VillageDefense.escorting(g)||VillageEras.tag(g,"halloween_equipped_day_",-1)==day)continue;
            if(s.era>=3){
                if(!(g.getMainHandStack().getItem() instanceof MusketItem)&&take(w,s,VillageItems.MUSKET,1)){var old=g.getMainHandStack();g.equipStack(EquipmentSlot.MAINHAND,new ItemStack(VillageItems.MUSKET));store(w,s,old);}
                if(!(g.getMainHandStack().getItem() instanceof MusketItem)&&take(w,s,VillageItems.REVOLVER,1)){var old=g.getMainHandStack();g.equipStack(EquipmentSlot.MAINHAND,new ItemStack(VillageItems.REVOLVER));store(w,s,old);}
                if(g.getMainHandStack().getItem() instanceof MusketItem&&g.getInventory().count(VillageItems.MUSKET_CARTRIDGE)<8&&g.getInventory().canInsert(new ItemStack(VillageItems.MUSKET_CARTRIDGE,8))&&take(w,s,VillageItems.MUSKET_CARTRIDGE,8))g.getInventory().addStack(new ItemStack(VillageItems.MUSKET_CARTRIDGE,8));
            }
            if(s.era>=3&&g.getMainHandStack().getItem() instanceof MusketItem&&g.getInventory().count(VillageItems.SABRE)+g.getInventory().count(VillageItems.RAPIER)==0){
                for(Item sidearm:List.of(VillageItems.SABRE,VillageItems.RAPIER))if(g.getInventory().canInsert(new ItemStack(sidearm))&&take(w,s,sidearm,1)){g.getInventory().addStack(new ItemStack(sidearm));break;}
            }
            var weapon=equipment(w,s,stack->{String n=Registries.ITEM.getId(stack.getItem()).getPath();return (n.contains("sword")||n.contains("spear")||stack.isOf(Items.CROSSBOW)||stack.isOf(VillageItems.RAPIER)||stack.isOf(VillageItems.SABRE))&&(s.era>=2||n.startsWith("wooden_"));},rank(g.getMainHandStack()));
            if(!weapon.isEmpty()&&g.getMainHandStack().getItem() instanceof MusketItem){store(w,s,weapon);weapon=ItemStack.EMPTY;}
            if(!weapon.isEmpty()){var old=g.getMainHandStack();g.equipStack(EquipmentSlot.MAINHAND,weapon);store(w,s,old);}
            if(armor)for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
                String suffix=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";default->"boots";};
                var piece=equipment(w,s,stack->Registries.ITEM.getId(stack.getItem()).getPath().endsWith(suffix)&&(s.era>=2||Registries.ITEM.getId(stack.getItem()).getPath().startsWith("chainmail_")),rank(g.getEquippedStack(slot)));
                if(!piece.isEmpty()){var old=g.getEquippedStack(slot);g.equipStack(slot,piece);store(w,s,old);}
            }
            if(s.era>=2&&g.getOffHandStack().isEmpty()){var shield=equipment(w,s,stack->stack.isOf(Items.SHIELD),0);if(!shield.isEmpty())g.equipStack(EquipmentSlot.OFFHAND,shield);}
            if(s.era>=2&&!g.getCommandTags().contains("halloween_bodyguard")&&!g.getCommandTags().contains("halloween_issued_mount")&&people.stream().filter(v->v.getCommandTags().contains("halloween_issued_mount")).count()<2){
                var horseArmor=equipment(w,s,stack->stack.isOf(Items.IRON_HORSE_ARMOR)||stack.isOf(Items.COPPER_HORSE_ARMOR),0);
                if(!horseArmor.isEmpty()){if(take(w,s,Items.SADDLE,1)){g.addCommandTag("halloween_issued_mount");g.addCommandTag(horseArmor.isOf(Items.COPPER_HORSE_ARMOR)?"halloween_mount_copper":"halloween_mount_iron");}else store(w,s,horseArmor);}
            }
            VillageEras.putTag(g,"halloween_equipped_day_",day);
        }
    }
    private VillageEconomy(){}
}
