package com.dreamguard.androidusbcamera;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.dreamguard.api.CameraType;
import com.dreamguard.api.KDXCamera;
import com.dreamguard.api.CameraViewInterface;

import java.util.HashMap;

/**
 *  create by hailin.dai 2016.12.10.
 */
public class USBCameraActivity extends AppCompatActivity {


    private static final int PREVIEW_WIDTH = 2560;

    private static final int PREVIEW_HEIGHT = 720;

    private CameraViewInterface mUVCCameraView;

    private KDXCamera camera;

    private Button record;

    private Button captureStill;

    private Button normal;

    private Button stereo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        final View view = findViewById(R.id.camera_view);
        record = (Button) findViewById(R.id.record);
        captureStill = (Button) findViewById(R.id.captureStill);
        normal = (Button) findViewById(R.id.showNormal);
        stereo = (Button) findViewById(R.id.showStereo);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!camera.isCameraOpened()){
                    boolean ret = camera.open(0);
                    if(!ret){
                        Toast.makeText(USBCameraActivity.this, "NO_USB_DEVICE", Toast.LENGTH_SHORT).show();
                    }else {
                        camera.setPreviewSize(PREVIEW_WIDTH,PREVIEW_HEIGHT);
                        camera.setPreviewTexture(mUVCCameraView.getSurfaceTexture());
                        camera.startPreview();
                    }
                }else {
                    camera.close();
                }
            }
        });

        captureStill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(camera.isCameraOpened()){
                    Toast.makeText(USBCameraActivity.this, "Captured", Toast.LENGTH_SHORT).show();
                    camera.captureStill();
                }
            }
        });
        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(camera.isCameraOpened() && !camera.isRecording()){
                    camera.startRecording();
                }
                if(camera.isCameraOpened() && camera.isRecording()){
                    camera.stopRecording();
                }
            }
        });


        mUVCCameraView = (CameraViewInterface)view;
        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH/2 / (float)PREVIEW_HEIGHT);
        camera = new KDXCamera();
        camera.init(this);
        camera.setCameraType(CameraType.C3D_SBS);

        normal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashMap<String ,String> rendererMode = new HashMap<String, String>();
                rendererMode.put("rendererMode","0");
                mUVCCameraView.setRendererParam(rendererMode);
            }
        });
        stereo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashMap<String ,String> rendererMode = new HashMap<String, String>();
                rendererMode.put("rendererMode","1");
                mUVCCameraView.setRendererParam(rendererMode);
            }
        });
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    public void onPause() {
        camera.close();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        camera.destroy();
    }


}
