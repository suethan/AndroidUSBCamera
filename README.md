# AndroidUSBCamera
android usb camera on non-rooted Android devices.<br/>
在Android设备上使用外接usb摄像头的项目，无需root权限。


<h2>use step:</h2>
<h3>1.init view and camera</h3>

    mCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

    camera = new USBCamera();
    camera.init(this);
    camera.setCameraType(CameraType.C3D_SBS);

<h3>2. start preview</h3>

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
<h3>3. captureStill</h3>

        if(camera.isCameraOpened()){
            Toast.makeText(USBCameraActivity.this, "Captured", Toast.LENGTH_SHORT).show();
            camera.captureStill();
        }
<h3>4. recording </h3>

        if(camera.isCameraOpened() && !camera.isRecording()){
            Toast.makeText(USBCameraActivity.this, "startRecording", Toast.LENGTH_SHORT).show();
            camera.startRecording();
        }
        if(camera.isCameraOpened() && camera.isRecording()){
            Toast.makeText(USBCameraActivity.this, "stopRecording", Toast.LENGTH_SHORT).show();

            camera.stopRecording();
        }
<h3>5. stop</h3>

        if(camera.isCameraOpened()){
           camera.close();
        }

<h3>6. destory</h3>

    camera.destroy();





