package com.main;

import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.bluetoothlegatt.BleUartDataReceiver;
import com.bluetoothlegatt.MotionClassifier;
import com.clj.blesample.DocumentTool;
import com.clj.blesample.adapter.DeviceAdapter;
import com.clj.fastble.data.BleDevice;
import com.minio.minio_android.MinioUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

/* generate runnable procedures corresponding to thread pools in MainActivity */
public class RunnableFactory {
    private static final String TAG = RunnableFactory.class.getSimpleName();
    private static final int LENGTH = 50; //length of data shown
    private int leftCount = 0;
    private int rightCount = 0;
    private Boolean leftFlag = false;
    private Boolean rightFlag = false;
    private Hashtable<String, float[]> lrData = new Hashtable<>();

    // message what: 0-left-data-ok 1-right-data-ok 2-upload-ok 3-download-ok 4-model-predicted

    Runnable getDataPipelineRunnable(DeviceAdapter mDeviceAdapter, BleDevice bleDevice, byte[] data, String savingDir, MotionClassifier mMotionClassifier, Handler handler) {
        return ()-> {
            // Parsing data here
            DeviceAdapter.DeviceAttrs devAttr = mDeviceAdapter.getAttrs(bleDevice.getKey());
            if (devAttr != null) { //判断没有断联
                String devMac = bleDevice.getMac();
                //这里必须保证接收数据的前后顺序
                if (!DocumentTool.isFileExists(savingDir + "/" + devMac.replace(':', '_') + ".txt"))
                    DocumentTool.addFile(savingDir + "/" + devMac.replace(':', '_') + ".txt");
                //DocumentTool.appendFileData(data_dir + "/" + devMac.replace(':', '_') + ".txt", parsed_data.toString().getBytes(StandardCharsets.UTF_8));
                DocumentTool.appendFileData(savingDir + "/" + devMac.replace(':', '_') + ".txt", data);

                BleUartDataReceiver data_parser = devAttr.parser;
                data_parser.receiveData(data);

                data_parser.setCb(parsed_data -> {
                    float[] predictData = parsed_data.toFloatList(false);
                    //TODO: input size? HOW TO MERGE LEFT and RIGHT DATA?
                    Message dataParsed = new Message();

                    if (devAttr.side == DeviceAdapter.Side.LEFT) {
                        dataParsed.what = 0;
                        //leftCount = (leftCount < LENGTH) ? leftCount + 1 : 0;
                        leftCount++;
                        leftFlag = true;
                        lrData.put("left", predictData);
                    } else {
                        dataParsed.what = 1;
                        //rightCount = (rightCount < LENGTH) ? rightCount + 1 : 0;
                        rightCount++;
                        rightFlag = true;
                        lrData.put("right",predictData);
                    }

                    if(leftFlag && rightFlag){
                        leftFlag = rightFlag = false;
                        float[] leftInput = lrData.get("left");
                        float[] rightInput = lrData.get("right");
                        if(leftInput != null && rightInput != null) {
                            leftInput = Arrays.copyOf(leftInput, leftInput.length + rightInput.length);
                            // combing two parts of inputs to leftInput
                            System.arraycopy(rightInput, 0, leftInput, leftInput.length, rightInput.length);

                            float predictResult = mMotionClassifier.classifyMotion(leftInput)[0];

                            devAttr.xAxis[devAttr.counter] = (devAttr.side == DeviceAdapter.Side.LEFT) ? leftCount : rightCount;
                            devAttr.yAxis[devAttr.counter] = predictResult; //TODO: buffer & classify
                            devAttr.counter++;
                            devAttr.counter %= LENGTH;

                            Log.d(TAG, devAttr.side == DeviceAdapter.Side.LEFT ? "LEFT" : "RIGHT");
                            Log.d(TAG, String.valueOf(devAttr.xAxis));


                            ArrayList<Object> xyAxis = new ArrayList<>(2);
                            xyAxis.add(devAttr.xAxis);
                            xyAxis.add(devAttr.yAxis);

                            Message modelPredicted = new Message();
                            modelPredicted.what = 4;
                            modelPredicted.obj = xyAxis;
                            //TODO: separate the LEFT/RIGHT and TRAINED message

                            handler.sendMessage(modelPredicted);
                        }
                    }

                    handler.sendMessage(dataParsed);
                });

                data_parser.parseData();
            }
        };
    }

    Runnable getUploadRunnable(String fileDir, MinioUtils client, Handler handler){
        //configureClient();
        return () -> {
            File dir = new File(fileDir);
            String[] files = dir.list();

            if (files != null) {
                for (String name : files) {
                    Log.d(TAG, "Uploading file: " + name);
                    client.upload(Paths.get(fileDir, name).toAbsolutePath().toString(), name);
                }
            }
            Message uploadOk = new Message();
            uploadOk.what = 2;
            handler.sendMessage(uploadOk);
        };
    }

    Runnable getDownloadRunnable(String name, String modelDir, MinioUtils client, Handler handler){
        //configureClient();
        return () -> {
            DocumentTool.addFile(modelDir + "/" + name);
            client.download(name,modelDir + "/" + name);
            Message downloadOk = new Message();
            downloadOk.what = 3;
            handler.sendMessage(downloadOk);
        };
    }
}
