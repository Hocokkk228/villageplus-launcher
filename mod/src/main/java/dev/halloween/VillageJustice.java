package dev.halloween;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.village.VillagerProfession;

/** Early village law prototype: guards respond to a witnessed assault. */
final class VillageJustice {
    private static final String GUARD="halloween_guard", BANDIT="halloween_bandit", WANTED="halloween_wanted", SURRENDER="halloween_surrender", PRISONER="halloween_prisoner";
    private static final java.util.Map<java.util.UUID,java.util.UUID> VICTIMS=new java.util.HashMap<>();
    private static final java.util.Map<Long,net.minecraft.entity.boss.ServerBossBar> VOTE_BARS=new java.util.HashMap<>();
    private static final String BODYGUARD="halloween_bodyguard";
    private static final java.util.Map<java.util.UUID,java.util.UUID> THREATS=new java.util.HashMap<>();
    static java.util.UUID royalThreat(java.util.UUID king){return THREATS.get(king);}
    static void register(){
        ServerTickEvents.END_WORLD_TICK.register(VillageJustice::tick);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(entity instanceof VillagerEntity dead&&entity.getEntityWorld() instanceof ServerWorld world){
                var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                for(var team:memory.teams)if(team.patrolGuards.remove(dead.getUuidAsString()))memory.markDirty();
            }
            if(entity instanceof VillagerEntity king && RoyalProfession.isKing(king)
                    && king.getEntityWorld() instanceof ServerWorld world){
                String owner="halloween_king_"+king.getUuidAsString();
                for(var e:world.iterateEntities())if(e instanceof VillagerEntity guard&&guard.isAlive()&&guard.getCommandTags().contains(owner)){
                    guard.removeCommandTag(owner);guard.removeCommandTag(BODYGUARD);guard.removeCommandTag("halloween_castle_guard");
                    guard.addCommandTag(GUARD);guard.setVillagerData(guard.getVillagerData().withProfession(GuardProfession.ENTRY));guard.setExperience(1);guard.reinitializeBrain(world);guard.setCustomName(Text.translatable("entity.halloween.guard"));
                }
                for(var player:world.getPlayers())player.sendMessage(Text.translatable(RoyalProfession.isPresident(king)?"message.halloween.president_killed":"message.halloween.king_killed",king.getDisplayName()),false);
                VillageMemory memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                VillageMemory.Team team=memory.teams.stream().filter(t->king.squaredDistanceTo(Vec3d.ofCenter(t.anchor))<160*160)
                    .min(java.util.Comparator.comparingDouble(t->king.squaredDistanceTo(Vec3d.ofCenter(t.anchor)))).orElse(null);
                if(team==null){team=new VillageMemory.Team(new net.minecraft.util.math.ChunkPos(king.getBlockPos()).toLong(),king.getBlockPos(),0);memory.teams.add(team);}
                team.electionAt=world.getTimeOfDay();team.electionStage=1;
                team.royalKiller=source.getAttacker()==null?"":source.getAttacker().getUuidAsString();memory.markDirty();
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(entity instanceof VillagerEntity king && RoyalProfession.isKing(king)
                    && source.getAttacker() instanceof net.minecraft.entity.LivingEntity attacker && attacker!=king)
                THREATS.put(king.getUuid(),attacker.getUuid());
            return true;
        });
    }
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
        VillageMemory memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(VillageMemory.Team team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<VillageEras.radius(w,team.anchor)*VillageEras.radius(w,team.anchor)))continue;
            List<VillagerEntity> all=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(VillageEras.radius(w,team.anchor),96,VillageEras.radius(w,team.anchor)),v->v.isAlive()&&!v.isBaby());
            boolean royalTrial=team.royalKiller!=null&&!team.royalKiller.isEmpty()&&w.getPlayers().stream().anyMatch(p->p.getUuidAsString().equals(team.royalKiller)&&p.getCommandTags().contains("halloween_player_wanted"));
            if(royalTrial&&team.electionStage==1){team.electionAt=w.getTimeOfDay();memory.markDirty();}
            else updateElection(w,memory,team,all);
            VillagerEntity king=all.stream().filter(v->RoyalProfession.isKing(v)).findFirst().orElse(null);
            
            ensureVillagePatrol(w,memory,team,all);
            StreetCrime.tick(w,team,all);
            NightWatch.tick(w,team,all);
            patrolVillage(w,memory,team,all);
            // Criminal vacancies are replenished by the saved population roster.
            for(VillagerEntity v:all){
                if(RailDriver.isDriver(v)||v.hasVehicle()||RailBoarding.queued(v)||v.getCommandTags().contains("halloween_royal_assassin"))continue;
                if(VillageDefense.engaged(v)||VillageTrials.busy(v)||v.getCommandTags().contains("halloween_player_guarding")||v.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_cell_exit_")))continue;
                if(v.getCommandTags().contains(BANDIT)){
                    if(StreetCrime.active(w,v)||VillageFights.owns(w,v)||VillageConvoys.fighting(w,v))continue;
                    if(!v.getCommandTags().contains(WANTED)&&!v.getCommandTags().contains("halloween_attacked")&&VillageBuilders.workHours(w)&&w.random.nextInt(20)!=0)continue;
                    if(v.getCommandTags().contains(PRISONER)){
                        if(v.getCommandTags().contains("halloween_early_release"))continue;
                        String expiry=v.getCommandTags().stream().filter(t->t.startsWith("halloween_release_")).findFirst().orElse(null);
                        long until=Long.MAX_VALUE;try{if(expiry!=null)until=Long.parseLong(expiry.substring("halloween_release_".length()));}catch(NumberFormatException ignored){}
                        if(w.getTime()>=until)v.addCommandTag("halloween_early_release");
                        continue;
                    }
                    if(v.getCommandTags().contains(SURRENDER)){
                        if(!v.isLeashed())v.removeCommandTag(SURRENDER);
                        continue;
                    }
                    VillagerEntity witness=all.stream().filter(t->t!=v&&t.isAlive()&&v.squaredDistanceTo(t)<144
                            && !t.getCommandTags().contains(BANDIT) && t.getCommandTags().contains(GUARD) && t.canSee(v)).findFirst().orElse(null);
                    if(v.getCommandTags().contains(WANTED)||v.getCommandTags().contains("halloween_escaped")){
                        VillagerEntity threat=witness!=null?witness:all.stream().filter(t->t!=v&&!t.getCommandTags().contains(BANDIT)&&v.squaredDistanceTo(t)<144).findFirst().orElse(null);
                        if(threat!=null){double dx=v.getX()-threat.getX(),dz=v.getZ()-threat.getZ();double len=Math.max(1,Math.sqrt(dx*dx+dz*dz));
                            v.getNavigation().startMovingTo(v.getX()+dx/len*16,v.getY(),v.getZ()+dz/len*16,0.85);}
                    }else if(v.getCommandTags().contains("halloween_thief")){
                        VillageTheft.tick(w,v,all);
                    }else if(v.getCommandTags().contains("halloween_attacked")){
                        VillagerEntity target=all.stream().filter(t->t.getUuid().equals(VICTIMS.get(v.getUuid()))).findFirst().orElse(null);
                        boolean seen=target!=null && (all.stream().anyMatch(t->t!=v&&t!=target&&t.squaredDistanceTo(target)<144&&t.canSee(v))
                            || w.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(target)<144&&p.canSee(v)));
                        if(seen)v.addCommandTag(WANTED);
                        else if(target==null)v.addCommandTag("halloween_escaped");
                        else if(target.isAlive()){pursue(v,target);if(v.squaredDistanceTo(target)<4){v.swingHand(Hand.MAIN_HAND);target.damage(w,w.getDamageSources().mobAttack(v),6);}}
                    }else{
                        VillagerEntity victim=all.stream().filter(t->t!=v&&!t.getCommandTags().contains(BANDIT)&&!t.getCommandTags().contains(GUARD)
                                &&v.squaredDistanceTo(t)<100).min(java.util.Comparator.comparingDouble(v::squaredDistanceTo)).orElse(null);
                        if(victim!=null){
                            boolean seen=all.stream().anyMatch(t->t!=v&&t!=victim&&t.squaredDistanceTo(victim)<144&&t.canSee(v))
                                || w.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(victim)<144&&p.canSee(v));
                            if(!seen){pursue(v,victim);if(v.squaredDistanceTo(victim)<4){
                                v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));v.swingHand(Hand.MAIN_HAND);
                                victim.damage(w,w.getDamageSources().mobAttack(v),6);v.addCommandTag("halloween_attacked");VICTIMS.put(v.getUuid(),victim.getUuid());
                            }}
                        }
                    }
                }else if(v.getCommandTags().contains(GUARD)){
                    if(v.hasVehicle() && !(v.getCommandTags().contains(WANTED))&&!v.getCommandTags().contains("halloween_guard_escorting")){ VillageDefense.fight(w,v,v.getBlockPos(),24); continue; }
                    if(VillageDefense.fight(w,v,v.getBlockPos(),24))continue;
                    if(v.getCommandTags().contains("halloween_player_guarding"))continue;
                    if(v.getCommandTags().contains("halloween_castle_guard")&&!v.getCommandTags().contains("halloween_guard_escorting"))continue;
                    if(v.getCommandTags().contains("halloween_executor"))continue;
                    if(v.getCommandTags().contains(BODYGUARD))continue;
                    if(!v.getVillagerData().profession().matchesKey(GuardProfession.KEY)){
                        v.setVillagerData(v.getVillagerData().withProfession(GuardProfession.ENTRY));v.setExperience(1);v.reinitializeBrain(w);
                    }
                    VillagerEntity captive=all.stream().filter(t->t.getCommandTags().contains(SURRENDER)&&t.isLeashed()&&t.getLeashHolder()==v).findFirst().orElse(null);
                    if(captive!=null){
                        if(captive.squaredDistanceTo(v)>16){v.getNavigation().stop();VillageEscort.move(captive,v.getBlockPos(),.65f);continue;}
                        var jail=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(team.anchor).expand(128),m->m.getCommandTags().contains("halloween_jail"))
                            .stream().min(java.util.Comparator.comparingDouble(v::squaredDistanceTo)).orElse(null);
                        if(jail!=null){
                            if(captive.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_trial_")))continue;
                            BlockPos assignedCell=VillagePrisons.cellFor(w,jail,captive);if(assignedCell==null)continue;
                            List<BlockPos> route=new java.util.ArrayList<>(VillagePrisons.escortRoute(w,jail,captive));if(!route.isEmpty()){var door=VillagePrisons.doorPost(w,captive);if(door!=null)route.set(route.size()-1,door);}if(route.isEmpty()){var entry=VillageTrials.jailEntrance(w,jail.getBlockPos());if(entry!=null)VillageEscort.move(v,entry,.6f);continue;}
                            String prefix="halloween_escort_step_";int step=0;
                            for(String tag:captive.getCommandTags())if(tag.startsWith(prefix))try{step=Integer.parseInt(tag.substring(prefix.length()));}catch(NumberFormatException ignored){}
                            step=Math.max(0,Math.min(step,route.size()-1));BlockPos waypoint=route.get(step);
                            boolean arrived=step==route.size()-1&&v.squaredDistanceTo(Vec3d.ofCenter(waypoint))<=2.25;
                            if(arrived){
                                long since=VillageEras.tag(captive,"halloween_cell_arrived_",-1);
                                if(since<0)VillageEras.putTag(captive,"halloween_cell_arrived_",w.getTime());
                                else if(w.getTime()-since>=60){
                                    var destination=Vec3d.ofBottomCenter(assignedCell);
                                    if(w.isSpaceEmpty(captive,captive.getBoundingBox().offset(destination.subtract(captive.getEntityPos()))))captive.requestTeleport(destination.x,destination.y,destination.z);
                                }
                            }else for(String tag:List.copyOf(captive.getCommandTags()))if(tag.startsWith("halloween_cell_arrived_"))captive.removeCommandTag(tag);
                            if(v.squaredDistanceTo(Vec3d.ofCenter(waypoint))>2.25){VillageEscort.move(v,waypoint,.6f);}
                            else if(captive.squaredDistanceTo(v)>9){if(v.getNavigation().isIdle())v.getNavigation().startMovingTo(captive,.5);}
                            else if(step<route.size()-1){for(String tag:List.copyOf(captive.getCommandTags()))if(tag.startsWith(prefix))captive.removeCommandTag(tag);captive.addCommandTag(prefix+(step+1));}
                            else if(captive.squaredDistanceTo(Vec3d.ofCenter(assignedCell))>1.5){v.getNavigation().stop();VillageEscort.move(captive,assignedCell,.65f);}
                            else if(w.getBlockState(VillagePrisons.cellFor(w,jail,captive)).isAir()){
                                BlockPos room=VillagePrisons.cellFor(w,jail,captive);captive.requestTeleport(room.getX()+.5,room.getY(),room.getZ()+.5);
                                for(String tag:List.copyOf(captive.getCommandTags()))if(tag.startsWith(prefix))captive.removeCommandTag(tag);
                                captive.detachLeashWithoutDrop();captive.getNavigation().stop();captive.setAiDisabled(false);captive.addCommandTag(PRISONER);VillageTrials.sealCell(w,captive,jail.getBlockPos());
                                int days=captive.getCommandTags().contains("halloween_sentence_10")?10:captive.getCommandTags().contains("halloween_sentence_1")?1:5;
                                captive.addCommandTag("halloween_release_"+(w.getTime()+days*24000L));v.removeCommandTag("halloween_guard_escorting");
                            }
                        }
                        continue;
                    }
                    if(VillageCustody.busy(w,v,null))continue;
                    VillagerEntity target=all.stream().filter(t->!VillageDefense.isGuard(t)&&t.getCommandTags().contains(WANTED)&&!t.getCommandTags().contains(SURRENDER)&&!t.getCommandTags().contains(PRISONER)&&VillageDefense.assigned(w,v,t))
                            .min(java.util.Comparator.comparingDouble(v::squaredDistanceTo)).orElse(null);
                    if(target!=null){pursue(v,target);if(v.squaredDistanceTo(target)<4){
                        boolean jailAvailable=!w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(team.anchor).expand(128),m->m.getCommandTags().contains("halloween_jail")).isEmpty();
                        if(jailAvailable && (target.getCommandTags().contains("halloween_crime_murder") || target.getCommandTags().contains("halloween_thief") || target.getHealth()<=8 || w.random.nextInt(4)==0)){
                            v.stopRiding();target.addCommandTag(SURRENDER);target.getNavigation().stop();target.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                            target.attachLeash(v,true);v.addCommandTag("halloween_guard_escorting");VillageTrials.arrested(w,target);
                        }else{v.swingHand(Hand.MAIN_HAND);target.damage(w,w.getDamageSources().mobAttack(v),3);}
                    }}
                }
            }
        }
    }
    private static void patrolVillage(ServerWorld w,VillageMemory memory,VillageMemory.Team team,List<VillagerEntity> all){
        String owner="halloween_patrol_team_"+Long.toUnsignedString(team.village);
        var patrol=all.stream().filter(v->v.getCommandTags().contains(owner)&&!VillageDefense.engaged(v)&&!v.hasVehicle()&&!v.getCommandTags().contains("halloween_player_guarding")
            &&!v.getCommandTags().contains("halloween_executor")&&!VillageDefense.escorting(v)).sorted(java.util.Comparator.comparing(VillagerEntity::getUuid)).toList();
        if(patrol.isEmpty())return;
        var houses=memory.houses.stream().filter(h->h.kind.equals("house")&&h.min.isWithinDistance(team.anchor,VillageEras.radius(w,team.anchor))).toList();
        for(int group=0;group<patrol.size();group+=3){
            var leader=patrol.get(group);
            boolean alert=w.getPlayers().stream().anyMatch(p->p.getCommandTags().contains("halloween_player_wanted")&&!p.getCommandTags().contains("halloween_player_custody")&&p.getBlockPos().isWithinDistance(team.anchor,320)&&PlayerJustice.wantedLevel(p)>=2);
            int interval=alert?60:200;
            BlockPos investigation = group == 0 ? NightWatch.response(w, team) : null;
            if (investigation == null && group == 0) investigation = MarketCrime.investigation(w, team.village);
            if (investigation == null && group == 0 && w.getTime() % 120 == 0) {
                BlockPos post = NightWatch.post(w, team, leader.getBlockPos());
                if (post != null && !post.isWithinDistance(leader.getBlockPos(), 4) && VillagePathBudget.take(w)) {
                    leader.getNavigation().startMovingTo(post.getX() + .5, post.getY(), post.getZ() + .5, .68);
                }
            }
            if (investigation != null) {
                if (w.getTime() % 60 == 0 && !investigation.isWithinDistance(leader.getBlockPos(), 4)
                        && w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(investigation).toLong())
                        && VillagePathBudget.take(w)) {
                    leader.getNavigation().startMovingTo(investigation.getX() + .5, investigation.getY(),
                            investigation.getZ() + .5, .85);
                }
            } else if(w.getTime()%interval==0&&leader.getNavigation().isIdle()&&!houses.isEmpty()){
                var h=houses.get((group/3+(int)(w.getTime()/200))%houses.size());BlockPos q=h.min.add(-3,0,-3);
                var farm=VillageEraData.get(w).settlement(team).plantation;
                if(group+3>=patrol.size()&&farm!=null)q=farm.add(12,0,-3);
                if(w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(q).toLong())){int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,q.getX(),q.getZ());leader.getNavigation().startMovingTo(q.getX()+.5,y,q.getZ()+.5,alert?.85:.55);}
            }
            for(int i=group+1;i<Math.min(group+3,patrol.size());i++){var follower=patrol.get(i);if(follower.squaredDistanceTo(leader)>16&&follower.getNavigation().isIdle())follower.getNavigation().startMovingTo(leader,.6);}
        }
    }
    private static void ensureVillagePatrol(ServerWorld w,VillageMemory memory,VillageMemory.Team team,List<VillagerEntity> all){
        String owner="halloween_patrol_team_"+Long.toUnsignedString(team.village);
        int patrolLimit=12;
        if(team.patrolGuards.size()>patrolLimit){
            for(String id:List.copyOf(team.patrolGuards))try{
                if(team.patrolGuards.size()<=patrolLimit)break;
                if(!(w.getEntity(java.util.UUID.fromString(id)) instanceof VillagerEntity v)||!v.isAlive())continue;
                if(v.getCommandTags().contains("halloween_player_guarding")||v.getCommandTags().contains("halloween_guard_escorting")||v.getCommandTags().contains("halloween_executor"))continue;
                v.stopRiding();v.removeCommandTag(GUARD);v.removeCommandTag(owner);v.setCustomName(null);
                v.setVillagerData(v.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getEntry(net.minecraft.util.Identifier.of("minecraft","none")).orElseThrow()));v.reinitializeBrain(w);
                for(EquipmentSlot slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))v.equipStack(slot,ItemStack.EMPTY);
                team.patrolGuards.remove(id);memory.markDirty();
            }catch(IllegalArgumentException ignored){}
        }
        for(var v:all){
            if(!RailDriver.isDriver(v)&&v.getCommandTags().contains(GUARD)&&!v.getCommandTags().contains(BODYGUARD)&&!v.getCommandTags().contains("halloween_castle_guard")
                &&v.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_patrol_team_")&&!t.equals(owner))&&!team.patrolGuards.contains(v.getUuidAsString())){
                v.addCommandTag(owner);team.patrolGuards.add(v.getUuidAsString());memory.markDirty();
            }
        }
        if(team.patrolGuards.size()<patrolLimit){
            VillagerEntity v=all.stream().filter(x->!RailDriver.isDriver(x)&&!x.hasVehicle()&&x.getVillagerData().profession().matchesKey(VillagerProfession.NONE)
                &&!x.getCommandTags().contains(BANDIT)&&!x.getCommandTags().contains(GUARD)&&!Oprichniki.isOfficer(x)).findFirst().orElse(null);
            if(v==null&&w.getTime()%100==0){
                for(int i=0;i<20;i++){
                    int x=team.anchor.getX()+w.random.nextBetween(-12,12),z=team.anchor.getZ()+w.random.nextBetween(-12,12);
                    BlockPos p=new BlockPos(x,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z);
                    if(!w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(p).toLong())||!w.getBlockState(p).isAir()||!w.getBlockState(p.up()).isAir()
                        ||!w.getBlockState(p.down()).isSolidBlock(w,p.down())||!w.getOtherEntities(null,new Box(p),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())continue;
                    v=EntityType.VILLAGER.create(w,SpawnReason.STRUCTURE);if(v==null)break;v.refreshPositionAndAngles(x+.5,p.getY(),z+.5,0,0);
                    v.initialize(w,w.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);if(!w.spawnEntity(v))v=null;break;
                }
            }
            if(v!=null){
                v.addCommandTag(GUARD);v.addCommandTag(owner);v.setPersistent();v.setVillagerData(v.getVillagerData().withProfession(GuardProfession.ENTRY));
                v.setExperience(1);v.reinitializeBrain(w);v.setCustomName(Text.translatable("entity.halloween.guard"));
                v.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.CHAINMAIL_HELMET));v.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                v.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.CHAINMAIL_LEGGINGS));v.equipStack(EquipmentSlot.FEET,new ItemStack(Items.CHAINMAIL_BOOTS));
                v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(team.patrolGuards.size()%3==1?Items.CROSSBOW:Items.IRON_SWORD));team.patrolGuards.add(v.getUuidAsString());memory.markDirty();
            }
        }
        if(team.patrolGuards.isEmpty())return;
        java.util.UUID riderId;try{riderId=java.util.UUID.fromString(team.patrolGuards.getFirst());}catch(IllegalArgumentException ex){return;}
        if(!(w.getEntity(riderId) instanceof VillagerEntity rider)||!rider.isAlive()||VillageDefense.escorting(rider)||VillageDefense.engaged(rider)||rider.getCommandTags().contains("halloween_executor")||rider.getMainHandStack().isOf(Items.LEAD))return;
        if(!rider.getCommandTags().contains("halloween_issued_mount"))return;
        for(String tag:List.copyOf(rider.getCommandTags()))if(tag.startsWith("halloween_mount_blocked_")){
            try{if(w.getTime()<Long.parseLong(tag.substring("halloween_mount_blocked_".length())))return;}catch(NumberFormatException ignored){}rider.removeCommandTag(tag);
        }
        if(rider.getVehicle() instanceof net.minecraft.entity.passive.HorseEntity horse){
            var speed=horse.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED);if(speed!=null&&speed.getBaseValue()<.28)speed.setBaseValue(.28);
            if(w.getTime()%40==0&&horse.getNavigation().isIdle()){
                int x=team.anchor.getX()+w.random.nextBetween(-28,28),z=team.anchor.getZ()+w.random.nextBetween(-28,28);
                BlockPos p=new BlockPos(x,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z);
                if(w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(p).toLong()))horse.getNavigation().startMovingTo(x+.5,p.getY(),z+.5,.9);
            }
        }else if(w.getTime()%200==0){
            var near=w.getEntitiesByClass(net.minecraft.entity.passive.HorseEntity.class,new Box(rider.getBlockPos()).expand(8),h->h.getCommandTags().contains(owner)&&!h.hasPassengers());
            net.minecraft.entity.passive.HorseEntity horse=near.isEmpty()?null:near.getFirst();
            if(horse==null){
                BlockPos p=rider.getBlockPos();if(!w.getBlockState(p.up(2)).isAir())return;
                horse=EntityType.HORSE.create(w,SpawnReason.STRUCTURE);if(horse==null)return;
                horse.refreshPositionAndAngles(p.getX()+1.5,p.getY(),p.getZ()+.5,0,0);horse.initialize(w,w.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);
                horse.setTame(true);horse.setPersistent();horse.addCommandTag(owner);horse.equipStack(EquipmentSlot.SADDLE,new ItemStack(Items.SADDLE));
                horse.equipStack(EquipmentSlot.BODY,new ItemStack(rider.getCommandTags().contains("halloween_mount_copper")?Items.COPPER_HORSE_ARMOR:Items.IRON_HORSE_ARMOR));if(!w.spawnEntity(horse))return;
            }
            rider.startRiding(horse,true,true);
        }
    }
    private static void updateElection(ServerWorld w,VillageMemory memory,VillageMemory.Team team,List<VillagerEntity> all){
        if(team.electionStage==0||team.electionStage==2){var bar=VOTE_BARS.remove(team.village);if(bar!=null)bar.clearPlayers();}
        if(team.electionStage==0)return;
        // Recover elections saved by older versions with a future start time.
        if(team.electionStage==1&&w.getTimeOfDay()<team.electionAt){team.electionAt=w.getTimeOfDay();memory.markDirty();}
        var square=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(team.anchor).expand(128),m->m.getCommandTags().contains("halloween_square"))
            .stream().min(java.util.Comparator.comparingDouble(m->m.squaredDistanceTo(Vec3d.ofCenter(team.anchor)))).orElse(null);
        if(square==null)return;
        long elapsed=w.getTimeOfDay()-team.electionAt;
        if(team.electionStage==1||team.electionStage==3){
            var bar=VOTE_BARS.computeIfAbsent(team.village,k->new net.minecraft.entity.boss.ServerBossBar(Text.literal("Идёт голосование"),net.minecraft.entity.boss.BossBar.Color.PINK,net.minecraft.entity.boss.BossBar.Style.PROGRESS));
            for(var player:w.getPlayers())if(player.squaredDistanceTo(square)<128*128)bar.addPlayer(player);else bar.removePlayer(player);
            bar.setPercent(team.electionStage==1?Math.min(.5f,Math.max(0,elapsed/1200f)):Math.min(1f,Math.max(.5f,1f+(w.getTimeOfDay()-team.electionAt)/1200f)));
        }
        RoyalCourt.ensureDeputy(w,square);
        if(team.electionStage==1)RoyalCourt.ensureLegacyTribunes(w,square);
        var deputies=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(96),v->v.isAlive()&&v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY));
        VillagerEntity deputy=deputies.stream().min(java.util.Comparator.comparingDouble(v->v.squaredDistanceTo(square))).orElse(null);
        var points=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune"));
        var attendees=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(112,48,112),v->v.isAlive()&&!v.getCommandTags().contains(BANDIT)
            &&!v.getCommandTags().contains(GUARD)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
            &&!RoyalProfession.isKing(v));
        if(team.electionStage==1){
            var bell=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(32),m->m.getCommandTags().contains("halloween_bell")).stream().findFirst().orElse(square);
            var podium=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(32),m->m.getCommandTags().contains("halloween_podium")).stream().findFirst().orElse(square);
            if(elapsed<60)w.playSound(null,bell.getBlockPos(),net.minecraft.sound.SoundEvents.BLOCK_BELL_USE,net.minecraft.sound.SoundCategory.BLOCKS,2,1);
            if(deputy!=null){if(deputy.isSleeping())deputy.wakeUp();BlockPos goal=elapsed<60?bell.getBlockPos():podium.getBlockPos();
                deputy.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(goal,.55f,1),60);
                if(deputy.getNavigation().isIdle()||deputy.age%40==0)deputy.getNavigation().startMovingTo(goal.getX()+.5,goal.getY(),goal.getZ()+.5,.55);}
            if(points.isEmpty())points.add(square);
            points.sort(java.util.Comparator.comparing(m->m.getUuid()));
            int ready=0,index=0;
            attendees.sort(java.util.Comparator.comparing(VillagerEntity::getUuid));
            for(var v:attendees){
                var spot=points.get((index++)%points.size());
                if(v.isSleeping())v.wakeUp();
                double dx=v.getX()-spot.getX(),dz=v.getZ()-spot.getZ();
                if(dx*dx+dz*dz<6.25&&Math.abs(v.getY()-spot.getY())<2){ready++;v.getNavigation().stop();v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);continue;}
                v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(spot.getBlockPos(),.55f,1),60);
                if(v.getNavigation().isIdle()||v.age%40==0)v.getNavigation().startMovingTo(spot.getX(),spot.getY(),spot.getZ(),.55);
            }
            if(elapsed>=600&&!attendees.isEmpty()){
                VillagerEntity candidate=attendees.stream().filter(v->!v.isBaby()).min(java.util.Comparator.comparingDouble(v->points.stream().mapToDouble(v::squaredDistanceTo).min().orElse(Double.MAX_VALUE))).orElse(null);
                if(candidate==null)return;candidate.addCommandTag("halloween_candidate");team.electionStage=3;team.electionAt=w.getTimeOfDay()+600;memory.markDirty();
            }
        }else if(team.electionStage==3){
            points.sort(java.util.Comparator.comparing(m->m.getUuid()));attendees.sort(java.util.Comparator.comparing(VillagerEntity::getUuid));
            if(!points.isEmpty())for(int i=0;i<attendees.size();i++){
                VillagerEntity v=attendees.get(i);var spot=points.get(i%points.size());
                if(w.getTimeOfDay()>=team.electionAt&&v.getCommandTags().contains("halloween_candidate"))continue;
                if(v.squaredDistanceTo(spot)<=6.25){v.getNavigation().stop();v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);}
                else {
                    v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(spot.getBlockPos(),.5f,1),60);
                    if(v.getNavigation().isIdle()||v.age%40==0)v.getNavigation().startMovingTo(spot.getX(),spot.getY(),spot.getZ(),.5);
                }
            }
            if(w.getTimeOfDay()<team.electionAt)return;
            VillagerEntity candidate=attendees.stream().filter(v->v.getCommandTags().contains("halloween_candidate")).findFirst().orElse(null);
            if(candidate==null){candidate=attendees.stream().filter(v->!v.isBaby()).min(java.util.Comparator.comparingDouble(v->v.squaredDistanceTo(square))).orElse(null);if(candidate==null)return;}
            var bell=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(32),m->m.getCommandTags().contains("halloween_bell")).stream().findFirst().orElse(square);
            if(candidate.squaredDistanceTo(bell)>16 && w.getTimeOfDay()<team.electionAt+200){
                candidate.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(bell.getBlockPos(),.55f,1),60);
                if(candidate.getNavigation().isIdle())candidate.getNavigation().startMovingTo(bell.getX(),bell.getY(),bell.getZ(),.55);
                return;
            }
            if(candidate.squaredDistanceTo(bell)>16){
                BlockPos base=bell.getBlockPos();boolean moved=false;
                for(int dy=-2;dy<=3&&!moved;dy++)for(int dx=-3;dx<=3&&!moved;dx++)for(int dz=-3;dz<=3&&!moved;dz++){
                    BlockPos p=base.add(dx,dy,dz);if(w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()&&w.getBlockState(p.down()).isSolidBlock(w,p.down())){candidate.requestTeleport(p.getX()+.5,p.getY(),p.getZ()+.5);moved=true;}
                }
                if(!moved)return;
            }
            candidate.getNavigation().stop();candidate.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
            var bar=VOTE_BARS.remove(team.village);if(bar!=null)bar.clearPlayers();
            candidate.removeCommandTag("halloween_candidate");
            candidate.setVillagerData(candidate.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getEntry(net.minecraft.registry.Registries.VILLAGER_PROFESSION.get(RoyalProfession.KEY))));
            candidate.setExperience(1);candidate.reinitializeBrain(w);candidate.equipStack(EquipmentSlot.HEAD,ItemStack.EMPTY);RoyalCoronation.begin(w,candidate);
            for(var player:w.getPlayers())player.sendMessage(Text.translatable(RoyalProfession.isPresident(candidate)?"message.halloween.president_elected":"message.halloween.king_elected",candidate.getDisplayName()),false);
            team.electionStage=team.royalKiller.isEmpty()?0:2;memory.markDirty();
        }else if(team.electionStage==2){
            java.util.UUID id;try{id=java.util.UUID.fromString(team.royalKiller);}catch(IllegalArgumentException ex){team.electionStage=0;memory.markDirty();return;}
            net.minecraft.entity.Entity murderer=w.getEntity(id);
            if(murderer instanceof VillagerEntity accused&&accused.isAlive()&&(accused.getCommandTags().stream().anyMatch(tag->tag.startsWith("halloween_trial_"))||accused.getCommandTags().contains("halloween_execution_pending")))return;
            if(murderer==null)return;
            if(!(murderer instanceof VillagerEntity criminal)||!criminal.isAlive()){
                team.electionStage=0;team.royalKiller="";memory.markDirty();return;
            }
            VillagerEntity guard=all.stream().filter(v->v.getCommandTags().contains(GUARD)&&!v.getCommandTags().contains(BODYGUARD))
                .min(java.util.Comparator.comparingDouble(v->v.squaredDistanceTo(criminal))).orElse(null);
            if(guard==null)return;guard.addCommandTag("halloween_executor");
            if(criminal.getLeashHolder()!=guard){
                criminal.setAiDisabled(false);criminal.removeCommandTag(PRISONER);
                if(guard.squaredDistanceTo(criminal)>4){pursue(guard,criminal);return;}
                if(criminal.isLeashed())criminal.detachLeashWithoutDrop();
                criminal.addCommandTag(SURRENDER);criminal.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);criminal.attachLeash(guard,true);
            }
            if(guard.squaredDistanceTo(square)>9){if(guard.getNavigation().isIdle()||guard.age%60==0)guard.getNavigation().startMovingTo(square.getX(),square.getY(),square.getZ(),.55);return;}
            if(criminal.squaredDistanceTo(square)>16)return;
            criminal.detachLeashWithoutDrop();guard.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            guard.swingHand(Hand.MAIN_HAND);criminal.damage(w,w.getDamageSources().mobAttack(guard),1000);
            guard.removeCommandTag("halloween_executor");team.electionStage=0;team.royalKiller="";memory.markDirty();
        }
    }
    private static void ensureBodyguards(ServerWorld world,VillagerEntity king,List<VillagerEntity> nearby){
        String owner="halloween_king_"+king.getUuidAsString();
        long count=nearby.stream().filter(v->v.getCommandTags().contains(BODYGUARD)&&v.getCommandTags().contains(owner)).count();
        if(count>=2)return;
        for(int n=0;n<12;n++){
            int x=king.getBlockX()+world.random.nextBetween(-4,4),z=king.getBlockZ()+world.random.nextBetween(-4,4);
            BlockPos p=new BlockPos(x,king.getBlockY(),z);
            if(!world.isChunkLoaded(new net.minecraft.util.math.ChunkPos(p).toLong())||!world.getBlockState(p).isAir()||!world.getBlockState(p.up()).isAir()
                    ||!world.getBlockState(p.down()).isSolidBlock(world,p.down()))continue;
            VillagerEntity guard=EntityType.VILLAGER.create(world,SpawnReason.STRUCTURE);if(guard==null)return;
            guard.refreshPositionAndAngles(x+.5,p.getY(),z+.5,world.random.nextFloat()*360,0);
            guard.initialize(world,world.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);
            guard.setVillagerData(guard.getVillagerData().withProfession(GuardProfession.ROYAL_ENTRY));guard.setExperience(1);guard.reinitializeBrain(world);
            guard.addCommandTag(GUARD);guard.addCommandTag(BODYGUARD);guard.addCommandTag(owner);guard.setPersistent();
            guard.setCustomName(Text.translatable("entity.halloween.bodyguard"));
            guard.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));guard.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
            guard.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));guard.equipStack(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
            guard.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));world.spawnEntity(guard);return;
        }
    }
    private static void pursue(VillagerEntity a,VillagerEntity b){
        if(a.getVehicle() instanceof net.minecraft.entity.passive.HorseEntity horse){if(horse.getNavigation().isIdle()||a.age%60==0)horse.getNavigation().startMovingTo(b,.8);}
        else if(a.getNavigation().isIdle()||a.age%60==0)a.getNavigation().startMovingTo(b,0.7);
    }
    private static void spawnBandit(ServerWorld w,BlockPos origin){
        int x=origin.getX()+w.random.nextBetween(-25,25),z=origin.getZ()+w.random.nextBetween(-25,25);
        BlockPos pos=new BlockPos(x,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z);
        if(!w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(pos).toLong())||!w.getBlockState(pos).isAir()||!w.getBlockState(pos.up()).isAir())return;
        VillagerEntity v=EntityType.VILLAGER.create(w,SpawnReason.STRUCTURE);if(v==null)return;
        v.refreshPositionAndAngles(x+.5,pos.getY(),z+.5,w.random.nextFloat()*360,0);
        v.initialize(w,w.getLocalDifficulty(pos),SpawnReason.STRUCTURE,null);v.addCommandTag(BANDIT);
        if(w.random.nextBoolean())v.addCommandTag("halloween_thief");w.spawnEntity(v);
    }
    private VillageJustice(){}
}



