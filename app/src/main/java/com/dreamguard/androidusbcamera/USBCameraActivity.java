package com.dreamguard.androidusbcamera;

import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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

    private static int cameraMode = 0;

    private USBCamera camera;

    @Bind(R.id.camera_view)
    protected UVCCameraTextureView mCameraView;

    @Bind(R.id.cameraOnOff)
    protected ImageView mCameraOnOff;

    @Bind(R.id.takeContent)
    protected Button mTakeContent;

    @Bind(R.id.welcomeView)
    protected ImageView mWelcomeView;

    @OnClick(R.id.lookPictureIv) void lookPicture(){
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setType("image/*");
//        intent.setDataAndType(Uri.fromFile(file), "image/*");
        startActivity(intent);
    }


    @OnClick(R.id.cameraOnOff) void OnOff(){
        if(!camera.isCameraOpened()){
            boolean ret = camera.open(0);
            if(!ret){
                Toast.makeText(USBCameraActivity.this, "NO_USB_DEVICE", Toast.LENGTH_SHORT).show();
            }else {
                camera.setPreviewSize(PREVIEW_WIDTH,PREVIEW_HEIGHT);
                camera.setPreviewTexture(mCameraView.getSurfaceTexture());
                camera.startPreview();
                mCameraOnOff.setImageResource(R.drawable.camera_open);
                mWelcomeView.setVisibility(View.GONE);
            }
        }else{
            camera.close();
            mCameraOnOff.setImageResource(R.drawable.camera_close);
            mWelcomeView.setVisibility(View.VISIBLE);
        }
    }

    @OnClick(R.id.cameraSwitch) void cameraSwitch(){
        if(cameraMode == 0){
            cameraMode = 1;
            mTakeContent.setBackgroundResource(R.drawable.ic_switch_video);
        }else {
            cameraMode = 0;
            mTakeContent.setBackgroundResource(R.drawable.ic_switch_camera);
        }
    }

    @OnClick(R.id.takeContent) void takeContent(){
        if(camera.isCameraOpened() && cameraMode == 0){
            Toast.makeText(USBCameraActivity.this, "Captured", Toast.LENGTH_SHORT).show();
            camera.captureStill();
        }else if(camera.isCameraOpened() && !camera.isRecording() && cameraMode == 1){
            Toast.makeText(USBCameraActivity.this, "startRecording", Toast.LENGTH_SHORT).show();
            camera.startRecording();
        }else if(camera.isCameraOpened() && camera.isRecording() && cameraMode == 1){
            Toast.makeText(USBCameraActivity.this, "stopRecording", Toast.LENGTH_SHORT).show();
            camera.stopRecording();
        }

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        ButterKnife.bind(this);

        mCameraView.setAspectRatio(PREVIEW_WIDTH/2 / (float)PREVIEW_HEIGHT);

        camera = new USBCamera();
        camera.init(this);
        camera.setCameraType(CameraType.C3D_SBS);

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
