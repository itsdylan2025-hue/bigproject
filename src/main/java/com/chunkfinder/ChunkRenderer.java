package com.chunkfinder;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws a translucent sheet + glowing edges + fading walls over each flagged chunk.
 * Geode chunks use the geode colour, every other flagged chunk uses the "other" colour.
 * Based on Fabric's official 1.21.11 "custom render pipeline" example.
 */
public final class ChunkRenderer {

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(ChunkFinderClient.MOD_ID, "pipeline/chunk_highlight_through_walls"))
					.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
					.withCull(false)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build());

	private static final RenderPipeline DEPTH_TESTED = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(ChunkFinderClient.MOD_ID, "pipeline/chunk_highlight"))
					.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
					.withCull(false)
					.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static BufferBuilder buffer;
	private static MappableRingBuffer vertexBuffer;
	private static boolean failed;

	private ChunkRenderer() {}

	public static void init() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(ChunkRenderer::onRender);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
	}

	// ------------------------------------------------------------ extraction phase

	private static void onRender(WorldRenderContext context) {
		ChunkFinderConfig cfg = ChunkFinderConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (failed || !cfg.enabled || mc.level == null) {
			return;
		}

		try {
			RenderPipeline pipeline = cfg.throughWalls ? THROUGH_WALLS : DEPTH_TESTED;
			Matrix4fc pose = context.matrices().last().pose();
			Vec3 cam = context.worldState().cameraRenderState.pos;
			float[] geodeRgb = cfg.geodeRgb();
			float[] otherRgb = cfg.otherRgb();

			ActivityScanner.forEachFlagged((cx, cz, result) -> {
				if (buffer == null) {
					buffer = new BufferBuilder(ALLOCATOR, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
				}
				float[] rgb = result.kind() == ActivityScanner.Kind.GEODE ? geodeRgb : otherRgb;
				addChunk(buffer, pose, cam, cfg, rgb, cx, cz, result.y());
			});

			if (buffer != null) {
				drawBuffer(mc, pipeline);
			}
		} catch (Throwable t) {
			failed = true;
			buffer = null;
			ChunkFinderClient.LOGGER.error("Chunk Finder highlight renderer crashed and was disabled", t);
		}
	}

	private static void addChunk(BufferBuilder b, Matrix4fc m, Vec3 cam, ChunkFinderConfig cfg,
								 float[] rgb, int cx, int cz, double y) {
		// Offsets are computed in double precision relative to the camera, so far-away chunks don't jitter.
		float x0 = (float) (cx * 16.0 - cam.x);
		float x1 = x0 + 16f;
		float z0 = (float) (cz * 16.0 - cam.z);
		float z1 = z0 + 16f;
		float yp = (float) (y + 0.05 - cam.y);
		float r = rgb[0];
		float g = rgb[1];
		float bl = rgb[2];

		// Main sheet
		flatQuad(b, m, r, g, bl, cfg.fillAlpha, x0, x1, yp, z0, z1);

		// Bright border strips
		float w = 0.2f;
		flatQuad(b, m, r, g, bl, cfg.edgeAlpha, x0, x1, yp, z0, z0 + w);
		flatQuad(b, m, r, g, bl, cfg.edgeAlpha, x0, x1, yp, z1 - w, z1);
		flatQuad(b, m, r, g, bl, cfg.edgeAlpha, x0, x0 + w, yp, z0 + w, z1 - w);
		flatQuad(b, m, r, g, bl, cfg.edgeAlpha, x1 - w, x1, yp, z0 + w, z1 - w);

		// Walls that fade out towards the top
		if (cfg.wallHeight > 0f) {
			float yt = yp + cfg.wallHeight;
			wall(b, m, r, g, bl, cfg.wallAlpha, x0, z0, x1, z0, yp, yt);
			wall(b, m, r, g, bl, cfg.wallAlpha, x1, z0, x1, z1, yp, yt);
			wall(b, m, r, g, bl, cfg.wallAlpha, x1, z1, x0, z1, yp, yt);
			wall(b, m, r, g, bl, cfg.wallAlpha, x0, z1, x0, z0, yp, yt);
		}
	}

	private static void flatQuad(BufferBuilder b, Matrix4fc m, float r, float g, float bl, float a,
								 float xMin, float xMax, float y, float zMin, float zMax) {
		b.addVertex(m, xMin, y, zMin).setColor(r, g, bl, a);
		b.addVertex(m, xMax, y, zMin).setColor(r, g, bl, a);
		b.addVertex(m, xMax, y, zMax).setColor(r, g, bl, a);
		b.addVertex(m, xMin, y, zMax).setColor(r, g, bl, a);
	}

	private static void wall(BufferBuilder b, Matrix4fc m, float r, float g, float bl, float aBottom,
							 float xA, float zA, float xB, float zB, float yBottom, float yTop) {
		b.addVertex(m, xA, yBottom, zA).setColor(r, g, bl, aBottom);
		b.addVertex(m, xB, yBottom, zB).setColor(r, g, bl, aBottom);
		b.addVertex(m, xB, yTop, zB).setColor(r, g, bl, 0f);
		b.addVertex(m, xA, yTop, zA).setColor(r, g, bl, 0f);
	}

	// ------------------------------------------------------------ drawing phase

	private static void drawBuffer(Minecraft client, RenderPipeline pipeline) {
		MeshData built = buffer.buildOrThrow();
		MeshData.DrawState drawParameters = built.drawState();
		VertexFormat format = drawParameters.format();

		GpuBuffer vertices = upload(drawParameters, format, built);
		draw(client, pipeline, built, drawParameters, vertices, format);

		// Rotate so we're less likely to touch a buffer the GPU is still using
		vertexBuffer.rotate();
		buffer = null;
	}

	private static GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData built) {
		int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

		if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = new MappableRingBuffer(() -> ChunkFinderClient.MOD_ID + " highlight vertices",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
		}

		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
				vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(built.vertexBuffer(), mappedView.data());
		}

		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, RenderPipeline pipeline, MeshData built,
							 MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;

		if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
			indexType = built.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
			indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
			indexType = shapeIndexBuffer.type();
		}

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> ChunkFinderClient.MOD_ID + " highlight pass",
						client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
						client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(pipeline);

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			renderPass.setVertexBuffer(0, vertices);
			renderPass.setIndexBuffer(indices, indexType);

			//noinspection ConstantValue
			renderPass.drawIndexed(0 / format.getVertexSize(), 0, drawParameters.indexCount(), 1);
		}

		built.close();
	}

	private static void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
