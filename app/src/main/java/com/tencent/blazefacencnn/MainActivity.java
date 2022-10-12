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
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements  MyFaceDetectorCallback
{
    public static final int REQUEST_CAMERA = 100;
    public static final String TAG = "MainActivity";

    private BlazeFaceNcnn blazefacencnn = new BlazeFaceNcnn();
    private int facing = 0;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;
    Camera2BasicFragment mCamera2BasicFragment;
    Bitmap imageBitMap;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (null == savedInstanceState) {
            mCamera2BasicFragment =  Camera2BasicFragment.newInstance();
            mCamera2BasicFragment.faceDetectorCallback = this;
            mCamera2BasicFragment.blazeFaceNcnn = blazefacencnn;
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, mCamera2BasicFragment)
                    .commit();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Create an instance of Camera
        // Create our Preview view and set it as the content of our activity.


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

        reload();
    }

    @Override
    public void onFaceDetect(ArrayList<DetectFace> detectFaces) {
        Log.d(TAG, "detect Faces: " + detectFaces.size());
        for (DetectFace detectFace: detectFaces) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float maxSize = (float)Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            float scale = (float)1440/640;
            float x = scale * detectFace.x;
            float y = scale * detectFace.y;
            float width = scale * detectFace.width;
            float height = scale * detectFace.height;
            mCamera2BasicFragment.rectangleOverlay.setRect(new Rect(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height)));
            Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
            Bitmap result = blazefacencnn.cropFace(bitmap, detectFace.byteBuffer, 640, 480, 270, Math.round(detectFace.x), Math.round(detectFace.y), Math.round(detectFace.width), Math.round(detectFace.height));
            this.imageBitMap = result;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setImage();
                }
            });
        }
    }

    private void setImage() {
        mCamera2BasicFragment.imageView.setImageBitmap(imageBitMap);
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


}
