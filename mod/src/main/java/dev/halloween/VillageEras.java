package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

/** Work unlocks the next era; the construction queue decides when the transition is complete. */
final class VillageEras {
    static final List<String> PROFESSIONS=List.of("builder","farmer","armorer","weaponsmith","toolsmith","mason","fletcher","fisherman","shepherd","butcher","leatherworker","librarian","cartographer","cleric","guard","treasurer");
    private static final String XP="halloween_work_xp_";
    static int level(int xp){return xp>=1200?5:xp>=600?4:xp>=240?3:xp>=80?2:1;}
    static boolean residential(VillageMemory.House h){return h.kind.equals("house")&&h.blueprint.stream().anyMatch(r->r.state().getBlock() instanceof net.minecraft.block.BedBlock);}
    static int builderTarget(ServerWorld w,VillageMemory.Team t){var s=VillageEraData.get(w).settlement(t);return s.transitionAt>=0||s.era>=2?12:5;}
    static int personalLevel(VillagerEntity v){return level((int)tag(v,XP,0));}
    static long tag(VillagerEntity v,String prefix,long fallback){for(String t:v.getCommandTags())if(t.startsWith(prefix))try{return Long.parseLong(t.substring(prefix.length()));}catch(NumberFormatException ignored){}return fallback;}
    static void putTag(VillagerEntity v,String prefix,long value){for(String t:List.copyOf(v.getCommandTags()))if(t.startsWith(prefix))v.removeCommandTag(t);v.addCommandTag(prefix+value);}
    static String profession(VillagerEntity v){if(Oprichniki.isOfficer(v))return "oprichnik";if(VillageDefense.isGuard(v))return "guard";return Registries.VILLAGER_PROFESSION.getId(v.getVillagerData().profession().value()).getPath();}
    static VillageMemory.Team team(ServerWorld w,BlockPos pos){
        var data=VillageEraData.get(w);
        return w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.stream()
            .filter(t->{double radius=IndustrialCityGrowth.radius(data.settlements.get(t.village),384);return horizontal(t.anchor,pos)<=radius*radius;})
            .min(Comparator.comparingDouble(t->horizontal(t.anchor,pos))).orElse(null);
    }
    static double horizontal(BlockPos a,BlockPos b){double x=a.getX()-b.getX(),z=a.getZ()-b.getZ();return x*x+z*z;}
    static boolean belongs(ServerWorld w,VillagerEntity v,long village){
        String prefix="halloween_era_village_";for(String t:v.getCommandTags())if(t.startsWith(prefix))return t.equals(prefix+village);
        var team=team(w,v.getBlockPos());if(team==null)return false;v.addCommandTag(prefix+team.village);return team.village==village;
    }
    static VillageEraData.Settlement settlement(ServerWorld w,BlockPos pos){var team=team(w,pos);return team==null?null:VillageEraData.get(w).settlement(team);}
    static void work(ServerWorld w,VillagerEntity v,String profession,int points){
        if(points<=0)return;var team=team(w,v.getBlockPos());
        for(String t:v.getCommandTags())if(t.startsWith("halloween_era_village_"))try{long id=Long.parseLong(t.substring("halloween_era_village_".length()));team=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.stream().filter(a->a.village==id).findFirst().orElse(team);}catch(NumberFormatException ignored){}
        if(team==null)return;var data=VillageEraData.get(w);var s=data.settlement(team);
        s.xp.merge(profession,points,(a,b)->Math.min(10000000,a+b));putTag(v,XP,Math.min(10000000,tag(v,XP,0)+points));data.markDirty();
    }
    static int radius(ServerWorld w,BlockPos pos){var s=settlement(w,pos);return IndustrialCityGrowth.radius(s,s!=null&&(s.era>=VillageEpoch.STONE||s.transitionAt>=0)?320:160);}
    static boolean transitioning(ServerWorld w,BlockPos pos){var s=settlement(w,pos);return s!=null&&s.transitionAt>=0;}
    static void register(){
        ServerTickEvents.END_WORLD_TICK.register(VillageEras::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(CommandManager.literal("villageplus")
            .requires(source->source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
            .then(CommandManager.literal("second").executes(context->{
                var player=context.getSource().getPlayerOrThrow();var w=player.getEntityWorld();var t=team(w,player.getBlockPos());
                if(t==null){context.getSource().sendError(Text.literal("Рядом нет известной деревни."));return 0;}
                var data=VillageEraData.get(w);var s=data.settlement(t);for(String p:PROFESSIONS)s.xp.put(p,Math.max(240,s.xp.getOrDefault(p,0)));
                for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(t.anchor).expand(384,96,384),v->v.isAlive()&&belongs(w,v,t.village)))putTag(v,XP,Math.max(240,tag(v,XP,0)));
                data.markDirty();begin(w,t,s);context.getSource().sendFeedback(()->Text.literal("Профессии деревни «"+s.name+"» получили уровень 3. Началась подготовка каменного века."),true);return 1;
            })).then(CommandManager.literal("third").executes(context->{
                var player=context.getSource().getPlayerOrThrow();var w=player.getEntityWorld();var t=team(w,player.getBlockPos());
                if(t==null||!VillageSteam.start(w,t)){context.getSource().sendError(Text.literal("Сначала завершите переход в каменный век и перестройку деревни."));return 0;}
                context.getSource().sendFeedback(()->Text.literal("Началась подготовка эпохи пара: перестройка домов и оружейный завод."),true);return 1;
            })).then(CommandManager.literal("fourth").executes(context->{
                var player=context.getSource().getPlayerOrThrow();
                var world=player.getEntityWorld();
                var team=team(world,player.getBlockPos());
                var data=VillageEraData.get(world);
                if(team==null||!VillageIndustrial.start(data.settlement(team))){
                    context.getSource().sendError(Text.literal("Для начала промышленной стройки нужна деревня III эпохи."));
                    return 0;
                }
                data.markDirty();
                context.getSource().sendFeedback(()->Text.literal("Начат поиск сухого участка для первого городского квартала."),true);
                return 1;
            }))));
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty())return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var data=VillageEraData.get(w);var projects=VillageProjects.get(w);
        int selected=(int)Math.floorMod(w.getTime(),Math.max(100,memory.teams.size()));
        var selectedTeams=new ArrayList<VillageMemory.Team>();
        if(selected<memory.teams.size())selectedTeams.add(memory.teams.get(selected));
        if(w.getTime()%5==0)for(var candidate:memory.teams)if(VillageFastEras.active(w,candidate.village)&&!selectedTeams.contains(candidate))selectedTeams.add(candidate);
        for(var t:selectedTeams){
            if(!WorldSettlements.loadedNearby(w,t.anchor)){
                var city=data.settlements.get(t.village);
                if(VillageIndustrial.civic(city)&&IndustrialCityGrowth.playerNear(w,t,city))
                    VillageIndustrial.tick(w,t,city);
                continue;
            }
            if(w.getPlayers().stream().noneMatch(p->horizontal(p.getBlockPos(),t.anchor)<Math.pow(Math.max(500,p.getViewDistance()*16),2)))continue;
            var s=data.settlement(t);
            VillageEraPlans.treasury(w,t,s);
            VillageArchitectureMigration.revisitWood(w,t,s);
            if(s.era>=2 || s.transitionAt>=0)VillageArchitectureMigration.tick(w,t,s);
            if(s.era==1&&s.transitionAt<0&&s.xp.entrySet().stream().filter(e->PROFESSIONS.contains(e.getKey())&&level(e.getValue())>=3).count()>=3)begin(w,t,s);
            if(s.transitionAt>=0){
                VillageEraPlans.transition(w,t,s);
                boolean converted=conversionsDone(projects,t.village,s);
                boolean landscapes=s.plantation!=null&&projects.complete(t.village,"plantation")&&projects.jobs.stream().filter(p->p.village==t.village&&p.kind.equals("farm_plaza")).allMatch(p->p.done);
                if(!VillageArchitectureMigration.running(s)&&converted&&landscapes&&s.vault!=null&&projects.complete(t.village,"treasury")){
                    s.era=2;s.transitionAt=-1;s.eraAt=w.getTime();s.nextRegicide=w.getTime()+48000;data.markDirty();
                    VillageArena.celebrateEra(w,t.anchor);
                    w.playSound(null,t.anchor,net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,net.minecraft.sound.SoundCategory.PLAYERS,1.0f,1.0f);
                    for(var player:w.getPlayers())if(horizontal(player.getBlockPos(),t.anchor)<320*320){
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(20,100,30));
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(Text.literal("§eКаменный век · "+s.name)));
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.literal("§6§lНОВАЯ ЭПОХА")));
                    }
                }
            }
            VillageSteam.tick(w,t,s);
            VillageIndustrial.tick(w,t,s);
            if(s.era>=2){if(s.transitionAt<0&&!VillageArchitectureMigration.running(s)){VillageArchitecture.ensureHomes(w,t);VillageEraPlans.expansion(w,t,s);}VillageEraPlans.replaceGardens(w,t,s);regicide(w,t,s);}
        }
    }
    static boolean conversionsDone(VillageProjects projects,long village,VillageEraData.Settlement settlement){
        return !VillageArchitectureMigration.running(settlement)&&projects.jobs.stream().filter(p->p.village==village&&p.kind.equals("conversion")).allMatch(p->p.done);
    }
    static void begin(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
        if(s.era!=1||s.transitionAt>=0)return;
        var houses=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().filter(h->residential(h)&&!VillageTerrain.removesHouse(w,h)&&team(w,h.min)==t).toList();
        s.baseHouses=Math.max(1,houses.size());s.conversionCount=houses.size();s.transitionAt=w.getTime();VillageEraData.get(w).markDirty();
        for(var player:w.getPlayers())if(horizontal(player.getBlockPos(),t.anchor)<192*192)player.sendMessage(Text.literal("§6"+s.name+": три профессии достигли III уровня. Строители начинают переход в каменный век — от окраин к центру."),false);
    }
    private static void regicide(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
        var people=w.getEntitiesByClass(VillagerEntity.class,new Box(t.anchor).expand(320,96,320),v->v.isAlive()&&belongs(w,v,t.village));
        var king=people.stream().filter(RoyalProfession::isKing).findFirst().orElse(null);if(king==null)return;
        var assassin=people.stream().filter(v->v.getCommandTags().contains("halloween_royal_assassin")).findFirst().orElse(null);
        if(assassin==null&&w.getTime()>=s.nextRegicide){
            assassin=people.stream().filter(v->!RailDriver.isDriver(v)&&!v.hasVehicle()&&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)&&!v.isBaby()&&!VillageTrials.busy(v)
                &&!profession(v).equals("builder")&&!profession(v).equals("deputy")&&!profession(v).equals("treasurer")&&!v.getCommandTags().contains("halloween_surrender")&&!v.getCommandTags().contains("halloween_prisoner"))
                .min(Comparator.comparingDouble(king::squaredDistanceTo)).orElse(null);
            if(assassin!=null){assassin.addCommandTag("halloween_royal_assassin");s.nextRegicide=w.getTime()+48000;VillageEraData.get(w).markDirty();}
        }
        if(assassin!=null){
            if(assassin.isSleeping())assassin.wakeUp();VillageTasks.hold(assassin);
            if(assassin.squaredDistanceTo(king)>6.25||!assassin.canSee(king)){StreetCrime.pursue(w,assassin,king,.8);return;}
            assassin.getNavigation().stop();
            assassin.addCommandTag("halloween_bandit");assassin.addCommandTag("halloween_wanted");assassin.addCommandTag("halloween_crime_king");assassin.addCommandTag("halloween_test_sentence_execution");
            king.damage(w,w.getDamageSources().mobAttack(assassin),1000);assassin.removeCommandTag("halloween_royal_assassin");
        }
    }
    static String label(String role){return switch(role){case "factory_porter"->"Складовщики";case "miner"->"Шахтёры";case "lumberjack"->"Лесорубы";case "builder"->"Строители";case "farmer"->"Фермеры";case "armorer"->"Бронники";case "weaponsmith"->"Оружейники";case "toolsmith"->"Инструментальщики";case "mason"->"Каменщики";case "guard","royal_guard"->"Гвардия";case "treasurer"->"Казначеи";case "fletcher"->"Лучники";case "fisherman"->"Рыбаки";case "shepherd"->"Пастухи";case "butcher"->"Мясники";case "leatherworker"->"Кожевники";case "librarian"->"Библиотекари";case "cartographer"->"Картографы";case "cleric"->"Священники";default->role;};}
    static String statistics(ServerWorld w,BlockPos pos){
        var t=team(w,pos);if(t==null)return "Деревня не найдена\nПодойдите к деревенскому колоколу.";
        var s=VillageEraData.get(w).settlement(t);var projects=VillageProjects.get(w);
        var jobs=projects.jobs.stream().filter(p->p.village==t.village&&(!p.kind.equals("conversion")||p.id.startsWith(VillageArchitecture.PREFIX))).toList();
        StringBuilder text=new StringBuilder(s.name).append('\n').append(VillageEpoch.title(s.era));
        if(s.transitionAt>=0){long total=jobs.stream().filter(p->!p.kind.equals("treasury")).mapToLong(p->p.plan.size()).sum(),done=jobs.stream().filter(p->!p.kind.equals("treasury")).mapToLong(p->p.index).sum();text.append("\nПереход: ").append(total==0?0:Math.min(100,done*100/total)).append("% текущего плана · перестройка от окраин");}
        if(s.era==2&&s.xp.getOrDefault(VillageSteam.PHASE,0)==1){
            var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
            long remaining=memory.houses.stream().filter(h->VillageHousingRebuild.ordinary(h)&&team(w,h.min)==t&&!VillageTerrain.removesHouse(w,h)).count();
            long rebuilt=s.xp.entrySet().stream().filter(e->e.getKey().startsWith("gothic_rebuild:"+t.village+":")&&e.getValue()>=3).count();
            text.append("\nПереход к III эпохе: жилые дома ").append(rebuilt).append(" готово, ").append(remaining).append(" осталось");
            var cathedral=projects.jobs.stream().filter(p->p.id.equals(VillageCathedral.id(t.village))).findFirst().orElse(null);
            text.append("\nСобор: ").append(cathedral==null?remaining>0?"после перестройки домов":"подготовка участка":cathedral.done?"готов":cathedral.index+"/"+cathedral.plan.size());
        }
        if(s.era>=2){var factory=VillageFactory.project(w,t.village);text.append("\nОружейный завод: ").append(factory==null?"поиск сухого участка":factory.done?"работает":factory.index+"/"+factory.plan.size());}
        long houses=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().filter(h->residential(h)&&!VillageTerrain.removesHouse(w,h)&&team(w,h.min)==t).count();
        text.append("\nДома: ").append(houses).append(VillageIndustrial.civic(s)?" / ≈"+IndustrialCityGrowth.TARGET:s.era==2?" / "+s.baseHouses*2:"");
        long population=w.getEntitiesByClass(VillagerEntity.class,new Box(t.anchor).expand(384,96,384),v->v.isAlive()&&belongs(w,v,t.village)).size();text.append("\nЖителей в загруженных чанках: ").append(population);
        boolean treasuryJob=projects.jobs.stream().anyMatch(p->p.village==t.village&&p.kind.equals("treasury")&&!p.done);
        boolean movingHome=VillageRelocations.get(w).moves.stream().anyMatch(m->m.village==t.village&&m.phase<3);
        text.append("\nКазна: ").append(projects.complete(t.village,"treasury")?"открыта":treasuryJob?"строится":movingHome?"ожидает переноса дома":"поиск подходящего участка");
        text.append("\nСтроительные задачи: ").append(jobs.stream().filter(p->!p.done).count());
        for(var pending:jobs.stream().filter(p->!p.done).limit(6).toList()){
            text.append("\n").append(pending.kind).append(": ").append(pending.index).append("/").append(pending.plan.size());
            if(pending.index<pending.plan.size())text.append(" · ").append(pending.plan.get(pending.index).pos().toShortString());
            if(pending.blocked>0)text.append(" · ожиданий: ").append(pending.blocked);
        }
        for(var move:VillageRelocations.get(w).moves)if(move.village==t.village&&move.phase<3)text.append("\nПеренос дома: ").append(move.status).append(" · ").append(move.min.toShortString()).append(" → ").append(move.destination.toShortString());
        if(VillageArchitectureMigration.running(s))text.append("\nПриоритет: перестройка старых домов");
        if(s.era<2&&s.transitionAt>=0)text.append("\nОпричники: после завершения перехода и казны");
        text.append("\n\nУРОВНИ ПРОФЕССИЙ");
        for(String role:PROFESSIONS){int xp=s.xp.getOrDefault(role,0);text.append('\n').append(label(role)).append(" · ур. ").append(level(xp)).append("  [").append(xp).append(" опыта]");}
        if(s.era==1)text.append("\n\nДля II эпохи: три профессии III уровня. Опыт даётся за выполненную работу.");
        else if(s.era==2&&s.xp.getOrDefault(VillageSteam.PHASE,0)==0)text.append("\n\nПереход к III эпохе начнётся после 50 игровых дней каменного века. Команда: /villageplus third.");
        else if(s.era==VillageEpoch.INDUSTRIAL)text.append("\n\nIV эпоха: открыт первый городской квартал.");
        else if(VillageIndustrial.pending(s))text.append("\n\nПереход к IV эпохе: ").append(VillageIndustrial.civic(s)
                ?"город растёт. Завершение — около 1500 достроенных жилых зданий."
                :"поиск участка и строительство первого городского квартала.");
        else if(s.era==VillageEpoch.STONE&&VillageSteam.infrastructureStarted(s)){
            text.append("\n\nПереход в III эпоху · вокзал ").append(projects.complete(t.village,"rail_station")?"готов":"в работе")
                .append(" · рельсовый цех ").append(projects.complete(t.village,"rail_foundry")?"готов":"в работе")
                .append(" · движение поездов ").append(RailwayNetwork.runningAt(w,t.village)?"открыто":"ожидается");
        }
        else if(s.era==VillageEpoch.STEAM)text.append("\n\nIII эпоха: пороховое оружие, железные дороги и флот. Городской квартал через 50 игровых дней или /villageplus fourth.");
        text.append("\nСнабжение гвардии — из запасов казны по утрам.");return text.toString();
    }
    private VillageEras(){}
}
