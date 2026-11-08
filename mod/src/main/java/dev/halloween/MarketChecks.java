package dev.halloween;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.block.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
final class MarketChecks {
 static void require(boolean b,String text){if(!b)throw new AssertionError(text);}
 static void check(ServerWorld w){var base=new BlockPos(140,175,140);long marketId=base.asLong();while(!Contraband.restricted(marketId))marketId++;final long village=marketId;var projects=VillageProjects.get(w);
  for(int era=1;era<=3;era++){
   int currentEra=era;var plan=VillageMarkets.plan(base,era);for(var p:plan.keySet())w.getChunk(p);
   var barrel=base.add(3,1,4);if(era>1)require(w.getBlockEntity(barrel) instanceof Inventory,"previous market storage exists");
   projects.add(w,VillageMarkets.id(village,era),village,"market",plan);
   var job=projects.jobs.stream().filter(p->p.id.equals(VillageMarkets.id(village,currentEra))).findFirst().orElseThrow();
   for(int n=0;n<1000&&!job.done;n++)VillageProjects.materialize(w,village,1000);
   require(job.done,"market construction completes in era "+era+" at "+job.index+"/"+job.plan.size());
   var stock=(Inventory)w.getBlockEntity(barrel);require(stock!=null,"market has accessible real barrel storage");
   if(era==1)stock.setStack(0,new ItemStack(Items.DIAMOND,11));else require(stock.count(Items.DIAMOND)==11,"same-site era upgrade preserves real stock");
   require(w.getBlockState(base.add(7,1,2)).isAir(),"market/shop entrance stays open");
   if(era==3){require(w.getBlockState(base.add(6,1,1)).isOf(Blocks.DARK_OAK_DOOR)&&w.getBlockState(base.add(6,2,1)).isOf(Blocks.DARK_OAK_DOOR),"shop has complete usable two-block doors");require(w.getBlockState(base.add(7,6,7)).isOf(Blocks.DARK_OAK_SLAB),"shop has a finished roof");}
  }
  checkCrime(w, base, village);
  checkContraband(w, base, village);
  checkArmsDealers(w, base, village);
  var wet=base.add(25,0,0);w.setBlockState(wet.up(),Blocks.WATER.getDefaultState());require(!VillageMarkets.safe(w,wet),"market site rejects water");
  org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS markets I/II and shop III complete with stock preserved and usable entrance");
 }
 private static void checkCrime(ServerWorld world, BlockPos base, long village) {
   var settlement = new VillageEraData.Settlement(village, "Речной");
   settlement.xp.put(VillageMarkets.X, base.getX());
   settlement.xp.put(VillageMarkets.Y, base.getY());
   settlement.xp.put(VillageMarkets.Z, base.getZ());
   settlement.xp.put(VillageMarkets.STAGE, 3);
   VillageEraData.get(world).settlements.put(village, settlement);
   var pos = base.add(3, 1, 4);
   var barrel = (net.minecraft.block.entity.BarrelBlockEntity)world.getBlockEntity(pos);
   var stall = MarketCrime.find(world, pos);
   require(stall != null && stall.era() == 3, "completed shop barrel recognized on an existing site");
   require(MarketCrime.find(world, pos.east()) == null, "ordinary nearby barrels are not market stock");
   var full = new net.minecraft.inventory.SimpleInventory(1);
   full.setStack(0, new ItemStack(Items.STONE, 64));
   require(MarketCrime.grab(barrel, full) == 0 && barrel.count(Items.DIAMOND) == 11,
       "full inventory does not lose or duplicate market stock");
   var witness = new net.minecraft.entity.passive.VillagerEntity(net.minecraft.entity.EntityType.VILLAGER, world);
   witness.setPosition(Vec3d.ofBottomCenter(pos.east(2)));
   world.spawnEntity(witness);
   var buyer = player(world, "MarketBuyer", pos.south());
   var raider = player(world, "MarketRaider", pos.east());
   try {
     var hand = net.minecraft.util.Hand.MAIN_HAND;
     var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
     var interact = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker();
     interact.interact(buyer, world, hand, hit);
     interact.interact(raider, world, hand, hit);
     raider.currentScreenHandler.onSlotClick(0, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, raider);
     require(barrel.isEmpty() && raider.getInventory().count(Items.DIAMOND) == 11,
         "actual menu quick move transfers merchandise");
     require(!buyer.getCommandTags().contains("halloween_player_wanted"), "observer with open menu is not blamed");
     require(PlayerJustice.caseDetails(raider).stream().anyMatch(c -> c.contains(stall.name())),
         "witness reports the actor and shop name in the case");
     require(base.add(7, 1, 0).equals(MarketCrime.investigation(world, village)),
         "alarm sends the patrol to the entrance of the correct market");
     raider.closeHandledScreen();
     raider.setSneaking(true);
     barrel.setStack(0, new ItemStack(Items.EMERALD, 16));
     interact.interact(raider, world, hand, hit);
     interact.interact(raider, world, hand, hit);
     require(raider.getInventory().count(Items.EMERALD) == 12 && barrel.count(Items.EMERALD) == 4,
         "grab transfers a bounded quantity and has a cooldown");
     witness.discard();
     buyer.closeHandledScreen();
     var thief = new net.minecraft.entity.passive.VillagerEntity(net.minecraft.entity.EntityType.VILLAGER, world);
     thief.setPosition(Vec3d.ofBottomCenter(pos.east()));
     world.spawnEntity(thief);
     try {
       VillageTheft.raidMarket(world, thief, stall);
       require(thief.getInventory().count(Items.EMERALD) == 4 && barrel.isEmpty()
           && thief.getCommandTags().contains("halloween_wanted"), "NPC raid takes real stock and starts pursuit");
     } finally { thief.discard(); }
   } finally {
     witness.discard();
     world.removePlayer(buyer, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
     world.removePlayer(raider, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
   }
   org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS market raids: witnessed theft attribution, player grab cooldown and NPC stock transfer");
 }

 private static void checkContraband(ServerWorld world, BlockPos base, long restricted) {
   long free = restricted + 1;
   while (Contraband.restricted(free)) free++;
   var from = new RailwayNetwork.Station(free, base.west(150), 0, 0);
   var to = new RailwayNetwork.Station(restricted, base, 0, 0);
   require(Contraband.restrictedDestination(from, to) && !Contraband.restrictedDestination(to, from)
       && !Contraband.restrictedDestination(to, to), "restriction applies only to an actual border crossing");
   var buyer = player(world, "BorderTest", base.add(9, 1, 11));
   try {
     var pos = base.add(10, 1, 11);
     var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
     var interact = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker();
     var hand = net.minecraft.util.Hand.MAIN_HAND;
     buyer.getInventory().setStack(0, new ItemStack(VillageItems.MUSKET));
     buyer.setSneaking(true);
     Contraband.arrive(world, from, buyer);
     interact.interact(buyer, world, hand, hit);
     require(buyer.getMainHandStack().isOf(VillageItems.MUSKET), "sale refused without a delivery");
     Contraband.arrive(world, to, buyer);
     interact.interact(buyer, world, hand, hit);
     require(buyer.getInventory().count(VillageItems.MUSKET) == 0 && buyer.getInventory().count(Items.EMERALD) == 6,
         "arrival enables one sale of an actual carried weapon");
     buyer.getInventory().setSelectedSlot(1);
     buyer.getInventory().setStack(1, new ItemStack(VillageItems.REVOLVER));
     interact.interact(buyer, world, hand, hit);
     require(buyer.getMainHandStack().isOf(VillageItems.REVOLVER) && buyer.getInventory().count(Items.EMERALD) == 6,
         "the same crossing cannot pay twice");
     var evidence = new net.minecraft.inventory.SimpleInventory(1);
     evidence.setStack(0, new ItemStack(Items.STONE, 64));
     require(Contraband.confiscate(buyer.getInventory(), evidence) == 0 && buyer.getMainHandStack().isOf(VillageItems.REVOLVER),
         "full evidence inventory conserves the player's weapon");
     evidence.clear();
     require(Contraband.confiscate(buyer.getInventory(), evidence) == 1
         && evidence.count(VillageItems.REVOLVER) == 1 && buyer.getMainHandStack().isEmpty(),
         "the inspection transfers the actual weapon");
     PlayerJustice.smugglingWarrant(buyer, to.origin);
     require(PlayerJustice.caseDetails(buyer).stream().anyMatch(c -> c.contains("контрабанда") && c.contains("1 дн.")),
         "contraband is a separate court offense");
     var ship = NavalContent.CUTTER_ENTITY.create(world, net.minecraft.entity.SpawnReason.EVENT);
     try {
       var home = new Contraband.Port(free, new NavalWorks.Site(base.west(150), Direction.SOUTH));
       var dock = new Contraband.Port(restricted, new NavalWorks.Site(base, Direction.SOUTH));
       require(dock.contains(dock.site().at(9, 0, 18)) && !dock.contains(dock.site().at(0, 0, 70)),
           "only a real berth in the harbor triggers docking");
       ship.naval().barrels[0].setStack(0, evidence.removeStack(0));
       Contraband.dock(world, home, buyer, ship);
       Contraband.dock(world, dock, buyer, ship);
       require(Contraband.stowed(ship, VillageItems.REVOLVER)
           && Contraband.confiscate(buyer.getInventory(), evidence) == 0,
           "personal inspection leaves the revolver in the ship's barrel");
       buyer.getInventory().setStack(1, ship.naval().barrels[0].removeStack(0));
       interact.interact(buyer, world, hand, hit);
       require(buyer.getInventory().count(VillageItems.REVOLVER) == 0 && buyer.getInventory().count(Items.EMERALD) == 15,
           "sea delivery can be sold after retrieving the actual weapon");
       Contraband.dock(world, dock, buyer, ship);
       buyer.getInventory().setStack(1, new ItemStack(VillageItems.REVOLVER));
       interact.interact(buyer, world, hand, hit);
       require(buyer.getMainHandStack().isOf(VillageItems.REVOLVER), "staying docked cannot generate a second delivery");
     } finally { ship.discard(); }

   } finally { world.removePlayer(buyer, net.minecraft.entity.Entity.RemovalReason.DISCARDED); }
   org.slf4j.LoggerFactory.getLogger("halloween-regression").info(
       "PASS contraband: city policy, single delivery reward, evidence conservation and separate court offense");
 }

 private static void checkArmsDealers(ServerWorld world, BlockPos market, long village) {
   var base = market.west(26);
   var settlement = VillageEraData.get(world).settlements.get(village);
   for (var site : ArmsDealers.Site.values()) {
     var plan = ArmsDealers.plan(base, site);
     for (var pos : plan.keySet()) world.getChunk(pos);
     String key = site.key();
     settlement.xp.put(key + "_x", base.getX());
     settlement.xp.put(key + "_y", base.getY());
     settlement.xp.put(key + "_z", base.getZ());
     var jobs = VillageProjects.get(world);
     jobs.add(world, site.id(village), village, "arms_cache", plan);
     var job = jobs.jobs.stream().filter(j -> j.id.equals(site.id(village))).findFirst().orElseThrow();
     for (int n = 0; n < 500 && !job.done; n++) VillageProjects.materialize(world, village, 1000);
     require(job.done, "builders construct dealer site " + site);
     var pos = site.stock(base);
     var stock = (net.minecraft.block.entity.BarrelBlockEntity)world.getBlockEntity(pos);
     var reserve = new net.minecraft.inventory.SimpleInventory(8);
     reserve.setStack(0, new ItemStack(VillageItems.MUSKET_CARTRIDGE, 30));
     reserve.setStack(1, new ItemStack(VillageItems.MUSKET));
     reserve.setStack(2, new ItemStack(VillageItems.MUSKET));
     reserve.setStack(3, new ItemStack(VillageItems.MUSKET));
     ArmsDealers.transfer(reserve, stock, VillageItems.MUSKET_CARTRIDGE, 16, 16);
     ArmsDealers.transfer(reserve, stock, VillageItems.MUSKET, 1, 2);
     require(stock.count(VillageItems.MUSKET_CARTRIDGE) == 14 && reserve.count(VillageItems.MUSKET_CARTRIDGE) == 16
         && reserve.count(VillageItems.MUSKET) == 2, "dealer restock moves surplus while keeping guards' reserve");
     var buyer = player(world, "DealerTest", pos.north());
     try {
       var callback = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker();
       var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
       buyer.getInventory().setStack(0, new ItemStack(Items.EMERALD, 13));
       callback.interact(buyer, world, net.minecraft.util.Hand.MAIN_HAND, hit);
       buyer.setSneaking(true);
       callback.interact(buyer, world, net.minecraft.util.Hand.MAIN_HAND, hit);
       require(buyer.getInventory().count(Items.EMERALD) == 1
           && buyer.getInventory().count(VillageItems.MUSKET_CARTRIDGE) == 8
           && buyer.getInventory().count(VillageItems.MUSKET) == 1
           && stock.count(VillageItems.MUSKET_CARTRIDGE) == 6 && stock.count(VillageItems.MUSKET) == 0,
           "dealer sells real stock at " + site);
     } finally { world.removePlayer(buyer, net.minecraft.entity.Entity.RemovalReason.DISCARDED); }
     base = base.south(12);
   }
   org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS sea contraband and stocked dealers at garages, alleys and stations");
 }

 private static net.minecraft.server.network.ServerPlayerEntity player(ServerWorld world, String name, BlockPos pos) {
   var server = world.getServer();
   var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name);
   var player = new net.minecraft.server.network.ServerPlayerEntity(server, world, profile,
       net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
   player.networkHandler = new net.minecraft.server.network.ServerPlayNetworkHandler(server,
       new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND), player,
       net.minecraft.server.network.ConnectedClientData.createDefault(profile, false));
   player.setPosition(Vec3d.ofBottomCenter(pos));
   world.onPlayerConnected(player);
   return player;
 }

}
