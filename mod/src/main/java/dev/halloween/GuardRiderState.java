package dev.halloween;

/** Extra client render state, populated from the actual vehicle relationship. */
public interface GuardRiderState {
    net.minecraft.client.render.item.ItemRenderState halloween$offhand();
    boolean halloween$isSeatedPassenger();
    void halloween$setSeatedPassenger(boolean value);
    boolean halloween$isGuard();
    void halloween$setGuard(boolean value);
    boolean halloween$isCrossbow();
    void halloween$setCrossbow(boolean value);
    enum Weapon { EMPTY, BLADE, SABRE, RAPIER, MUSKET, REVOLVER, CROSSBOW, TOOL }
    Weapon halloween$weapon();
    boolean halloween$leftStrike();
    void halloween$combat(Weapon weapon, boolean leftStrike);
    int halloween$fightAction();
    float halloween$fightFrame();
    void halloween$fightAction(int action,float frame);
    float halloween$swing();
    void halloween$setSwing(float value);
    enum Pose { CLASSIC, CASUAL, WORK, GUARD, STREET, SWIM, INTERACT }
    Pose halloween$pose();
    void halloween$pose(Pose pose, float turn, float hurt, float seed);
    float halloween$turn();
    float halloween$hurt();
    float halloween$seed();
    boolean halloween$isMountedGuard();
    void halloween$setMountedGuard(boolean value);
}
