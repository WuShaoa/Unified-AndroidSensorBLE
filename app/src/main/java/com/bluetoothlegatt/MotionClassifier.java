package com.bluetoothlegatt;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import android.util.Log;

import androidx.annotation.RequiresApi;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.SupportPreconditions;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
//TODO: THIS CLASS NEEDS MORE MODIFICATION, AFTER REAL UNDERSTAND OF THE DEEP-LEARNING MODEL...
public class MotionClassifier {
    // Display preferences
    public static final float PROB_THRESHOLD = 0.5f;

    /** Tag for the {@link Log}. */
    private static final String TAG = "MotionClassifier";

    /** Number of results to show in the UI. */
    private static final int RESULTS_TO_SHOW = 3;

    /** Dimensions of inputs. */
    private static final int DIM_BATCH_SIZE = 1;

    public static final int DIM_DATA_SIZE = 18;

    /** Labels corresponding to the output of the vision model. */
    private List<String> labelList = Arrays.asList("Falling", "Other");

    /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs. */
    private float[][][] sensorDataInput = new float[DIM_BATCH_SIZE][1][DIM_DATA_SIZE];//(batch, time-step, input-dim)
    private float[][] predictionOutput;


    private String mModelPath = "my_model2.tflite";

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    private Interpreter tflite;

    /** Options for configuring the Interpreter.*/
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** An instance of host activity.*/
    private Activity activity = null;

    public MotionClassifier(Activity activity) {
        // TODO(b/169965231): Add support for delegates.
        this.activity = activity;

        try {
            initializeModel();
        } catch (IOException e) {
            e.printStackTrace();
        }

        predictionOutput = new float[1][1];
        Log.d(TAG, "Created a MotionClassifier.");
    }

    /** Classifies a frame from the preview stream.
     * flag: 0-left 1-right */
    public void classifyMotion(float[] input, int flag, MotionClassifierCallBack callback) {
        //printTopKLabels(builder);

        if (tflite == null) {
            Log.e(TAG, "Classifier has not been initialized; Skipped.");
//            builder.append(new SpannableString("Uninitialized Classifier."));
        }

        sensorDataInput[0][0] = Arrays.copyOf(input, DIM_DATA_SIZE);

        // Here's where the magic happens!!!
        long startTime = SystemClock.uptimeMillis();
        tflite.run(sensorDataInput, predictionOutput);
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));

        callback.onPredictionResult(predictionOutput[0], endTime - startTime);
    }

    public float[] classifyMotion(float[] input){
        sensorDataInput[0][0] = Arrays.copyOf(input, DIM_DATA_SIZE);

        try {
            tflite.run(sensorDataInput, predictionOutput);
        } catch (InternalError e) {
            return new float[]{0.0F};
        }
        return predictionOutput[0];
    }

    private void initializeModel() throws IOException {
        if (tflite == null) {
            MappedByteBuffer tfLiteModel = FileUtil.loadMappedFile(activity, mModelPath);
            tflite = new Interpreter(tfLiteModel, tfliteOptions);
        }
    }

    public void setModelByPath(String path, Handler handler) {
        String temp = mModelPath;
        mModelPath = path;

        try {
            MappedByteBuffer tfLiteModel = loadMappedFileEx(path);
            tflite = new Interpreter(tfLiteModel, tfliteOptions);
            if(handler != null){
                Message changedModel = new Message();
                changedModel.what = 5;
                changedModel.obj = path;
                handler.sendMessage(changedModel);
            }

        } catch (Exception e) {
            mModelPath = temp;
            Log.e(TAG, "ERROR TO CHANGE MODEL.");
            e.printStackTrace();
        }
    }

    public List<String> getLabels() {
        return labelList;
    }

    public static MappedByteBuffer loadMappedFileEx( @NonNull String filePath) throws Exception {
        SupportPreconditions.checkNotNull(filePath, "File path cannot be null.");
        File model = new File(filePath);

        final MappedByteBuffer r = new RandomAccessFile(filePath, "r").getChannel()
                .map(FileChannel.MapMode.READ_ONLY, 0, model.length());
        return r;
    }

    public void setLabels(List<String> l){ labelList = l;}

    interface MotionClassifierCallBack {
        void onPredictionResult(float[] result,long duration);
    }
}
