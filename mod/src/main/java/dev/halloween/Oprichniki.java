package dev.halloween;

import com.google.common.collect.ImmutableSet;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;

/** Royal treasury guards; patrol and visits are coordinated independently from ordinary guards. */
final class Oprichniki {
    static boolean isOfficer(VillagerEntity v){return v.getCommandTags().contains("halloween_oprichnik")||v.getVillagerData().profession().matchesKey(KEY);}
    static final RegistryKey<VillagerProfession> KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","oprichnik"));
    private static RegistryEntry<VillagerProfession> ENTRY;
    private record Visit(UUID resident,BlockPos home,BlockPos chest,int stage,long next){}
    private static final String VISIT_TAG="halloween_oprich_visit:";
    private static Visit getVisit(VillagerEntity guard){
        for(String tag:guard.getCommandTags())if(tag.startsWith(VISIT_TAG))try{String[] a=tag.substring(VISIT_TAG.length()).split(":");return new Visit(UUID.fromString(a[0]),BlockPos.fromLong(Long.parseLong(a[1])),BlockPos.fromLong(Long.parseLong(a[2])),Integer.parseInt(a[3]),Long.parseLong(a[4]));}catch(RuntimeException ignored){}return null;
    }
    static String thought(VillagerEntity officer){var visit=getVisit(officer);if(visit==null)return "Патрулирует деревню";return switch(visit.stage()){case 0->"Едет к дому неплательщика";case 1->"Стучит в дом";case 2,4->"Проверяет спрятанные вещи";default->"Решает исход проверки";};}
    private static void setVisit(VillagerEntity guard,Visit visit){
        for(String tag:List.copyOf(guard.getCommandTags()))if(tag.startsWith(VISIT_TAG))guard.removeCommandTag(tag);
        if(visit!=null)guard.addCommandTag(VISIT_TAG+visit.resident()+":"+visit.home().asLong()+":"+visit.chest().asLong()+":"+visit.stage()+":"+visit.next());
    }
    private static void queueRebuild(ServerWorld w,VillagerEntity officer,Visit visit,VillagerEntity victim){
        String role=VillageEras.profession(victim);if(!VillageEras.PROFESSIONS.contains(role))role="builder";
        officer.addCommandTag("halloween_oprich_rebuild:"+visit.home().asLong()+":"+(w.getTime()+2400)+":"+role);
    }
    private static void rebuild(ServerWorld w,VillageMemory.Team team,VillagerEntity officer){
        for(String tag:List.copyOf(officer.getCommandTags()))if(tag.startsWith("halloween_oprich_rebuild:"))try{
            String[] a=tag.substring("halloween_oprich_rebuild:".length()).split(":");BlockPos bed=BlockPos.fromLong(Long.parseLong(a[0]));if(w.getTime()<Long.parseLong(a[1]))continue;String role=a[2];
            var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
            String id="oprich_rebuild:"+team.village+":"+bed.asLong();if(projects.exists(id)){officer.removeCommandTag(tag);continue;}
            var h=memory.houses.stream().filter(x->x.kind.equals("house")&&bed.getX()>=x.min.getX()&&bed.getX()<=x.max.getX()&&bed.getZ()>=x.min.getZ()&&bed.getZ()<=x.max.getZ()).findFirst().orElse(null);if(h==null)continue;
            var door=h.blueprint.stream().filter(r->r.state().getBlock() instanceof net.minecraft.block.DoorBlock&&r.state().get(net.minecraft.block.DoorBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER).map(VillageMemory.BlockRecord::pos).findFirst();if(door.isEmpty())continue;
            var template=VillageEraPlans.template(w,"profession_"+role+"_0");if(template==null)continue;BlockPos d=door.get(),base=new BlockPos(d.getX()-template.size().getX()/2,d.getY()-1,d.getZ()-1);
            Set<BlockPos> owned=new HashSet<>();for(var r:h.blueprint)owned.add(r.pos());Map<BlockPos,net.minecraft.block.BlockState> plan=new LinkedHashMap<>();
            for(var r:h.blueprint){if(!w.isChunkLoaded(new ChunkPos(r.pos()).toLong())){VillageProjects.keepLoaded(w,r.pos());return;}var current=w.getBlockState(r.pos());if(!current.hasBlockEntity()&&!r.state().isAir()&&current.isOf(r.state().getBlock()))plan.put(r.pos(),net.minecraft.block.Blocks.AIR.getDefaultState());}
            boolean safe=true;for(var e:VillageArchitecture.plan(w,base,role,0).entrySet()){
                var pos=e.getKey();if(!w.isChunkLoaded(new ChunkPos(pos).toLong())){VillageProjects.keepLoaded(w,pos);return;}var current=w.getBlockState(pos);if(current.hasBlockEntity())continue;
                if(!owned.contains(pos)&&!current.isAir()&&!current.isReplaceable()&&!VillageCivilWorks.natural(current)){safe=false;break;}plan.put(pos,e.getValue());
            }
            if(safe&&!plan.isEmpty()){projects.add(w,id,team.village,"workshop_"+role,plan);if(projects.exists(id))officer.removeCommandTag(tag);}
        }catch(RuntimeException ignored){}
    }
    private static void say(ServerWorld w,BlockPos home,String message){
        for(var player:w.getPlayers())if(player.getBlockPos().isWithinDistance(home,20))player.sendMessage(Text.literal(message),false);
    }
    private static void visit(ServerWorld w,VillagerEntity officer,Visit job){
        var entity=w.getEntity(job.resident());if(entity==null)return;if(!(entity instanceof VillagerEntity resident)||!resident.isAlive()){setVisit(officer,null);officer.stopRiding();return;}
        if(w.getTime()<job.next())return;
        if(job.stage()==0){
            if(officer.squaredDistanceTo(Vec3d.ofCenter(job.home()))>36){
                if(officer.getVehicle() instanceof net.minecraft.entity.passive.HorseEntity horse){if(w.getTime()%100==0)horse.getNavigation().startMovingTo(job.home().getX()+.5,job.home().getY(),job.home().getZ()+.5,.9);}
                else VillageProjects.approach(w,officer,job.home());return;
            }
            officer.stopRiding();w.playSound(null,job.home(),SoundEvents.BLOCK_WOOD_HIT,net.minecraft.sound.SoundCategory.NEUTRAL,1,1);
            say(w,job.home(),"§8[У дома] §cОпричник: Открывай. Пришли за налогом.");setVisit(officer,new Visit(job.resident(),job.home(),job.chest(),1,w.getTime()+60));return;
        }
        if(job.stage()==1){
            int hidden=0;
            if(w.getBlockEntity(job.chest()) instanceof net.minecraft.block.entity.ChestBlockEntity chest&&resident.getBlockPos().isWithinDistance(job.chest(),12)){
                for(int i=0;i<chest.size();i++){var stack=chest.getStack(i);if(stack.isEmpty())continue;var remainder=resident.getInventory().addStack(stack.copy());int moved=stack.getCount()-remainder.getCount();if(moved>0){chest.setStack(i,remainder);chest.markDirty();resident.getInventory().markDirty();hidden=moved;break;}}
            }
            say(w,job.home(),"§8[У дома] §7Житель: У меня ничего нет. Я беден.");
            setVisit(officer,new Visit(job.resident(),job.home(),job.chest(),hidden>0?2:3,w.getTime()+60));return;
        }
        if(job.stage()==2&&w.random.nextBoolean()){
            say(w,job.home(),"§8[У дома] §cОпричник: Я видел, как ты спрятал вещи!");
            if(officer.squaredDistanceTo(resident)>9){VillageProjects.approach(w,officer,resident.getBlockPos());setVisit(officer,new Visit(job.resident(),job.home(),job.chest(),4,w.getTime()+20));return;}
            officer.swingHand(net.minecraft.util.Hand.MAIN_HAND);resident.damage(w,w.getDamageSources().mobAttack(officer),1000);if(!resident.isAlive())queueRebuild(w,officer,job,resident);
        }else if(job.stage()==4){
            if(officer.squaredDistanceTo(resident)>9){VillageProjects.approach(w,officer,resident.getBlockPos());return;}
            officer.swingHand(net.minecraft.util.Hand.MAIN_HAND);resident.damage(w,w.getDamageSources().mobAttack(officer),1000);if(!resident.isAlive())queueRebuild(w,officer,job,resident);
        }else say(w,job.home(),"§8[У дома] §cОпричник: Сегодня тебе повезло.");
        setVisit(officer,null);
    }
    private static boolean mount(ServerWorld w,VillageMemory.Team team,VillagerEntity officer){
        if(officer.hasVehicle())return true;
        var stable=VillageStable.entrance(w,team.village);if(stable==null)return false;
        String owner="halloween_oprich_horse_"+officer.getUuidAsString();
        var existing=w.getEntitiesByClass(net.minecraft.entity.passive.HorseEntity.class,new Box(team.anchor).expand(320,96,320),h->h.isAlive()&&h.getCommandTags().contains(owner)&&!h.hasPassengers()).stream().findFirst().orElse(null);
        if(existing!=null){if(!existing.getBlockPos().isWithinDistance(stable,14)){if(existing.getNavigation().isIdle())existing.getNavigation().startMovingTo(stable.getX()+.5,stable.getY(),stable.getZ()+.5,.65);VillageProjects.approach(w,officer,existing.getBlockPos());return false;}if(officer.squaredDistanceTo(existing)>6){VillageProjects.approach(w,officer,existing.getBlockPos());return false;}return officer.startRiding(existing,true,true);}
        if(officer.squaredDistanceTo(Vec3d.ofCenter(stable))>9){VillageProjects.approach(w,officer,stable);return false;}
        var horse=net.minecraft.entity.EntityType.HORSE.create(w,net.minecraft.entity.SpawnReason.STRUCTURE);if(horse==null)return false;horse.addCommandTag(owner);
        horse.refreshPositionAndAngles(officer.getX()+1,officer.getY(),officer.getZ(),0,0);horse.initialize(w,w.getLocalDifficulty(officer.getBlockPos()),net.minecraft.entity.SpawnReason.STRUCTURE,null);
        horse.setTame(true);horse.setPersistent();horse.equipStack(EquipmentSlot.SADDLE,new ItemStack(Items.SADDLE));
        var speed=horse.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED);if(speed!=null)speed.setBaseValue(.28);
        return w.spawnEntity(horse)&&officer.startRiding(horse,true,true);
    }
    static void register(){
        var profession=Registry.register(Registries.VILLAGER_PROFESSION,KEY,new VillagerProfession(Text.translatable("entity.minecraft.villager.halloween.oprichnik"),e->false,e->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_ARMORER));
        ENTRY=Registries.VILLAGER_PROFESSION.getEntry(profession);ServerTickEvents.END_WORLD_TICK.register(Oprichniki::tick);
    }
    private static void equip(VillagerEntity v){
        if(!v.getMainHandStack().isOf(Items.DIAMOND_SWORD))v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        if(!v.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET))v.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        if(!v.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.IRON_CHESTPLATE))v.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        if(!v.getEquippedStack(EquipmentSlot.LEGS).isOf(Items.IRON_LEGGINGS))v.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));
        if(!v.getEquippedStack(EquipmentSlot.FEET).isOf(Items.IRON_BOOTS))v.equipStack(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var team:List.copyOf(memory.teams)){
            var state=VillageEraData.get(w).settlement(team);
            if(state.era<2||state.vault==null||!VillageProjects.get(w).complete(team.village,"treasury")||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,320)))continue;
            BlockPos post=team.anchor;
            var people=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(320,80,320),v->v.isAlive()&&!v.isBaby()&&VillageEras.belongs(w,v,team.village));
            var guards=new ArrayList<>(people.stream().filter(Oprichniki::isOfficer).toList());
            if(guards.size()<4&&w.getTime()%100==0){
                var volunteer=people.stream().filter(v->VillageEras.profession(v).equals("none")&&!VillageTrials.busy(v)&&!VillageDefense.isGuard(v)).findFirst().orElse(null);
                if(volunteer!=null){volunteer.setVillagerData(volunteer.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.get(VillagerProfession.NONE))));volunteer.setExperience(1);volunteer.setCustomName(Text.literal("Опричник"));volunteer.reinitializeBrain(w);volunteer.addCommandTag("halloween_oprichnik");guards.add(volunteer);}
            }
            for(var guard:guards){
                guard.addCommandTag("halloween_oprichnik");
                if(!guard.getVillagerData().profession().matchesKey(VillagerProfession.NONE)){guard.setVillagerData(guard.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.get(VillagerProfession.NONE))));guard.reinitializeBrain(w);}
                guard.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.JOB_SITE);guard.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.POTENTIAL_JOB_SITE);
                equip(guard);if(guard.isSleeping()||VillageTrials.busy(guard))continue;
                rebuild(w,team,guard);var active=getVisit(guard);if(active!=null){if(active.stage()==0&&VillageStable.entrance(w,team.village)!=null&&!mount(w,team,guard))continue;visit(w,guard,active);continue;}
                long day=Math.floorDiv(w.getTimeOfDay(),24000);long hour=Math.floorMod(w.getTimeOfDay(),24000);
                if(hour>=0&&hour<1000&&VillageStable.entrance(w,team.village)!=null&&!guard.hasVehicle()){mount(w,team,guard);continue;}
                if(day>0&&hour>=1000&&hour<10000&&w.getTime()%200==0){
                    for(var resident:people){
                        if(resident==guard||VillageDefense.isGuard(resident)||isOfficer(resident)||resident.getVillagerData().profession().matchesKey(VillageEconomy.TREASURER)||VillageTrials.busy(resident)||VillageEras.tag(resident,"halloween_tax_day_",-1)>=day-1||VillageEras.tag(resident,"halloween_oprichnik_visit_",-1)>=day)continue;
                        var home=resident.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.HOME);if(home.isEmpty()||!home.get().dimension().equals(w.getRegistryKey()))continue;
                        BlockPos bed=home.get().pos();var house=memory.houses.stream().filter(h->h.kind.equals("house")&&bed.getX()>=h.min.getX()&&bed.getX()<=h.max.getX()&&bed.getZ()>=h.min.getZ()&&bed.getZ()<=h.max.getZ()).findFirst().orElse(null);if(house==null)continue;
                        var chest=house.blueprint.stream().filter(r->r.state().isOf(net.minecraft.block.Blocks.CHEST)&&w.getBlockEntity(r.pos()) instanceof net.minecraft.block.entity.ChestBlockEntity).map(VillageMemory.BlockRecord::pos).findFirst();if(chest.isEmpty())continue;
                        VillageEras.putTag(resident,"halloween_oprichnik_visit_",day);setVisit(guard,new Visit(resident.getUuid(),bed,chest.get(),0,w.getTime()));break;
                    }
                }
                if(getVisit(guard)==null&&guard.squaredDistanceTo(Vec3d.ofCenter(post))>144)VillageProjects.approach(w,guard,post);
            }
        }
    }
    private Oprichniki(){}
}

