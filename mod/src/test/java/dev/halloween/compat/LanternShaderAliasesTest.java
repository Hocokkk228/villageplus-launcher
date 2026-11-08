package dev.halloween.compat;

import java.io.StringReader;
import java.util.Properties;

/** Regression checks for multiline packs, conditional mappings and unrelated namespaces. */
public final class LanternShaderAliasesTest {
    public static void main(String[] args) throws Exception {
        String source = "# lantern is a comment\nblock.198=lantern \\\n mod:wall_lantern\nblock.245=soul_lantern\nblock.2=other:lantern\nlayer.cutout=lantern\n";
        Properties props = new Properties();
        String patched = LanternShaderAliases.addAlias("block.properties", source);
        props.load(new StringReader(patched));
        require(props.getProperty("block.198").equals("lantern halloween:halloween_garland halloween:pumpkin_lantern halloween:bat_lantern mod:wall_lantern"), "continued block entry");
        require(props.getProperty("block.245").equals("soul_lantern"), "soul lantern must be untouched");
        require(props.getProperty("block.2").equals("other:lantern"), "other namespace must be untouched");
        require(props.getProperty("layer.cutout").equals("lantern"), "render layers must be untouched");
        require(patched.startsWith("# lantern is a comment"), "comments must be untouched");
        String conditional = "block.10021 = \\\n#ifdef MOD_SUPPORT\n other:light \\\n#endif\n torch minecraft:lantern \\\n furnace:lit=true\n";
        require(LanternShaderAliases.addAlias("block.properties", conditional).contains("minecraft:lantern halloween:halloween_garland halloween:pumpkin_lantern"), "Derivative conditional continuation");
        require(LanternShaderAliases.addAlias("item.properties", "item.1015=lantern\n").contains("item.1015=lantern halloween:halloween_garland halloween:pumpkin_lantern"), "held item alias");
        require(LanternShaderAliases.addAlias("block.properties", patched).equals(patched), "idempotent");
        require(LanternShaderAliases.addAlias("entity.properties", source).equals(source), "unrelated file");
        require(LanternShaderAliases.addAlias("item.properties", null) == null, "missing item.properties");
        System.out.println("Shader compatibility regression checks passed.");
    }
    private static void require(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
}
