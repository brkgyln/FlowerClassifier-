package com.android.cicekdedektoru;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import static com.android.cicekdedektoru.ModelConfig.CLASSIFICATION_THRESHOLD;
import static com.android.cicekdedektoru.ModelConfig.IMAGE_MEAN;
import static com.android.cicekdedektoru.ModelConfig.IMAGE_STD;
import static com.android.cicekdedektoru.ModelConfig.INPUT_IMG_SIZE_HEIGHT;
import static com.android.cicekdedektoru.ModelConfig.INPUT_IMG_SIZE_WIDTH;
import static com.android.cicekdedektoru.ModelConfig.MAX_CLASSIFICATION_RESULTS;
import static com.android.cicekdedektoru.ModelConfig.MODEL_INPUT_SIZE;

public class Classifier {
    private final Interpreter interpreter;

    private Classifier(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public static Classifier classifier(AssetManager assetManager, String modelPath) throws IOException {
        ByteBuffer byteBuffer = loadModelFile(assetManager, modelPath);
        Interpreter interpreter = new Interpreter(byteBuffer);
        return new Classifier(interpreter);
    }

    private static ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public List<Classification> recognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        float[][] result = new float[1][ModelConfig.OUTPUT_LABELS.size()];
        interpreter.run(byteBuffer, result);
        return getSortedResult(result);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MODEL_INPUT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_IMG_SIZE_WIDTH * INPUT_IMG_SIZE_HEIGHT];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < INPUT_IMG_SIZE_WIDTH; ++i) {
            for (int j = 0; j < INPUT_IMG_SIZE_HEIGHT; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        return byteBuffer;
    }
    
    private List<Classification> getSortedResult(float[][] resultsArray) {
        PriorityQueue<Classification> sortedResults = new PriorityQueue<>(
                MAX_CLASSIFICATION_RESULTS,
                (lhs, rhs) -> Float.compare(rhs.confidence, lhs.confidence)
        );

        for (int i = 0; i < ModelConfig.OUTPUT_LABELS.size(); ++i) {
            float confidence = resultsArray[0][i];
            if (confidence > CLASSIFICATION_THRESHOLD) {
                ModelConfig.OUTPUT_LABELS.size();
                sortedResults.add(new Classification(ModelConfig.OUTPUT_LABELS.get(i), confidence));
            }
        }

        return new ArrayList<>(sortedResults);
    }
}
