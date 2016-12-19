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


<h2>Refer</h2>
http://bigflake.com/mediacodec/

<h2>Todo</h2>

    1.同时录制视频和声音。
        需要常见的宽高比例，如果是双摄像头，目前简易做法，camera.setCameraType(CameraType.C3D_SBS)，将全宽图片压缩为半宽。
    2.编写Camera应用，测试插件。
    3.优化压缩方式。
    4.支持多种预览方式。
    5.支持两种拍摄和录制方式
        原始数据。
        预览画面显示的数据。

<h2>讨论</h2>

	QQ群：199324650






