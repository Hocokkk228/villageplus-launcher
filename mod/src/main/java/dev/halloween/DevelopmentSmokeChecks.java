package dev.halloween;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.slf4j.LoggerFactory;

/** Opt-in development-client integration checks; never registered in an installed mod. */
final class DevelopmentSmokeChecks {
    private static int ticks;
    private static boolean done;
    private static BlockPos anchor;
    static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(done||server.getPlayerManager().getPlayerList().isEmpty())return;
        ServerPlayerEntity p=server.getPlayerManager().getPlayerList().getFirst();ServerWorld w=server.getOverworld();
        try{
            if(++ticks==20){
                anchor=p.getBlockPos().add(2,2,0);
                for(String id:new String[]{"pumpkin_chair","gothic_chair","coffin_chair","witch_stool","pumpkin_cushion","bat_cushion","ghost_cushion","spider_cushion"}){
                    FurnitureBlock b=(FurnitureBlock)Registries.BLOCK.get(Identifier.of("halloween",id));
                    BlockState state=b.getDefaultState();w.setBlockState(anchor,state);
                    b.onUse(state,w,anchor,p,new BlockHitResult(Vec3d.ofCenter(anchor),Direction.UP,anchor,false));
                    require(p.getVehicle() instanceof SeatEntity,"riding "+id);
                    SeatEntity seat=(SeatEntity)p.getVehicle();p.stopRiding();seat.tick();require(seat.isRemoved(),"seat cleanup "+id);
                    w.breakBlock(anchor,false);
                }
                CandelabraBlock candle=(CandelabraBlock)Registries.BLOCK.get(Identifier.of("halloween","candle_candelabra"));
                w.setBlockState(anchor,candle.getDefaultState());
                var hit=new BlockHitResult(Vec3d.ofCenter(anchor),Direction.UP,anchor,false);
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
                candle.onUseWithItem(p.getMainHandStack(),w.getBlockState(anchor),w,anchor,p,Hand.MAIN_HAND,hit);
                require(w.getBlockState(anchor).get(CandelabraBlock.LIT),"flint ignition");
                p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);candle.onUse(w.getBlockState(anchor),w,anchor,p,hit);
                require(!w.getBlockState(anchor).get(CandelabraBlock.LIT),"candle extinguishing");w.breakBlock(anchor,false);
                TallFurnitureBlock b=(TallFurnitureBlock)Registries.BLOCK.get(Identifier.of("halloween","haunted_cabinet"));
                w.setBlockState(anchor,b.getDefaultState());b.onPlaced(w,anchor,b.getDefaultState(),p,ItemStack.EMPTY);
                CabinetEntity cabinet=(CabinetEntity)w.getBlockEntity(anchor);require(cabinet!=null&&cabinet.size()==54,"54-slot cabinet");
                cabinet.setStack(53,new ItemStack(Items.NETHERITE_INGOT,3));
                var loaded=BlockEntity.createFromNbt(anchor,w.getBlockState(anchor),cabinet.createNbtWithIdentifyingData(w.getRegistryManager()),w.getRegistryManager());
                require(loaded instanceof CabinetEntity copy&&copy.getStack(53).getCount()==3,"inventory NBT round trip");
                p.openHandledScreen(cabinet);
                require(p.currentScreenHandler instanceof GenericContainerScreenHandler screen&&screen.getRows()==6,"double chest screen");
                LoggerFactory.getLogger("halloween-smoke").info("PASS: 8 seats, dismount cleanup, candles, 54 slots and inventory serialization");
            }
            if(ticks==40){require(w.getBlockState(anchor).get(TallFurnitureBlock.DOOR)==4,"door opening animation");p.closeHandledScreen();}
            if(ticks==60){
                require(w.getBlockState(anchor).get(TallFurnitureBlock.DOOR)==0,"door closing animation");
                w.breakBlock(anchor.up(2).east(),true);
                require(w.getBlockState(anchor).isAir(),"cabinet dismantles from upper corner");
                int count=w.getEntitiesByClass(ItemEntity.class,new Box(anchor).expand(4),e->e.getStack().isOf(Items.NETHERITE_INGOT)).stream().mapToInt(e->e.getStack().getCount()).sum();
                require(count==3,"cabinet drops contents once");
                TallFurnitureBlock sc=(TallFurnitureBlock)Registries.BLOCK.get(Identifier.of("halloween","pumpkin_scarecrow"));
                w.setBlockState(anchor,sc.getDefaultState());sc.onPlaced(w,anchor,sc.getDefaultState(),p,ItemStack.EMPTY);
                require(w.getBlockState(anchor.up(2)).isOf(sc),"three-block scarecrow");w.breakBlock(anchor.up(),true);
                require(w.getBlockState(anchor).isAir()&&w.getBlockState(anchor.up(2)).isAir(),"scarecrow cleanup");
                done=true;LoggerFactory.getLogger("halloween-smoke").info("ALL CHECKS PASSED: door animation, inventory drops and three-block structures");
                net.minecraft.client.MinecraftClient.getInstance().execute(()->net.minecraft.client.MinecraftClient.getInstance().scheduleStop());
            }
        }catch(Throwable error){done=true;LoggerFactory.getLogger("halloween-smoke").error("SMOKE TEST FAILED",error);
            net.minecraft.client.MinecraftClient.getInstance().execute(()->net.minecraft.client.MinecraftClient.getInstance().scheduleStop());}
    });}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
