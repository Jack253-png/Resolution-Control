package resolutioncontrol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.api.client.keybinding.KeyBindingRegistry;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import resolutioncontrol.client.gui.screen.SettingsScreen;
import resolutioncontrol.util.*;

public class ResolutionControlMod implements ModInitializer {
	public static final String MOD_ID = "resolutioncontrol";
	
	public static Identifier identifier(String path) {
		return new Identifier(MOD_ID, path);
	}
	
	private static MinecraftClient client = MinecraftClient.getInstance();
	
	private static ResolutionControlMod instance;
	
	public static ResolutionControlMod getInstance() {
		return instance;
	}
	
	private static KeyBinding settingsKeyBinding;
	
	private boolean shouldScale = false;
	
	@Nullable
	private Framebuffer framebuffer;
	
	@Nullable
	private Framebuffer clientFramebuffer;
	
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		instance = this;
		
		settingsKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.resolutioncontrol.settings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				"key.categories.misc"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (settingsKeyBinding.wasPressed()) {
				client.openScreen(new SettingsScreen());
			}
		});
	}
	
	public void setShouldScale(boolean shouldScale) {
		if (shouldScale == this.shouldScale) return;
		
		if (getScaleFactor() == 1) return;
		
		Window window = getWindow();
		if (framebuffer == null) {
			this.shouldScale = true; // so we get the right dimensions
			framebuffer = new Framebuffer(
				window.getFramebufferWidth(),
				window.getFramebufferHeight(),
				true,
				MinecraftClient.IS_SYSTEM_MAC
			);
		}
		
		this.shouldScale = shouldScale;
		
		client.getProfiler().swap(shouldScale ? "startScaling" : "finishScaling");
		
		// swap out framebuffers as needed
		boolean shouldUpdateViewport = true;
		if (shouldScale) {
			clientFramebuffer = client.getFramebuffer();
			setClientFramebuffer(framebuffer);
			framebuffer.beginWrite(shouldUpdateViewport);
			// nothing on the client's framebuffer yet
		} else {
			setClientFramebuffer(clientFramebuffer);
			client.getFramebuffer().beginWrite(shouldUpdateViewport);
			framebuffer.draw(
				window.getFramebufferWidth(),
				window.getFramebufferHeight()
			);
		}
		
		client.getProfiler().swap("level");
	}
	
	public double getScaleFactor() {
		return Config.getScaleFactor();
	}
	
	public void setScaleFactor(double scaleFactor) {
		if (scaleFactor == Config.getScaleFactor()) return;
		
		Config.getInstance().scaleFactor = scaleFactor;
		
		updateFramebufferSize();
		
		ConfigHandler.instance.saveConfig();
	}

	public ScalingAlgorithm getUpscaleAlgorithm() {
		return Config.getUpscaleAlgorithm();
	}

	public void setUpscaleAlgorithm(ScalingAlgorithm algorithm) {
		if (algorithm == Config.getUpscaleAlgorithm()) return;

		Config.getInstance().upscaleAlgorithm = algorithm;

		updateFramebufferSize();

		ConfigHandler.instance.saveConfig();
	}

	public void nextUpscaleAlgorithm() {
		ScalingAlgorithm currentAlgorithm = getUpscaleAlgorithm();
		if (currentAlgorithm.equals(ScalingAlgorithm.NEAREST)) {
			setUpscaleAlgorithm(ScalingAlgorithm.LINEAR);
		} else {
			setUpscaleAlgorithm(ScalingAlgorithm.NEAREST);
		}
	}

	public ScalingAlgorithm getDownscaleAlgorithm() {
		return Config.getDownscaleAlgorithm();
	}

	public void setDownscaleAlgorithm(ScalingAlgorithm algorithm) {
		if (algorithm == Config.getDownscaleAlgorithm()) return;

		Config.getInstance().downscaleAlgorithm = algorithm;

		updateFramebufferSize();

		ConfigHandler.instance.saveConfig();
	}

	public void nextDownscaleAlgorithm() {
		ScalingAlgorithm currentAlgorithm = getDownscaleAlgorithm();
		if (currentAlgorithm.equals(ScalingAlgorithm.NEAREST)) {
			setDownscaleAlgorithm(ScalingAlgorithm.LINEAR);
		} else {
			setDownscaleAlgorithm(ScalingAlgorithm.NEAREST);
		}
	}
	
	public double getCurrentScaleFactor() {
		return shouldScale ? Config.getScaleFactor() : 1;
	}

	public ScalingAlgorithm getCurrentScalingAlgorithm() {
		return Config.getScaleFactor() > 1.0 ? Config.getUpscaleAlgorithm() : Config.getDownscaleAlgorithm();
	}
	
	public void onResolutionChanged() {
		updateFramebufferSize();
	}
	
	private void updateFramebufferSize() {
		if (framebuffer == null) return;
		
		if (getScaleFactor() != 1) {
			// resize if not unused
			resize(framebuffer);
		}
		
		resize(client.worldRenderer.getEntityOutlinesFramebuffer());
	}
	
	public void resize(Framebuffer framebuffer) {
		boolean prev = shouldScale;
		shouldScale = true;
		framebuffer.resize(
			getWindow().getFramebufferWidth(),
			getWindow().getFramebufferHeight(),
			MinecraftClient.IS_SYSTEM_MAC
		);
		shouldScale = prev;
	}
	
	private Window getWindow() {
		return client.getWindow();
	}
	
	private void setClientFramebuffer(Framebuffer framebuffer) {
		((MutableMinecraftClient) client).setFramebuffer(framebuffer);
	}

	public KeyBinding getSettingsKeyBinding() {
		return settingsKeyBinding;
	}

	public interface MutableMinecraftClient {
		void setFramebuffer(Framebuffer framebuffer);
	}
}
