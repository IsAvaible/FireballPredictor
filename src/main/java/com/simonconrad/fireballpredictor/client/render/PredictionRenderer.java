package com.simonconrad.fireballpredictor.client.render;

import com.simonconrad.fireballpredictor.math.PredictionData;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PredictionRenderer {

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Camera camera, ClientWorld world, PredictionData data) {
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

    }


}
