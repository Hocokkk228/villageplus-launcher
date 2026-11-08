package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Player warrants and custody survive reconnects through player command tags. */
final class PlayerJustice {
    private static final String WANTED="halloween_player_wanted", CUSTODY="halloween_player_custody", JAILED="halloween_player_jailed", ESCAPED="halloween_player_escaped";
    private static final String DEADLINE="halloween_player_deadline_", GUARD="halloween_player_guard_", PHASE="halloween_player_phase_";
    private record ChestWatch(BlockPos pos,int count){}
    private static final Map<UUID,ChestWatch> WATCH=new HashMap<>();
    private static final Map<UUID,Long> ATTACKS=new HashMap<>();
    private static final Map<String,Long> ASSAULTS=new HashMap<>();
    static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((dead,source)->{
            if(dead instanceof ServerPlayerEntity player){
                if(player.getEntityWorld() instanceof ServerWorld world){var escort=guard(world,player);if(escort!=null)escort.removeCommandTag("halloween_player_guarding");}
                clear(player);
            }
            if(dead instanceof VillagerEntity escort&&dead.getEntityWorld() instanceof ServerWorld world)for(var player:world.getPlayers())if(value(player,GUARD).equals(escort.getUuidAsString()))loseEscort(player);
            if(dead instanceof VillagerEntity victim && source.getAttacker() instanceof ServerPlayerEntity p && victim.getEntityWorld() instanceof ServerWorld w){
                String key=p.getUuidAsString()+":"+victim.getUuidAsString();Long assault=ASSAULTS.remove(key);
                if(assault!=null&&w.getTime()-assault<200){
                    putTime(p,"halloween_player_days_",Math.max(0,number(p,"halloween_player_days_")-1));
                    long left=Math.max(0,number(p,"halloween_player_count_assault_")-1);putTime(p,"halloween_player_count_assault_",left);
                    if(left==0)p.removeCommandTag("halloween_player_crime_assault");
                    removeAssaultCase(p,victim.getUuid());
                }
                warrant(p,RoyalProfession.isKing(victim)?"убийство короля":"убийство",victim.getName().getString(),victim.getUuid(),victim.getBlockPos());
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(amount>0&&entity instanceof VillagerEntity victim&&source.getAttacker() instanceof ServerPlayerEntity player&&victim.getEntityWorld() instanceof ServerWorld world){
                if (VillageBounties.suspect(victim)) return true;
                String key=player.getUuidAsString()+":"+victim.getUuidAsString();long last=ASSAULTS.getOrDefault(key,-1000L);
                if(world.getTime()-last>=200){ASSAULTS.put(key,world.getTime());warrant(player,VillageDefense.isGuard(victim)?"нападение на охрану":"нападение на жителя",victim.getName().getString(),victim.getUuid(),victim.getBlockPos());}
            }
            return true;
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if (player instanceof ServerPlayerEntity p && world instanceof ServerWorld w
                    && w.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.inventory.Inventory chest) {
                if (chest instanceof net.minecraft.block.entity.BarrelBlockEntity barrel) {
                    if (hand == Hand.MAIN_HAND) {
                        var sale = ArmsDealers.interact(w, p, hit.getBlockPos(), barrel);
                        if (sale != ActionResult.PASS) return sale;
                    }
                    var stall = MarketCrime.find(w, hit.getBlockPos());
                    if (stall != null) {
                        WATCH.remove(p.getUuid());
                        return MarketCrime.interact(w, p, hand, stall, barrel);
                    }
                }
                if (villageChest(w, hit.getBlockPos())) WATCH.put(p.getUuid(),new ChestWatch(hit.getBlockPos(),count(chest)));
            }
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,be)->{
            if(player instanceof ServerPlayerEntity p&&p.getCommandTags().contains(JAILED)&&state.isOf(Blocks.IRON_BARS)){
                p.addCommandTag("halloween_player_bars_broken");
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->removeLead(handler.player));
        ServerTickEvents.END_WORLD_TICK.register(PlayerJustice::tick);
    }
    private static int count(net.minecraft.inventory.Inventory chest){int n=0;for(int i=0;i<chest.size();i++)n+=chest.getStack(i).getCount();return n;}
    static boolean witnessed(ServerWorld w,ServerPlayerEntity p){return !w.getEntitiesByClass(VillagerEntity.class,new Box(p.getBlockPos()).expand(16),v->v.isAlive()&&!v.isSleeping()&&!v.getCommandTags().contains("halloween_bandit")&&v.canSee(p)).isEmpty();}
    private static boolean nearVillage(ServerWorld w,BlockPos p){
        if(w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.stream().anyMatch(t->t.anchor.isWithinDistance(p,192)))return true;
        return !w.getEntitiesByClass(MarkerEntity.class,new Box(p).expand(160,64,160),m->m.getCommandTags().contains("halloween_square")||m.getCommandTags().contains("halloween_jail")).isEmpty();
    }
    private static boolean villageChest(ServerWorld w,BlockPos p){return w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().anyMatch(h->p.getX()>=h.min.getX()&&p.getX()<=h.max.getX()&&p.getY()>=h.min.getY()&&p.getY()<=h.max.getY()&&p.getZ()>=h.min.getZ()&&p.getZ()<=h.max.getZ()&&h.blueprint.stream().anyMatch(r->r.pos().equals(p)&&r.state().getBlock() instanceof ChestBlock))||nearVillage(w,p);}
    private static String chestOwner(ServerWorld w,BlockPos pos){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var h=memory.houses.stream().filter(a->a.kind.equals("house")&&pos.getX()>=a.min.getX()&&pos.getX()<=a.max.getX()&&pos.getZ()>=a.min.getZ()&&pos.getZ()<=a.max.getZ()).findFirst().orElse(null);
        if(h==null)return "хозяин неизвестен";
        return w.getEntitiesByClass(VillagerEntity.class,new Box(pos).expand(96),v->v.isAlive()&&v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME).map(home->home.pos().getX()>=h.min.getX()&&home.pos().getX()<=h.max.getX()&&home.pos().getZ()>=h.min.getZ()&&home.pos().getZ()<=h.max.getZ()).orElse(false)).stream().findFirst().map(v->v.getName().getString()).orElse("хозяин неизвестен");
    }
    static int wantedLevel(long murder,long theft,long king){if(king>0||murder>=3||theft>=3)return 3;if(murder>=1||theft>=2)return 2;return 1;}
    static int wantedLevel(ServerPlayerEntity p){return wantedLevel(number(p,"halloween_player_count_murder_"),number(p,"halloween_player_count_theft_"),number(p,"halloween_player_count_king_"));}
    private static void removeAssaultCase(ServerPlayerEntity p,UUID victim){for(String t:List.copyOf(p.getCommandTags()))if(t.startsWith("halloween_player_case_")&&t.contains(":assault:"+victim+":")){p.removeCommandTag(t);break;}}
    static void smugglingWarrant(ServerPlayerEntity player, BlockPos station) {
        smugglingWarrant(player, station, false);
    }

    static void smugglingWarrant(ServerPlayerEntity player, BlockPos checkpoint, boolean sea) {
        warrant(player, "контрабанду оружия", sea ? "портовый досмотр" : "вокзальный досмотр", null, checkpoint);
    }

    static void marketWarrant(ServerPlayerEntity player, MarketCrime.Stall stall) {
        warrant(player, "ограбление", stall.name(), null, stall.barrel());
    }

    private static void warrant(ServerPlayerEntity p,String crime){warrant(p,crime,"неизвестно",null,p.getBlockPos());}
    private static void warrant(ServerPlayerEntity p,String crime,String victim,UUID victimId,BlockPos location){
        if(p.isCreative()||p.isSpectator()||p.getCommandTags().contains(JAILED))return;
        boolean fresh=!p.getCommandTags().contains(WANTED);
        p.removeCommandTag(ESCAPED);p.removeCommandTag("halloween_player_warned");p.removeCommandTag("halloween_player_house_ambush");
        p.addCommandTag(WANTED);
        String code=switch(crime){case "убийство короля"->"king";case "убийство"->"murder";case "нападение на охрану","нападение на жителя"->"assault";case "контрабанду оружия"->"smuggling";default->"theft";};
        p.addCommandTag("halloween_player_crime_"+code);
        putTime(p,"halloween_player_count_"+code+"_",number(p,"halloween_player_count_"+code+"_")+1);
        int days=code.equals("king")?0:code.equals("murder")||code.equals("theft")?5:1;
        long serial=number(p,"halloween_player_case_serial_")+1;putTime(p,"halloween_player_case_serial_",serial);
        String encoded=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(victim.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        p.addCommandTag("halloween_player_case_"+serial+":"+code+":"+(victimId==null?"unknown":victimId)+":"+encoded+":"+location.asLong());
        if(p.getEntityWorld() instanceof ServerWorld world){
            var injured=victimId==null?null:world.getEntity(victimId);
            var living=injured instanceof net.minecraft.entity.LivingEntity v?v:null;
            CourtTestimony.observe(world,p,living,location,injured instanceof VillagerEntity v&&v.getTarget()==p);
        }
        putTime(p,"halloween_player_days_",Math.max(0,number(p,"halloween_player_days_"))+days);
        if(fresh){p.sendMessage(Text.literal("§4§lВ РОЗЫСКЕ"),true);}
        p.sendMessage(Text.literal("§cВы объявлены в розыск за "+crime+". Звёзд: "+wantedLevel(p)),false);
    }
    private static void tick(ServerWorld w){
        for(var p:w.getPlayers())if(p.isCreative()||p.isSpectator()){if(p.getCommandTags().contains(WANTED)||p.getCommandTags().contains(CUSTODY)||p.getCommandTags().contains(JAILED))clear(p);}

        // Clean up anchors left by version 0.26 without dropping duplicate leads.
        if(w.getTime()%100==0)for(var player:w.getPlayers())for(var chicken:w.getEntitiesByClass(net.minecraft.entity.passive.ChickenEntity.class,new Box(player.getBlockPos()).expand(128),c->c.getCommandTags().contains("halloween_custody_lead"))){
            if(chicken.isLeashed())chicken.detachLeashWithoutDrop();chicken.discard();
        }
        for(ServerPlayerEntity p:w.getPlayers())if(p.getCommandTags().contains(CUSTODY)&&!p.getCommandTags().contains("halloween_player_cage_inside")&&!p.getCommandTags().contains("halloween_player_remand_inside")){
            String id=value(p,GUARD);VillagerEntity escort=null;try{if(w.getEntity(UUID.fromString(id)) instanceof VillagerEntity g&&g.isAlive())escort=g;}catch(IllegalArgumentException ignored){}
            if(escort==null){loseEscort(p);continue;}
            makeLead(w,p,escort);
            if(p.getCommandTags().contains("halloween_player_entering_cell")){var cell=VillagePrisons.assigned(p);if(cell!=null)pullTowardCell(p,cell);else p.removeCommandTag("halloween_player_entering_cell");}
            else pull(p,escort);
        }
        if(w.getTime()%5!=0)return;
        for(ServerPlayerEntity p:w.getPlayers()){
            if(p.isCreative()||p.isSpectator())continue;
            ChestWatch watch=WATCH.get(p.getUuid());
            if(watch!=null){if(!(w.getBlockEntity(watch.pos()) instanceof net.minecraft.inventory.Inventory chest)||p.squaredDistanceTo(Vec3d.ofCenter(watch.pos()))>100)WATCH.remove(p.getUuid());
                else{int n=count(chest);if(n<watch.count()&&witnessed(w,p)){warrant(p,"ограбление",chestOwner(w,watch.pos()),null,watch.pos());VillageFights.robbery(w,p,watch.pos());WATCH.remove(p.getUuid());}else WATCH.put(p.getUuid(),new ChestWatch(watch.pos(),n));}}
            if(p.getCommandTags().contains(JAILED)){
                if(VillagePrisons.escaped(p)){VillagePrisons.open(w,p);{p.removeCommandTag(JAILED);p.removeCommandTag("halloween_player_bars_broken");p.addCommandTag(ESCAPED);p.addCommandTag(WANTED);putTime(p,DEADLINE,w.getTime()+3600);p.sendMessage(Text.literal("§cПобег! Скройтесь от стражи на три минуты."),false);}}
                if(p.getCommandTags().contains(JAILED)&&w.getTime()>=time(p,DEADLINE)){clear(p);p.sendMessage(Text.literal("§aСрок заключения окончен."),false);}continue;}
            if(w.getTime()%20==0)WantedNetwork.send(p,p.getCommandTags().contains(WANTED)&&!p.getCommandTags().contains(JAILED),sentenceDays(p),crimeSummary(p));
            if(!p.getCommandTags().contains(WANTED))continue;
            var guard=guard(w,p);
            if(p.getCommandTags().contains(ESCAPED)&&w.getTime()>=time(p,DEADLINE)){clear(p);p.sendMessage(Text.literal("§aВы скрылись от розыска."),false);continue;}
            if(p.getCommandTags().contains(CUSTODY)){custody(w,p,guard);continue;}
            if(guard==null)continue;
            if(p.getCommandTags().contains(ESCAPED)&&guard.squaredDistanceTo(p)<=36&&guard.canSee(p))putTime(p,DEADLINE,w.getTime()+3600);
            if(!p.getCommandTags().contains("halloween_player_warned")){putTime(p,"halloween_player_warning_at_",w.getTime());p.sendMessage(Text.literal("§eГвардия: Присядьте и не двигайтесь!"),false);p.addCommandTag("halloween_player_warned");}
            var hiding=wantedLevel(p)>=3?null:hideout(w,p);
            if(hiding!=null)p.addCommandTag("halloween_player_house_ambush");
            boolean ambush=hiding==null&&p.getCommandTags().contains("halloween_player_house_ambush");
            boolean surrender=p.isSneaking()&&p.getVelocity().horizontalLengthSquared()<.01||ambush;
            BlockPos ambushPost=hiding==null?null:houseExit(hiding,p.getBlockPos());
            var guards=availableGuards(w);
            for(var pursuer:guards){
                if(!VillageDefense.assigned(w,pursuer,p))continue;
                VillageDefense.combat(pursuer);
                if(pursuer.isSleeping())pursuer.wakeUp();
                if(ambushPost!=null){pursuer.stopRiding();if(pursuer.squaredDistanceTo(Vec3d.ofCenter(ambushPost))>2.25)move(pursuer,ambushPost,.8f);else pursuer.getNavigation().stop();continue;}
                if(pursuer.getVehicle() instanceof net.minecraft.entity.passive.HorseEntity horse)horse.getNavigation().startMovingTo(p,.9);
                else move(pursuer,p.getBlockPos(),.8f);
                if(!surrender&&(number(p,"halloween_player_count_assault_")+number(p,"halloween_player_count_murder_")>0||w.getTime()-time(p,"halloween_player_warning_at_")>=200)&&pursuer.getMainHandStack().isOf(Items.CROSSBOW)&&pursuer.squaredDistanceTo(p)<576&&pursuer.squaredDistanceTo(p)>9){VillageDefense.shoot(w,pursuer,p);continue;}
                if(!surrender&&(number(p,"halloween_player_count_assault_")+number(p,"halloween_player_count_murder_")>0||w.getTime()-time(p,"halloween_player_warning_at_")>=200)&&pursuer.squaredDistanceTo(p)<6.25
                    &&w.getTime()-ATTACKS.getOrDefault(pursuer.getUuid(),-100L)>=20){
                    ATTACKS.put(pursuer.getUuid(),w.getTime());pursuer.swingHand(Hand.MAIN_HAND);p.damage(w,w.getDamageSources().mobAttack(pursuer),3);
                }
            }
            if(!VillageCustody.busy(w,guard,p)&&hiding==null&&p.squaredDistanceTo(guard)<(ambush?16:9)&&surrender&&guard.canSee(p)){
                resetHearing(p);p.removeCommandTag("halloween_player_entering_cell");p.addCommandTag(CUSTODY);p.removeCommandTag(ESCAPED);p.removeCommandTag("halloween_player_house_ambush");guard.stopRiding();makeLead(w,p,guard);guard.addCommandTag("halloween_player_guarding");var sq=marker(w,p,"halloween_square");if(sq!=null){put(p,"halloween_player_square_",sq.getUuidAsString());}put(p,GUARD,guard.getUuidAsString());putTime(p,PHASE,ambush?w.getTime()+500:VillageTrials.nextCourtStart(w));p.sendMessage(Text.literal("§6Вы сдались. Охрана ведёт вас на суд."),false);
            }
        }
    }
    static VillageMemory.House hideout(ServerWorld w,ServerPlayerEntity p){
        if(w.isSkyVisible(p.getBlockPos().up()))return null;
        return w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().filter(h->h.kind.equals("house")&&insideHouse(h,p.getBlockPos())).findFirst().orElse(null);
    }
    private static boolean insideHouse(VillageMemory.House h,BlockPos p){return p.getX()>h.min.getX()&&p.getX()<h.max.getX()&&p.getZ()>h.min.getZ()&&p.getZ()<h.max.getZ()&&p.getY()>=h.min.getY()&&p.getY()<=h.max.getY();}
    static BlockPos houseExit(VillageMemory.House h,BlockPos from){
        var door=h.blueprint.stream().filter(r->r.state().getBlock() instanceof DoorBlock&&r.state().get(DoorBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER).min(Comparator.comparingDouble(r->r.pos().getSquaredDistance(from))).orElse(null);
        if(door==null)return h.min.add(-1,1,0);
        Direction facing=door.state().get(DoorBlock.FACING);
        BlockPos a=door.pos().offset(facing),b=door.pos().offset(facing.getOpposite());return insideHouse(h,a)?b:a;
    }
    static void resetHearing(ServerPlayerEntity p){
        CourtTestimony.reset(p,false);
        for(String tag:List.copyOf(p.getCommandTags()))if(tag.startsWith("halloween_player_court_")||tag.startsWith("halloween_player_cage_")||tag.startsWith("halloween_player_route_")||tag.startsWith("halloween_player_cell_arrival_")||tag.equals("halloween_player_sentenced")||tag.equals("halloween_player_execution")||tag.equals("halloween_player_bell"))p.removeCommandTag(tag);
    }
    private static void loseEscort(ServerPlayerEntity p){
        p.removeCommandTag("halloween_player_entering_cell");
        if(p.getEntityWorld() instanceof ServerWorld w){var square=assignedSquare(w,p);if(square!=null){VillageTrials.abandonCourt(w,square,p);}}
        removeLead(p);put(p,GUARD,"");p.removeCommandTag(CUSTODY);p.removeCommandTag("halloween_player_warned");
        resetHearing(p);
        p.addCommandTag(WANTED);
    }
    private static boolean remand(ServerWorld w,ServerPlayerEntity p,VillagerEntity guard,MarkerEntity square){
        final String active="halloween_player_remand",inside="halloween_player_remand_inside",returning="halloween_player_remand_return";
        if(p.getCommandTags().contains("halloween_player_sentenced"))return false;
        long hour=Math.floorMod(w.getTimeOfDay(),24000);
        if(!p.getCommandTags().contains(active)&&hour>=1000&&hour<12000)return false;
        var jail=w.getEntitiesByClass(MarkerEntity.class,new Box(square==null?p.getBlockPos():square.getBlockPos()).expand(square==null?384:160,96,square==null?384:160),m->m.getCommandTags().contains("halloween_jail")).stream().min(Comparator.comparingDouble(m->square==null?m.squaredDistanceTo(p):m.squaredDistanceTo(square))).orElse(null);
        if(jail==null)return false;
        if(p.addCommandTag(active)){putTime(p,"halloween_player_remand_idx_",0);p.sendMessage(Text.literal("§6Гвардия: До утреннего суда вы будете ждать в камере."),false);}
        if(p.getCommandTags().contains(inside)){
            removeLead(p);
            if(VillagePrisons.escaped(p)){
                VillageTrials.openCell(w,p);p.removeCommandTag(inside);p.removeCommandTag(active);p.removeCommandTag(CUSTODY);guard.removeCommandTag("halloween_player_guarding");p.addCommandTag(ESCAPED);putTime(p,DEADLINE,w.getTime()+3600);return true;
            }
            if(hour<1000||hour>=12000){
                // Accelerate daylight only when every active player is waiting in remand.
                if(w.getPlayers().stream().filter(q->!q.isSpectator()).allMatch(q->q.getCommandTags().contains(inside))&&w.getPlayers().stream().filter(q->q.getCommandTags().contains(inside)).findFirst().orElse(null)==p){
                    long distance=Math.floorMod(1000-hour,24000);w.setTimeOfDay(w.getTimeOfDay()+Math.min(35,distance));
                }
                return true;
            }
            if(square==null)return true;
            BlockPos post=VillagePrisons.roomPost(w,p);if(post==null)return true;
            if(guard.squaredDistanceTo(Vec3d.ofCenter(post))>4){move(guard,post,.65f);return true;}
            VillageTrials.openCell(w,p);VillageCustody.resetTrail(guard);p.removeCommandTag(inside);p.addCommandTag(returning);putTime(p,"halloween_player_remand_idx_",0);makeLead(w,p,guard);p.sendMessage(Text.literal("§6Гвардия: Утро. Следуйте за мной на суд."),false);return true;
        }
        if(p.getCommandTags().contains(returning)){
            var path=new ArrayList<>(VillagePrisons.escortRoute(w,jail,p));Collections.reverse(path);
            int idx=(int)number(p,"halloween_player_remand_idx_");
            if(idx<path.size()){
                var target=path.get(idx);if(p.squaredDistanceTo(guard)>12.25){guard.getNavigation().stop();pull(p,guard);return true;}
                if(guard.squaredDistanceTo(Vec3d.ofCenter(target))>4){move(guard,target,.65f);pull(p,guard);return true;}
                putTime(p,"halloween_player_remand_idx_",idx+1);return true;
            }
            if(square==null){p.removeCommandTag(returning);p.addCommandTag(inside);return true;}p.removeCommandTag(active);p.removeCommandTag(returning);putTime(p,PHASE,w.getTime()+500);return false;
        }
        var cell=VillagePrisons.cellFor(w,jail,p);if(cell==null){var entry=VillageTrials.jailEntrance(w,jail.getBlockPos());if(entry!=null){move(guard,entry,.65f);pull(p,guard);}return true;}
        if(withinCaptureRange(p.getEntityPos(),cell)&&placeCaptive(w,p,guard,cell)){
            VillageTrials.sealCell(w,p,cell);p.addCommandTag(inside);p.sendMessage(Text.literal("§7Вы помещены в камеру до суда. Приговор ещё не вынесен."),false);return true;
        }
        var route=VillagePrisons.escortRoute(w,jail,p);if(route.isEmpty()){var entry=VillageTrials.jailEntrance(w,jail.getBlockPos());if(entry!=null){move(guard,entry,.65f);pull(p,guard);}return true;}
        int idx=(int)Math.min(number(p,"halloween_player_remand_idx_"),route.size()-1);BlockPos goal=route.get(idx);
        if(p.squaredDistanceTo(guard)>12.25){guard.getNavigation().stop();pull(p,guard);return true;}
        if(guard.squaredDistanceTo(Vec3d.ofCenter(goal))>4){move(guard,goal,.65f);pull(p,guard);return true;}
        if(idx<route.size()-1){putTime(p,"halloween_player_remand_idx_",idx+1);return true;}
        var post=VillagePrisons.doorPost(w,p);if(post!=null&&guard.squaredDistanceTo(Vec3d.ofCenter(post))>2.25){move(guard,post,.6f);pull(p,guard);return true;}
        if(p.squaredDistanceTo(Vec3d.ofCenter(cell))>2.25){p.addCommandTag("halloween_player_entering_cell");pullTowardCell(p,cell);return true;}
        p.removeCommandTag("halloween_player_entering_cell");
        removeLead(p);VillageTrials.sealCell(w,p,cell);p.addCommandTag(inside);p.sendMessage(Text.literal("§7Вы помещены в камеру до суда. Приговор ещё не вынесен."),false);return true;
    }
    private static void custody(ServerWorld w,ServerPlayerEntity p,VillagerEntity guard){
        if(guard==null){loseEscort(p);return;}
        if(!value(p,GUARD).equals(guard.getUuidAsString())){
            if(p.squaredDistanceTo(guard)>9||Math.abs(p.getY()-guard.getY())>1.8){move(guard,p.getBlockPos(),.75f);return;}
            put(p,GUARD,guard.getUuidAsString());
        }
        if(VillageCustody.busy(w,guard,p)){loseEscort(p);return;}
        guard.addCommandTag("halloween_player_guarding");
        if(!p.getCommandTags().contains("halloween_player_cage_inside")&&!p.getCommandTags().contains("halloween_player_remand_inside"))makeLead(w,p,guard);
        var square=assignedSquare(w,p);if(remand(w,p,guard,square))return;
        if(square==null)return;
        if(!p.getCommandTags().contains("halloween_player_sentenced")){
            if(w.getTime()<time(p,PHASE)-500){move(guard,VillageTrials.waitingPost(square,p),.6f);pull(p,guard);return;}
        }
        if(!p.getCommandTags().contains("halloween_player_sentenced")&&!VillageTrials.claimCourt(w,square,p)){
            if(p.addCommandTag("halloween_player_court_waiting"))p.sendMessage(Text.literal("§6Конвой ожидает готовности зала суда."),false);
            move(guard,VillageTrials.waitingPost(square,p),.6f);pull(p,guard);return;
        }
        p.removeCommandTag("halloween_player_court_waiting");
        if(!p.getCommandTags().contains("halloween_player_sentenced")&&!p.getCommandTags().contains("halloween_player_court_announced")){
            p.addCommandTag("halloween_player_court_announced");putTime(p,PHASE,w.getTime()+500);VillageTrials.assemble(w,square);
        }
        long phase=time(p,PHASE);boolean sentenced=p.getCommandTags().contains("halloween_player_sentenced");
        if(p.getCommandTags().contains("halloween_player_execution")){executeSentence(w,p,guard,square);return;}
        MarkerEntity jailMarker=sentenced?w.getEntitiesByClass(MarkerEntity.class,new Box(square.getBlockPos()).expand(160,96,160),m->m.getCommandTags().contains("halloween_jail"))
            .stream().min(Comparator.comparingDouble(m->m.squaredDistanceTo(square))).orElse(null):null;
        BlockPos destination=sentenced?(jailMarker==null?null:VillagePrisons.cellFor(w,jailMarker,p)):VillageTrials.cagePosition(w,square);
        if(destination==null)return;
        boolean captureRange=withinCaptureRange(p.getEntityPos(),destination)&&(sentenced||VillageTrials.courtArrival(guard,destination));
        if(!captureRange&&!p.getCommandTags().contains("halloween_player_cage_inside")&&(p.squaredDistanceTo(guard)>12.25||Math.abs(p.getY()-guard.getY())>1.8)){
            guard.getNavigation().stop();if(Math.abs(p.getY()-guard.getY())>1.8)move(guard,p.getBlockPos(),.6f);else pull(p,guard);return;
        }
        var hall=VillageCourthouse.hall(w,square);
        if(!sentenced){
            if(hall!=null)CourtTestimony.guide(w,square,p);
            VillageTrials.guideAttendees(w,square);VillageTrials.holdMinister(w,square);VillageTrials.buildCage(w,destination);
            BlockPos guardPost=VillageTrials.standingPost(w,destination.add(2,0,-2));
            if(guard.squaredDistanceTo(Vec3d.ofCenter(destination))<1.5)VillageTrials.openCageDoor(w,destination);
            if(!captureRange&&!p.getCommandTags().contains("halloween_player_cage_inside")&&(!VillageTrials.atCourtLevel(guard.getEntityPos(),destination)||guard.squaredDistanceTo(Vec3d.ofCenter(guardPost))>9)){move(guard,guardPost,.7f);pull(p,guard);return;}
            guard.getNavigation().stop();guard.getBrain().forget(MemoryModuleType.WALK_TARGET);
            if(!captureRange){pull(p,guard);return;}
            if(!VillageTrials.cageReady(w,destination)||w.getTime()<phase)return;
            if(!p.getCommandTags().contains("halloween_player_cage_inside")&&!p.getCommandTags().contains("halloween_player_remand_inside")){
                if(hall!=null&&!VillageCourthouse.judgePresent(w,hall))return;
                if(!placeCaptive(w,p,guard,destination))return;p.addCommandTag("halloween_player_cage_inside");putTime(p,"halloween_player_cage_until_",w.getTime()+300);
                if(hall==null)for(var listener:w.getPlayers())listener.sendMessage(Text.literal("§eМинистр: Суд рассматривает обвинения против "+p.getName().getString()+". Выслушайте решение."),false);VillageTrials.closeCage(w,destination);return;
            }
            long remaining=time(p,"halloween_player_cage_until_")-w.getTime();
            if(hall!=null)CourtTestimony.speak(w,square,p,remaining,crimeSummary(p));
            if(remaining>0){
                if(hall==null&&w.getTime()>=time(p,"halloween_player_cage_until_")-150&&!p.getCommandTags().contains("halloween_player_court_speech_mid")){
                    p.addCommandTag("halloween_player_court_speech_mid");for(var listener:w.getPlayers())listener.sendMessage(Text.literal("§eМинистр: Обвинения рассмотрены: "+crime(p)+". Суд определяет наказание."),false);
                }
                if(p.squaredDistanceTo(Vec3d.ofCenter(destination))>2.25)p.requestTeleport(destination.getX()+.5,destination.getY(),destination.getZ()+.5);
                return;
            }
        }
        if(!sentenced&&!p.getCommandTags().contains("halloween_player_bell")){
            p.addCommandTag("halloween_player_bell");
            for(var other:w.getPlayers())other.sendMessage(Text.literal("§6Собрание: начинается суд над "+p.getName().getString()),false);
        }
        if(sentenced){
            if(jailMarker==null)return;
            if(captureRange){if(!placeCaptive(w,p,guard,destination))return;}
            else {
                List<BlockPos> route=VillagePrisons.escortRoute(w,jailMarker,p);
                if(route.isEmpty()){var entry=VillageTrials.jailEntrance(w,destination);if(entry!=null)move(guard,entry,.6f);pull(p,guard);return;}
                int index=(int)Math.min(number(p,"halloween_player_route_idx_"),route.size()-1);BlockPos waypoint=route.get(index);
                if(p.squaredDistanceTo(guard)>36){guard.getNavigation().stop();pull(p,guard);return;}
                if(guard.squaredDistanceTo(Vec3d.ofCenter(waypoint))>2.25){move(guard,waypoint,.6f);pull(p,guard);return;}
                if(index<route.size()-1){putTime(p,"halloween_player_route_idx_",index+1);return;}
                var post=VillagePrisons.doorPost(w,p);if(post!=null)move(guard,post,.55f);pull(p,guard);return;
            }
        }else{
            if(!p.getCommandTags().contains("halloween_player_cage_inside"))return;
        }
        if(!sentenced){
            if(hall!=null&&CourtTestimony.acquit(p)){
                CourtTestimony.announce(w,hall,"§aСудья: Вина не доказана. Подсудимого освободить.");
                VillageTrials.removeCage(w,destination);VillageTrials.releaseMinister(w,square);
                guard.removeCommandTag("halloween_player_guarding");CourtTestimony.reset(p,true);clear(p);return;
            }
            CourtTestimony.reset(p,true);
            p.addCommandTag("halloween_player_sentenced");p.removeCommandTag("halloween_player_cage_inside");VillageTrials.removeCage(w,destination);if(!p.getCommandTags().contains("halloween_player_crime_king"))VillageTrials.releaseMinister(w,square);makeLead(w,p,guard);
            for(var item:caseDetails(p))for(var listener:w.getPlayers())listener.sendMessage(Text.literal("§eСудья: "+item),false);
            if(p.getCommandTags().contains("halloween_player_crime_king")){
                p.addCommandTag("halloween_player_execution");
                for(var other:w.getPlayers())other.sendMessage(Text.literal("§eСудья: Лицо «"+p.getName().getString()+"» приговорено к смертной казни. Вердикт вынесен."),false);
                return;
            }
            String crime=crimeSummary(p);int days=sentenceDays(p);
            for(var other:w.getPlayers())other.sendMessage(Text.literal("§eМинистр: "+p.getName().getString()+" осуждён за "+crime+" на "+days+(days==1?" день":" дня")+" тюрьмы."),false);return;
        }
        guard.removeCommandTag("halloween_player_guarding");removeLead(p);p.removeCommandTag(CUSTODY);p.addCommandTag(JAILED);VillageTrials.sealCell(w,p,destination);int days=sentenceDays(p);putTime(p,DEADLINE,w.getTime()+days*24000L);placeChest(w,p);p.sendMessage(Text.literal("§cВы заключены на "+days+(days==1?" игровой день.":" игровых дня.")),false);
    }
    static boolean withinCaptureRange(Vec3d position,BlockPos destination){
        var center=Vec3d.ofBottomCenter(destination);
        return Math.abs(position.x-center.x)<=10&&Math.abs(position.y-center.y)<=10&&Math.abs(position.z-center.z)<=10;
    }
    static boolean placeCaptive(ServerWorld w,ServerPlayerEntity player,VillagerEntity guard,BlockPos destination){
        if(!withinCaptureRange(player.getEntityPos(),destination))return false;
        var center=Vec3d.ofBottomCenter(destination);
        if(!w.isSpaceEmpty(player,player.getBoundingBox().offset(center.subtract(player.getEntityPos()))))return false;
        removeLead(player);VillageCustody.resetTrail(guard);guard.getNavigation().stop();guard.getBrain().forget(MemoryModuleType.WALK_TARGET);
        player.removeCommandTag("halloween_player_entering_cell");player.setVelocity(Vec3d.ZERO);player.fallDistance=0;
        player.requestTeleport(center.x,center.y,center.z);
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        return true;
    }
    private static void pullTowardCell(ServerPlayerEntity p,BlockPos cell){
        Vec3d d=Vec3d.ofBottomCenter(cell).subtract(p.getEntityPos());double n=Math.sqrt(d.x*d.x+d.z*d.z);if(n<.3)return;double x=d.x/n*.18,z=d.z/n*.18;if(!p.getEntityWorld().isSpaceEmpty(p,p.getBoundingBox().offset(x,0,z)))return;p.setVelocity(x,p.getVelocity().y,z);p.velocityDirty=true;p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
    }
    private static void executeSentence(ServerWorld w,ServerPlayerEntity p,VillagerEntity escort,MarkerEntity square){
        VillageTrials.guideAttendees(w,square);VillageTrials.holdMinister(w,square);
        var bell=w.getEntitiesByClass(MarkerEntity.class,new Box(square.getBlockPos()).expand(32),m->m.getCommandTags().contains("halloween_bell"))
            .stream().min(Comparator.comparingDouble(square::squaredDistanceTo)).orElse(square);
        BlockPos destination=bell.getBlockPos();
        if(escort.squaredDistanceTo(bell)>9){move(escort,destination,.6f);pull(p,escort);return;}
        if(p.squaredDistanceTo(bell)>16){pull(p,escort);return;}
        escort.getNavigation().stop();
        var guards=w.getEntitiesByClass(VillagerEntity.class,new Box(destination).expand(24),VillageDefense::isGuard);
        if(!guards.contains(escort))guards.add(escort);
        for(var g:guards){
            if(g.squaredDistanceTo(p)>6.25){move(g,p.getBlockPos(),.8f);continue;}
            if(w.getTime()-ATTACKS.getOrDefault(g.getUuid(),-100L)>=20){
                ATTACKS.put(g.getUuid(),w.getTime());g.swingHand(Hand.MAIN_HAND);p.damage(w,w.getDamageSources().mobAttack(g),6);
            }
        }
    }
    private static String crime(ServerPlayerEntity p){
        if(p.getCommandTags().contains("halloween_player_crime_king"))return "убийство короля";
        if(p.getCommandTags().contains("halloween_player_crime_murder"))return "убийство";
        if(p.getCommandTags().contains("halloween_player_crime_theft"))return "ограбление";
        if(p.getCommandTags().contains("halloween_player_crime_smuggling"))return "контрабанда";
        return "нападение на охрану";
    }
    private static String crimeSummary(ServerPlayerEntity p){
        List<String> entries=new ArrayList<>();
        for(String code:List.of("king","murder","theft","smuggling","assault")){
            long count=number(p,"halloween_player_count_"+code+"_");if(count==0)continue;
            String label=switch(code){case "king"->"король";case "murder"->"убийства";case "theft"->"ограбления";case "smuggling"->"контрабанда";default->"нападения";};entries.add(label+": "+count);
        }
        return entries.isEmpty()?crime(p):String.join(", ",entries);
    }
    static List<String> caseDetails(ServerPlayerEntity p){
        List<String> result=new ArrayList<>();var tags=p.getCommandTags().stream().filter(t->t.startsWith("halloween_player_case_")&&!t.startsWith("halloween_player_case_serial_")).sorted(Comparator.comparingLong(t->{try{return Long.parseLong(t.substring("halloween_player_case_".length()).split(":")[0]);}catch(RuntimeException e){return Long.MAX_VALUE;}})).toList();
        for(String t:tags)try{var a=t.substring("halloween_player_case_".length()).split(":");String name=new String(java.util.Base64.getUrlDecoder().decode(a[3]),java.nio.charset.StandardCharsets.UTF_8);BlockPos place=BlockPos.fromLong(Long.parseLong(a[4]));String label=switch(a[1]){case "king"->"убийство короля";case "murder"->"убийство";case "theft"->"ограбление";case "smuggling"->"контрабанда";default->"нападение";};int days=a[1].equals("king")?0:(a[1].equals("assault")||a[1].equals("smuggling"))?1:5;result.add(label+": "+name+", место "+place.toShortString()+", "+(days==0?"смертная казнь":days+" дн."));}catch(RuntimeException ignored){}
        return result;
    }
    private static int sentenceDays(ServerPlayerEntity p){return (int)Math.max(1,Math.min(10000,number(p,"halloween_player_days_")));}
    private static long number(ServerPlayerEntity p,String prefix){try{return Long.parseLong(value(p,prefix));}catch(NumberFormatException e){return 0;}}
    static void makeLead(ServerWorld w,ServerPlayerEntity p,VillagerEntity guard){
        var leash=(net.minecraft.entity.Leashable)(Object)p;
        if(leash.getLeashHolder()!=guard){if(leash.isLeashed())leash.detachLeashWithoutDrop();leash.attachLeash(guard,true);
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket(p,guard));}
    }
    static boolean leadAttached(ServerWorld w,ServerPlayerEntity p,VillagerEntity guard){return ((net.minecraft.entity.Leashable)(Object)p).getLeashHolder()==guard;}
    static void removeLead(ServerPlayerEntity p){var leash=(net.minecraft.entity.Leashable)(Object)p;if(leash.isLeashed()){
        leash.detachLeashWithoutDrop();p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket(p,null));
    }else{leash.setLeashData(null);p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket(p,null));}}
    static void pull(ServerPlayerEntity p,VillagerEntity guard){VillageCustody.pull(p,guard);}
    private static void placeChest(ServerWorld w,ServerPlayerEntity p){VillagePrisons.provision(w,p);}
    private static VillagerEntity guard(ServerWorld w,ServerPlayerEntity p){
        String id=value(p,GUARD);if(!id.isEmpty())try{
            if(w.getEntity(UUID.fromString(id)) instanceof VillagerEntity g&&g.isAlive()){
                if(p.getCommandTags().contains(CUSTODY))return g;
                g.removeCommandTag("halloween_player_guarding");removeLead(p);
            }
        }catch(IllegalArgumentException ignored){}
        return availableGuards(w).stream().filter(v->VillageDefense.assigned(w,v,p))
            .min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
    }
    private static List<VillagerEntity> availableGuards(ServerWorld w){
        List<VillagerEntity> guards=new ArrayList<>();
        for(var entity:w.iterateEntities())if(entity instanceof VillagerEntity v&&VillageDefense.isGuard(v)
            &&!v.getCommandTags().contains("halloween_bodyguard")&&!v.getCommandTags().contains("halloween_castle_guard")&&!v.getCommandTags().contains("halloween_executor")&&!v.getCommandTags().contains("halloween_player_guarding")
            &&!v.getMainHandStack().isOf(Items.LEAD)&&!VillageDefense.escorting(v)&&!VillageCustody.busy(w,v,null))guards.add(v);
        return guards;
    }
    private static MarkerEntity assignedSquare(ServerWorld w,ServerPlayerEntity p){
        String id=value(p,"halloween_player_square_");
        if(!id.isEmpty())try{if(w.getEntity(UUID.fromString(id)) instanceof MarkerEntity m&&m.isAlive())return m;}catch(IllegalArgumentException ignored){}
        MarkerEntity m=marker(w,p,"halloween_square");if(m!=null)put(p,"halloween_player_square_",m.getUuidAsString());return m;
    }
    private static MarkerEntity marker(ServerWorld w,ServerPlayerEntity p,String tag){return w.getEntitiesByClass(MarkerEntity.class,new Box(p.getBlockPos()).expand(384),m->m.getCommandTags().contains(tag)).stream().min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);}
    private static void move(VillagerEntity g,BlockPos p,float speed){
        VillageEscort.move(g,p,speed);
    }
    private static String value(ServerPlayerEntity p,String prefix){return p.getCommandTags().stream().filter(t->t.startsWith(prefix)).map(t->t.substring(prefix.length())).findFirst().orElse("");}
    private static long time(ServerPlayerEntity p,String prefix){try{return Long.parseLong(value(p,prefix));}catch(NumberFormatException e){return Long.MAX_VALUE;}}
    private static void putTime(ServerPlayerEntity p,String prefix,long n){put(p,prefix,Long.toString(n));}
    private static void put(ServerPlayerEntity p,String prefix,String value){for(String t:List.copyOf(p.getCommandTags()))if(t.startsWith(prefix))p.removeCommandTag(t);p.addCommandTag(prefix+value);}
    static void clear(ServerPlayerEntity p){CourtTestimony.reset(p,true);if(p.getEntityWorld() instanceof ServerWorld courtWorld){var court=assignedSquare(courtWorld,p);if(court!=null)VillageTrials.abandonCourt(courtWorld,court,p);}if(p.getEntityWorld() instanceof ServerWorld world){String id=value(p,GUARD);try{if(world.getEntity(UUID.fromString(id)) instanceof VillagerEntity g)g.removeCommandTag("halloween_player_guarding");}catch(IllegalArgumentException ignored){}}removeLead(p);if(p.getEntityWorld() instanceof ServerWorld w)VillageTrials.openCell(w,p);WATCH.remove(p.getUuid());VillageCustody.release(wOrNull(p),p);for(String t:List.copyOf(p.getCommandTags()))if(t.startsWith("halloween_player_"))p.removeCommandTag(t);WantedNetwork.send(p,false,0,"");}
    private static ServerWorld wOrNull(ServerPlayerEntity p){return p.getEntityWorld();}
    private PlayerJustice(){}
}
