package net.coderbot.iris.texture.pbr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.texture.TextureTracker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.rendertarget.NativeImageBackedSingleColorTexture;
import net.coderbot.iris.texture.pbr.loader.PBRTextureLoader;
import net.coderbot.iris.texture.pbr.loader.PBRTextureLoader.PBRTextureConsumer;
import net.coderbot.iris.texture.pbr.loader.PBRTextureLoaderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

public class PBRTextureManager {
	public static final PBRTextureManager INSTANCE = new PBRTextureManager();

	// TODO: Figure out how to merge these two.
	private static Runnable normalTextureChangeListener;
	private static Runnable specularTextureChangeListener;

	static {
		StateUpdateNotifiers.normalTextureChangeNotifier = listener -> normalTextureChangeListener = listener;
		StateUpdateNotifiers.specularTextureChangeNotifier = listener -> specularTextureChangeListener = listener;
	}

	private final Int2ObjectMap<PBRTextureHolder> holders = new Int2ObjectOpenHashMap<>();
	private final PBRTextureConsumerImpl consumer = new PBRTextureConsumerImpl();

	private NativeImageBackedSingleColorTexture defaultNormalTexture;
	private NativeImageBackedSingleColorTexture defaultSpecularTexture;
	// Not PBRTextureHolderImpl to directly reference fields
	private final PBRTextureHolder defaultHolder = new PBRTextureHolder() {
		@Override
		public @NotNull AbstractTexture getNormalTexture() {
			return defaultNormalTexture;
		}

		@Override
		public @NotNull AbstractTexture getSpecularTexture() {
			return defaultSpecularTexture;
		}
	};

	private PBRTextureManager() {
	}

	public void init() {
		defaultNormalTexture = new NativeImageBackedSingleColorTexture(PBRType.NORMAL.getDefaultValue());
		defaultSpecularTexture = new NativeImageBackedSingleColorTexture(PBRType.SPECULAR.getDefaultValue());
	}

	public PBRTextureHolder getHolder(int id) {
		PBRTextureHolder holder = holders.get(id);
		if (holder == null) {
			return defaultHolder;
		}
		return holder;
	}

	public PBRTextureHolder getOrLoadHolder(int id) {
		PBRTextureHolder holder = holders.get(id);
		if (holder == null) {
			holder = loadHolder(id);
			holders.put(id, holder);
		}
		return holder;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private PBRTextureHolder loadHolder(int id) {
		AbstractTexture texture = TextureTracker.INSTANCE.getTexture(id);
		if (texture != null) {
			Class<? extends AbstractTexture> clazz = texture.getClass();
			PBRTextureLoader loader = PBRTextureLoaderRegistry.INSTANCE.getLoader(clazz);
			if (loader != null) {
				int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
				consumer.clear();
				try {
					loader.load(texture, Minecraft.getMinecraft().getResourceManager(), consumer);
					return consumer.toHolder();
				} catch (Exception e) {
					Iris.logger.error("Failed to load PBR textures for texture " + id, e);
				} finally {
					GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
				}
			}
		}
		return defaultHolder;
	}

	public void onDeleteTexture(int id) {
		PBRTextureHolder holder = holders.remove(id);
		if (holder != null) {
			closeHolder(holder);
		}
	}

	public void clear() {
		for (PBRTextureHolder holder : holders.values()) {
			if (holder != defaultHolder) {
				closeHolder(holder);
			}
		}
		holders.clear();
	}

	public void close() {
		clear();
//		defaultNormalTexture.close();
//		defaultSpecularTexture.close();
	}

	private void closeHolder(PBRTextureHolder holder) {
		AbstractTexture normalTexture = holder.getNormalTexture();
		AbstractTexture specularTexture = holder.getSpecularTexture();
		if (normalTexture != defaultNormalTexture) {
			closeTexture(normalTexture);
		}
		if (specularTexture != defaultSpecularTexture) {
			closeTexture(specularTexture);
		}
	}

	private static void closeTexture(AbstractTexture texture) {
		texture.deleteGlTexture();
	}

	public static void notifyPBRTexturesChanged() {
		if (normalTextureChangeListener != null) {
			normalTextureChangeListener.run();
		}

		if (specularTextureChangeListener != null) {
			specularTextureChangeListener.run();
		}
	}

	private class PBRTextureConsumerImpl implements PBRTextureConsumer {
		private AbstractTexture normalTexture;
		private AbstractTexture specularTexture;
		private boolean changed;

		@Override
		public void acceptNormalTexture(@NotNull AbstractTexture texture) {
			normalTexture = texture;
			changed = true;
		}

		@Override
		public void acceptSpecularTexture(@NotNull AbstractTexture texture) {
			specularTexture = texture;
			changed = true;
		}

		public void clear() {
			normalTexture = defaultNormalTexture;
			specularTexture = defaultSpecularTexture;
			changed = false;
		}

		public PBRTextureHolder toHolder() {
			if (changed) {
				return new PBRTextureHolderImpl(normalTexture, specularTexture);
			} else {
				return defaultHolder;
			}
		}
	}

	private static class PBRTextureHolderImpl implements PBRTextureHolder {
		private final AbstractTexture normalTexture;
		private final AbstractTexture specularTexture;

		public PBRTextureHolderImpl(AbstractTexture normalTexture, AbstractTexture specularTexture) {
			this.normalTexture = normalTexture;
			this.specularTexture = specularTexture;
		}

		@Override
		public @NotNull AbstractTexture getNormalTexture() {
			return normalTexture;
		}

		@Override
		public @NotNull AbstractTexture getSpecularTexture() {
			return specularTexture;
		}
	}
}
