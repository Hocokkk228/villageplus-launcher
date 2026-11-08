package dev.halloween;
/** Stable identifiers for saved village eras. Steam is gated by completed construction and infrastructure. */
final class VillageEpoch {
    static final int WOOD=1, STONE=2, STEAM=3, INDUSTRIAL=4;
    static String title(int era){return switch(era){case WOOD->"I · Деревянный век";case STONE->"II · Каменный век";case STEAM->"III · Готика и порох";case INDUSTRIAL->"IV · Индустриальный век";default->"Неизвестная эпоха";};}
    static boolean steamAvailable(){return true;}
    private VillageEpoch(){}
}
