package dev.halloween.mixin.client;

import dev.halloween.GuardRiderState;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import org.spongepowered.asm.mixin.*;

@Mixin(VillagerEntityRenderState.class)
public abstract class GuardRiderStateMixin implements GuardRiderState {
    @Unique private final net.minecraft.client.render.item.ItemRenderState halloween$offhand=new net.minecraft.client.render.item.ItemRenderState();
    public net.minecraft.client.render.item.ItemRenderState halloween$offhand(){return halloween$offhand;}
    @Unique private boolean halloween$seatedPassenger,halloween$mountedGuard,halloween$guard,halloween$crossbow;
    @Unique private float halloween$swing,halloween$turn,halloween$hurt,halloween$seed;
    @Unique private Pose halloween$pose=Pose.CLASSIC;
    @Unique private Weapon halloween$weapon=Weapon.EMPTY;
    @Unique private boolean halloween$leftStrike;
    @Unique private int halloween$fightAction;
    @Unique private float halloween$fightFrame;
    public int halloween$fightAction(){return halloween$fightAction;}
    public float halloween$fightFrame(){return halloween$fightFrame;}
    public void halloween$fightAction(int action,float frame){halloween$fightAction=action;halloween$fightFrame=frame;}
    public Weapon halloween$weapon(){return halloween$weapon;}
    public boolean halloween$leftStrike(){return halloween$leftStrike;}
    public void halloween$combat(Weapon weapon,boolean leftStrike){halloween$weapon=weapon;halloween$leftStrike=leftStrike;}
    public Pose halloween$pose(){return halloween$pose;}
    public void halloween$pose(Pose pose,float turn,float hurt,float seed){halloween$pose=pose;halloween$turn=turn;halloween$hurt=hurt;halloween$seed=seed;}
    public float halloween$turn(){return halloween$turn;}
    public float halloween$hurt(){return halloween$hurt;}
    public float halloween$seed(){return halloween$seed;}
    public boolean halloween$isSeatedPassenger(){return halloween$seatedPassenger;}
    public void halloween$setSeatedPassenger(boolean value){halloween$seatedPassenger=value;}
    public boolean halloween$isGuard(){return halloween$guard;}
    public void halloween$setGuard(boolean value){halloween$guard=value;}
    public boolean halloween$isCrossbow(){return halloween$crossbow;}
    public void halloween$setCrossbow(boolean value){halloween$crossbow=value;}
    public float halloween$swing(){return halloween$swing;}
    public void halloween$setSwing(float value){halloween$swing=value;}
    public boolean halloween$isMountedGuard(){return halloween$mountedGuard;}
    public void halloween$setMountedGuard(boolean value){halloween$mountedGuard=value;}
}
