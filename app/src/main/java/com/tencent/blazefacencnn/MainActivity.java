// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.blazefacencnn;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.tencent.blazefacencnn.api.*;
import com.tencent.blazefacencnn.api.models.FaceDetectResult;
import com.tencent.blazefacencnn.tflite.SimilarityClassifier;
import com.tencent.blazefacencnn.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MainActivity extends AppCompatActivity implements  MyFaceDetectorCallback, SensorEventListener
{
    public static final int REQUEST_CAMERA = 100;
    public static final String TAG = "MainActivity";
    // MobileFaceNet
    private static final int TF_OD_API_INPUT_SIZE = 112;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    private BlazeFaceNcnn blazefacencnn = new BlazeFaceNcnn();
    private int facing = 0;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;
    private ApiClient apiClient = new ApiClient();
    Camera2BasicFragment mCamera2BasicFragment;
    Bitmap imageBitMap;
    private  SensorManager mSensorManager;
    private  Sensor mAccelerometer;
    private int accelerometerOrientation = 0;
    private SimilarityClassifier detector;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (null == savedInstanceState) {
            mCamera2BasicFragment = Camera2BasicFragment.newInstance();
            mCamera2BasicFragment.faceDetectorCallback = this;
            mCamera2BasicFragment.blazeFaceNcnn = blazefacencnn;
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, mCamera2BasicFragment)
                    .commit();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Create an instance of Camera
        // Create our Preview view and set it as the content of our activity.
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
        buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                int new_facing = 1 - facing;

                facing = new_facing;
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_model)
                {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {

            }
        });

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            //cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Exception initializing classifier!: " + e.toString());
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        reload();
    }

    @Override
    public void onFaceDetect(ArrayList<DetectFace> detectFaces) {
        Log.d(TAG, "detect Faces: " + detectFaces.size());
        for (DetectFace detectFace: detectFaces) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float scale = (float)displayMetrics.widthPixels/480;
            float x = scale * detectFace.x;
            float y = scale * detectFace.y;
            float width = scale * detectFace.width;
            float height = scale * detectFace.height;
            Rect rect = new Rect(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height));
            mCamera2BasicFragment.rectangleOverlay.setRect(rect);

            Bitmap bitmap = Bitmap.createBitmap(660, 500, Bitmap.Config.ARGB_8888);
            Bitmap result = blazefacencnn.cropFace(
                    bitmap,
                    detectFace.byteBuffer,
                    640,
                    480,
                    detectFace.winWidth,
                    detectFace.winHeight,
                    detectFace.orientation,
                    detectFace.accelerometerOrientation,
                    Math.round(detectFace.x - 10), Math.round(detectFace.y - 10),
                    Math.round(detectFace.width + 20),
                    Math.round(detectFace.height + 20)
            );
            this.imageBitMap = result;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setImage();
                }
            });
            Bitmap compressImage = ImageHelper.compressImage(bitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE);
            List<SimilarityClassifier.Recognition> recognitions =  detector.recognizeImage(compressImage, false);
            if (recognitions.size() > 0) {
               Log.d(TAG, "recognization: " + recognitions.get(0).getDistance());
            }
        }
    }

    private void setImage() {
        mCamera2BasicFragment.imageView.setImageBitmap(imageBitMap);
    }

    private void drawText(String text) {
        mCamera2BasicFragment.rectangleOverlay.setText(text);
    }

    private void reload()
    {
        boolean ret_init = blazefacencnn.loadModel(getAssets(), current_model, current_cpugpu);
        if (!ret_init)
        {
            Log.e("MainActivity", "blazefacencnn loadModel failed");
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float acceleration_x = sensorEvent.values[0];
        float acceleration_y = sensorEvent.values[1];
        float acceleration_z = sensorEvent.values[2];

        if (acceleration_y > 7)
        {
            accelerometerOrientation = 0;
        }
        if (acceleration_x < -7)
        {
            accelerometerOrientation = 90;
        }
        if (acceleration_y < -7)
        {
            accelerometerOrientation = 180;
        }
        if (acceleration_x > 7)
        {
            accelerometerOrientation = 270;
        }

        mCamera2BasicFragment.accelerometerOrientation = accelerometerOrientation;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}
