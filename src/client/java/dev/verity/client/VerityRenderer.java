package dev.verity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.verity.VerityEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws Verity as a glowing yellow sphere with a smiley face. No model file needed. */
public class VerityRenderer extends EntityRenderer<VerityEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("verity", "textures/entity/verity.png");
	private static final float RADIUS = 0.2f;
	private static final int LAT = 12;
	private static final int LON = 18;

	public VerityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.12f;
		this.shadowStrength = 0.4f;
	}

	@Override
	public ResourceLocation getTextureLocation(VerityEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(VerityEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int packedLight) {
		if (entity.isInvisible()) return;
		float time = entity.tickCount + partialTick;
		int mood = entity.getMood();

		poseStack.pushPose();
		poseStack.translate(0.0, RADIUS, 0.0);
		float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
		float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		poseStack.mulPose(Axis.YP.rotationDegrees(-headYaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

		float pulse = 0.92f + 0.08f * Mth.sin(time * 0.15f);
		boolean stare = mood == VerityEntity.MOOD_STARE;
		// Body
		sphere(pose, vc, RADIUS, 1.0f * pulse, (stare ? 0.75f : 0.88f) * pulse, 0.15f, 1.0f);

		// Face, drawn on the +Z side just above the surface.
		float er = stare ? 0.9f : 0.12f;
		float eg = stare ? 0.05f : 0.08f;
		float eb = stare ? 0.05f : 0.02f;
		boolean blink = !stare && ((int) time % 90) < 3;
		float eyeH = blink ? 0.008f : (stare ? 0.03f : 0.045f);
		quad(pose, vc, -0.085f, 0.03f, 0.03f, eyeH, 0f, er, eg, eb, 1f);
		quad(pose, vc, 0.085f, 0.03f, 0.03f, eyeH, 0f, er, eg, eb, 1f);
		if (stare) {
			// Wide grin with teeth.
			for (int i = -4; i <= 4; i++) {
				float x = i * 0.025f;
				float y = -0.07f + (x * x) * 1.2f;
				quad(pose, vc, x, y, 0.027f, 0.05f, 0f, 0.1f, 0.0f, 0.0f, 1f);
				if (i % 2 == 0) quad(pose, vc, x, y + 0.012f, 0.014f, 0.02f, 0.003f, 1f, 1f, 0.95f, 1f);
			}
		} else {
			// Friendly smile: a curved row of small dark squares.
			for (int i = -4; i <= 4; i++) {
				float x = i * 0.022f;
				float y = -0.06f + (x * x) * 2.2f;
				quad(pose, vc, x, y, 0.024f, 0.018f, 0f, 0.12f, 0.08f, 0.02f, 1f);
			}
		}

		// Soft glow halo.
		float haloR = 1.0f;
		float haloG = mood == VerityEntity.MOOD_ALERT ? 0.45f : (stare ? 0.2f : 0.95f);
		float haloB = mood == VerityEntity.MOOD_ALERT ? 0.1f : 0.35f;
		sphere(pose, vc, RADIUS * (1.35f + 0.05f * Mth.sin(time * 0.2f)), haloR, haloG, haloB, 0.18f);

		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
	}

	private static void sphere(PoseStack.Pose pose, VertexConsumer vc, float r, float red, float green, float blue, float alpha) {
		for (int i = 0; i < LAT; i++) {
			float t0 = Mth.PI * i / LAT;
			float t1 = Mth.PI * (i + 1) / LAT;
			for (int j = 0; j < LON; j++) {
				float p0 = Mth.TWO_PI * j / LON;
				float p1 = Mth.TWO_PI * (j + 1) / LON;
				vertex(pose, vc, r, t0, p0, red, green, blue, alpha);
				vertex(pose, vc, r, t1, p0, red, green, blue, alpha);
				vertex(pose, vc, r, t1, p1, red, green, blue, alpha);
				vertex(pose, vc, r, t0, p1, red, green, blue, alpha);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float r, float theta, float phi,
			float red, float green, float blue, float alpha) {
		float nx = Mth.sin(theta) * Mth.cos(phi);
		float ny = Mth.cos(theta);
		float nz = Mth.sin(theta) * Mth.sin(phi);
		vc.addVertex(pose, nx * r, ny * r, nz * r)
				.setColor(red, green, blue, alpha)
				.setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, nx, ny, nz);
	}

	/** A small flat rectangle centred at (x, y) facing +Z. */
	private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float w, float h, float lift,
			float red, float green, float blue, float alpha) {
		// Push the face out so it hugs the sphere's curve.
		float z = (float) Math.sqrt(Math.max(0, RADIUS * RADIUS - x * x - y * y)) + 0.004f + lift;
		float x0 = x - w / 2, x1 = x + w / 2, y0 = y - h / 2, y1 = y + h / 2;
		corner(pose, vc, x0, y0, z, red, green, blue, alpha);
		corner(pose, vc, x1, y0, z, red, green, blue, alpha);
		corner(pose, vc, x1, y1, z, red, green, blue, alpha);
		corner(pose, vc, x0, y1, z, red, green, blue, alpha);
	}

	private static void corner(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z,
			float red, float green, float blue, float alpha) {
		vc.addVertex(pose, x, y, z)
				.setColor(red, green, blue, alpha)
				.setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, 0f, 0f, 1f);
	}
}
