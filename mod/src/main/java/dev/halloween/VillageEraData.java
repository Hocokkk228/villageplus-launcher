package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Progress belongs to the settlement, not to the currently loaded population. */
final class VillageEraData extends PersistentState {
    static final class Vault {
        static final Codec<Vault> CODEC=RecordCodecBuilder.create(i->i.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(v->v.origin),Codec.INT.fieldOf("facing").forGetter(v->v.facing),
            Codec.STRING.optionalFieldOf("treasurer","").forGetter(v->v.treasurer),Codec.LONG.optionalFieldOf("issued_day",-1L).forGetter(v->v.issuedDay),
            Codec.BOOL.optionalFieldOf("seeded",false).forGetter(v->v.seeded)).apply(i,Vault::new));
        final BlockPos origin;final int facing;String treasurer;long issuedDay;boolean seeded;
        Vault(BlockPos origin,int facing,String treasurer,long issuedDay,boolean seeded){this.origin=origin;this.facing=facing;this.treasurer=treasurer;this.issuedDay=issuedDay;this.seeded=seeded;}
    }
    static final class Settlement {
        static final Codec<Settlement> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.fieldOf("village").forGetter(s->s.village),Codec.STRING.fieldOf("name").forGetter(s->s.name),
            Codec.INT.optionalFieldOf("era",1).forGetter(s->s.era),Codec.unboundedMap(Codec.STRING,Codec.INT).optionalFieldOf("xp",Map.of()).forGetter(s->s.xp),
            Codec.LONG.optionalFieldOf("transition_at",-1L).forGetter(s->s.transitionAt),Codec.LONG.optionalFieldOf("era_at",-1L).forGetter(s->s.eraAt),
            Codec.INT.optionalFieldOf("base_houses",0).forGetter(s->s.baseHouses),Codec.LONG.optionalFieldOf("next_regicide",-1L).forGetter(s->s.nextRegicide),
            Vault.CODEC.optionalFieldOf("vault").forGetter(s->Optional.ofNullable(s.vault)),
            BlockPos.CODEC.optionalFieldOf("plantation").forGetter(s->Optional.ofNullable(s.plantation)),
            Codec.INT.optionalFieldOf("conversion_count",0).forGetter(s->s.conversionCount)).apply(i,Settlement::new));
        final long village;final String name;int era,baseHouses,conversionCount;final Map<String,Integer> xp;
        long transitionAt,eraAt,nextRegicide;Vault vault;BlockPos plantation;
        Settlement(long village,String name,int era,Map<String,Integer> xp,long transitionAt,long eraAt,int baseHouses,long nextRegicide,Optional<Vault> vault,Optional<BlockPos> plantation,int conversionCount){
            this.village=village;this.name=name;this.era=era;this.xp=new HashMap<>(xp);this.transitionAt=transitionAt;this.eraAt=eraAt;this.baseHouses=baseHouses;
            this.nextRegicide=nextRegicide;this.vault=vault.orElse(null);this.plantation=plantation.orElse(null);this.conversionCount=conversionCount;
        }
        Settlement(long village,String name){this(village,name,1,Map.of(),-1,-1,0,-1,Optional.empty(),Optional.empty(),0);}
    }
    static final Codec<VillageEraData> CODEC=Settlement.CODEC.listOf().xmap(VillageEraData::new,d->new ArrayList<>(d.settlements.values()));
    static final PersistentStateType<VillageEraData> TYPE=new PersistentStateType<>("halloween_village_eras",()->new VillageEraData(List.of()),CODEC,null);
    final Map<Long,Settlement> settlements=new LinkedHashMap<>();
    VillageEraData(List<Settlement> settlements){for(var s:settlements)this.settlements.put(s.village,s);}
    static VillageEraData get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
    Settlement settlement(VillageMemory.Team t){
        var existing=settlements.get(t.village);if(existing!=null)return existing;
        String[] names={"Берёзовка","Ольховка","Заречье","Сосновый Бор","Лесогорье","Дубровка","Медные Ключи","Каменка","Ясная Поляна","Воронец","Лохино","Сосово","Речное","Светлогорье","Тихий Лог","Златополь","Кленовка","Рябиново","Луговое","Северная Застава","Беловодье","Липовец","Красный Холм","Соколинка"};
        var s=new Settlement(t.village,names[Math.floorMod(Long.hashCode(t.village),names.length)]);settlements.put(t.village,s);markDirty();return s;
    }
}
