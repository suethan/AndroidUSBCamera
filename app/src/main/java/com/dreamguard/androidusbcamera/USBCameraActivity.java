package com.dreamguard.androidusbcamera;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.dreamguard.api.KDXCamera;
import com.dreamguard.widget.CameraViewInterface;

public class USBCameraActivity extends AppCompatActivity {


    private static final int PREVIEW_WIDTH = 2560;

    private static final int PREVIEW_HEIGHT = 720;

    private CameraViewInterface mUVCCameraView;

    private KDXCamera camera;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        final View view = findViewById(R.id.camera_view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!camera.isCameraOpened()){
                    boolean ret = camera.open(0);
                    if(!ret){
                        Toast.makeText(USBCameraActivity.this, "NO_USB_DEVICE", Toast.LENGTH_SHORT).show();
                    }
                }else {
                    camera.close();
                }
            }
        });
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(camera.isCameraOpened()){
                    camera.captureStill();
                    return true;
                }
                return false;
            }
        });
        mUVCCameraView = (CameraViewInterface)view;
        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH/2 / (float)PREVIEW_HEIGHT);
        camera = new KDXCamera();
        camera.init(this,mUVCCameraView);
        camera.setPreviewSize(PREVIEW_WIDTH,PREVIEW_HEIGHT);
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
        mUVCCameraView.onResume();
        hideSystemUI();
    }

    @Override
    public void onPause() {
        mUVCCameraView.onPause();
        camera.close();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        camera.destroy();
    }
}
