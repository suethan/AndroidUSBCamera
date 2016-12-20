package com.dreamguard.androidusbcamera;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.dreamguard.api.CameraType;
import com.dreamguard.api.USBCamera;
import com.dreamguard.widget.UVCCameraTextureView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 *  create by hailin.dai 2016.12.10.
 */
public class USBCameraActivity extends AppCompatActivity {


    private static final int PREVIEW_WIDTH = 2560;

    private static final int PREVIEW_HEIGHT = 720;

    private USBCamera camera;

    @Bind(R.id.camera_view)
    protected UVCCameraTextureView mCameraView;


    @OnClick(R.id.open) void open(){
        if(!camera.isCameraOpened()){
            boolean ret = camera.open(0);
            if(!ret){
                Toast.makeText(USBCameraActivity.this, "NO_USB_DEVICE", Toast.LENGTH_SHORT).show();
            }else {
                camera.setPreviewSize(PREVIEW_WIDTH,PREVIEW_HEIGHT);
                camera.setPreviewTexture(mCameraView.getSurfaceTexture());
                camera.startPreview();
            }
        }
    }
    @OnClick(R.id.close) void close(){
        if(camera.isCameraOpened()){
           camera.close();
        }
    }
    @OnClick(R.id.captureStill) void captureStill(){
        if(camera.isCameraOpened()){
            Toast.makeText(USBCameraActivity.this, "Captured", Toast.LENGTH_SHORT).show();
            camera.captureStill();
        }
    }
    @OnClick(R.id.record) void record(){
        if(camera.isCameraOpened() && !camera.isRecording()){
            Toast.makeText(USBCameraActivity.this, "startRecording", Toast.LENGTH_SHORT).show();
            camera.startRecording();
        }
        if(camera.isCameraOpened() && camera.isRecording()){
            Toast.makeText(USBCameraActivity.this, "stopRecording", Toast.LENGTH_SHORT).show();

            camera.stopRecording();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        ButterKnife.bind(this);

        mCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

        camera = new USBCamera();
        camera.init(this);
//        camera.setCameraType(CameraType.C3D_SBS);

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

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }


}
