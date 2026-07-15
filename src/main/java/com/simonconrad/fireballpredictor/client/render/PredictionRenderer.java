package com.simonconrad.fireballpredictor.client.render;

import com.simonconrad.fireballpredictor.math.PredictionData;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PredictionRenderer {

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Camera camera, PredictionData data) {
        Vec3d cameraPos = camera.getPos();
        
        // Render Trajectory Line
        if (data.path != null && data.path.size() > 1) {
            VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            MatrixStack.Entry entry = matrices.peek();
            
            for (int i = 0; i < data.path.size() - 1; i++) {
                Vec3d p1 = data.path.get(i);
                Vec3d p2 = data.path.get(i + 1);
                
                float dx = (float)(p2.x - p1.x);
                float dy = (float)(p2.y - p1.y);
                float dz = (float)(p2.z - p1.z);
                
                float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length > 0) {
                    dx /= length;
                    dy /= length;
                    dz /= length;
                }
                
                lineConsumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z)
                    .color(255, 0, 0, 255)
                    .normal(entry, dx, dy, dz);
                lineConsumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z)
                    .color(255, 0, 0, 255)
                    .normal(entry, dx, dy, dz);
            }
            matrices.pop();
        }

        // Render Block Highlights
        if (data.brokenBlocks != null && !data.brokenBlocks.isEmpty()) {
            VertexConsumer blockConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            MatrixStack.Entry entry = matrices.peek();

            for (BlockPos pos : data.brokenBlocks) {
                Box box = new Box(pos).expand(0.01);
                drawBox(positionMatrix, entry, blockConsumer, box, 255, 165, 0, 255); // Orange outline
            }
            matrices.pop();
        }
    }

    private static void drawBox(Matrix4f matrix, MatrixStack.Entry entry, VertexConsumer consumer, Box box, int r, int g, int b, int a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom square
        drawLine(matrix, entry, consumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(matrix, entry, consumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(matrix, entry, consumer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(matrix, entry, consumer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top square
        drawLine(matrix, entry, consumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(matrix, entry, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(matrix, entry, consumer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(matrix, entry, consumer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical pillars
        drawLine(matrix, entry, consumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(matrix, entry, consumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(matrix, entry, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(matrix, entry, consumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void drawLine(Matrix4f matrix, MatrixStack.Entry entry, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0) {
            dx /= length;
            dy /= length;
            dz /= length;
        }

        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(entry, dx, dy, dz);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(entry, dx, dy, dz);
    }
}
