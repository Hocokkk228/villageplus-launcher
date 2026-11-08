package dev.halloween;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
final class NavalControlsClient {
 static KeyBinding sails;static boolean lastAnchor;static int lastShip=-1;
 static void register(){sails=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.halloween.ship_sails",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_V,KeyBinding.Category.create(Identifier.of("halloween","naval"))));
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   if(c.player==null||!(c.player.getVehicle() instanceof RailSeatEntity seat)||!seat.controls()||seat.train()==null||!seat.train().isShip()){lastAnchor=false;lastShip=-1;while(sails.wasPressed()){}return;}
   var ship=seat.train();if(lastShip!=ship.getId()){lastAnchor=false;lastShip=ship.getId();}
   boolean anchor=c.options.jumpKey.isPressed();boolean toggle=anchor&&!lastAnchor;lastAnchor=anchor;boolean reef=sails.wasPressed();while(sails.wasPressed()){}
   if(ClientPlayNetworking.canSend(NavalControlNetwork.Input.ID))ClientPlayNetworking.send(new NavalControlNetwork.Input((c.options.forwardKey.isPressed()?1:0)-(c.options.backKey.isPressed()?1:0),(c.options.rightKey.isPressed()?1:0)-(c.options.leftKey.isPressed()?1:0),toggle,reef));
  });
 }
}
