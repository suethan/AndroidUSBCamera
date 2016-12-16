package com.dreamguard.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import android.util.Log;


import com.dreamguard.util.FileSwapHelper;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * Created by robi on 2016-04-01 10:45.
 */
public class MediaMuxerRunnable extends Thread {

    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;
    public static boolean DEBUG = true;
    private static MediaMuxerRunnable mediaMuxerThread;
    private final Object lock = new Object();
    private MediaMuxer mediaMuxer;
    private Vector<MuxerData> muxerDatas;
    private volatile boolean isExit = false;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private volatile boolean isVideoAdd;
    private volatile boolean isAudioAdd;
    private AudioRunnable audioThread;
    private VideoRunnable videoThread;
    private FileSwapHelper fileSwapHelper;
    private boolean isMediaMuxerStart = false;
    private MediaFormat videoMediaFormat;
    private MediaFormat audioMediaFormat;

    private volatile long writeCount = 0;

    private MediaMuxerRunnable() {
    }

    public static void startMuxer() {
        if (mediaMuxerThread == null) {
            synchronized (MediaMuxerRunnable.class) {
                if (mediaMuxerThread == null) {
                    mediaMuxerThread = new MediaMuxerRunnable();
                    mediaMuxerThread.start();
                }
            }
        }
    }

    public static void stopMuxer() {
        if (mediaMuxerThread != null) {
            mediaMuxerThread.exit();
            try {
                mediaMuxerThread.join();
            } catch (InterruptedException e) {

            }
            mediaMuxerThread = null;
        }
    }

    public static void addVideoFrameData(byte[] data) {
        if (mediaMuxerThread != null) {
            mediaMuxerThread.addVideoData(data);
        }
    }

    private void initMuxer() {
        muxerDatas = new Vector<>();
        fileSwapHelper = new FileSwapHelper();

        audioThread = new AudioRunnable(new WeakReference<MediaMuxerRunnable>(this));
        videoThread = new VideoRunnable(1280, 720, new WeakReference<MediaMuxerRunnable>(this));

        audioThread.start();
        videoThread.start();

        fileSwapHelper.requestSwapFile(true);
        restartMediaMuxer();
    }

    private void addVideoData(byte[] data) {
        if (videoThread != null) {
            videoThread.add(data);
        }
    }

    private void restartMediaMuxer() {
        try {
            resetMediaMuxer();
            if (videoMediaFormat != null) {
                videoTrackIndex = mediaMuxer.addTrack(videoMediaFormat);
                isVideoAdd = true;
            }
            if (audioMediaFormat != null) {
                audioTrackIndex = mediaMuxer.addTrack(audioMediaFormat);
                isAudioAdd = true;
            }
            requestStart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopMediaMuxer() {
        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mediaMuxer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            isAudioAdd = false;
            isVideoAdd = false;
            isMediaMuxerStart = false;
            mediaMuxer = null;
        }
    }

    private void resetMediaMuxer() throws Exception {
        stopMediaMuxer();
        mediaMuxer = new MediaMuxer(fileSwapHelper.getNextFileName(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        Log.e("angcyo-->", "创建混合器,保存至:" + fileSwapHelper.getNextFileName());
    }

    public synchronized void setMediaFormat(int index, MediaFormat mediaFormat) {
        if (mediaMuxer == null) {
            return;
        }

        if (index == TRACK_VIDEO) {
            if (videoMediaFormat == null) {
                videoMediaFormat = mediaFormat;
                videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                isVideoAdd = true;
            }
//            else if (videoMediaFormat != mediaFormat) {
//                try {
//                    resetMediaMuxer();
//                    videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
//                    videoMediaFormat = mediaFormat;
//                    isVideoAdd = true;
//                    if (DEBUG) Log.e("angcyo-->", "添加视轨 完成");
//                    if (audioMediaFormat != null) {
//                        audioTrackIndex = mediaMuxer.addTrack(audioMediaFormat);
//                        isAudioAdd = true;
//                        if (DEBUG) Log.e("angcyo-->", "添加音轨 完成 使用旧的 audioMediaFormat");
//                    }
//                } catch (Exception e) {
//                    restartMediaMuxer();
//                }
//            }
        } else {
            if (audioMediaFormat == null) {
                audioMediaFormat = mediaFormat;
                audioTrackIndex = mediaMuxer.addTrack(mediaFormat);
                isAudioAdd = true;
            }
//            else if (audioMediaFormat != mediaFormat) {
//                try {
//                    resetMediaMuxer();
//                    audioTrackIndex = mediaMuxer.addTrack(mediaFormat);
//                    audioMediaFormat = mediaFormat;
//                    isAudioAdd = true;
//                    if (DEBUG) Log.e("angcyo-->", "添加音轨 完成");
//                    if (videoMediaFormat != null) {
//                        videoTrackIndex = mediaMuxer.addTrack(videoMediaFormat);
//                        isVideoAdd = true;
//                        if (DEBUG) Log.e("angcyo-->", "添加视轨 完成 使用旧的 videoMediaFormat");
//                    }
//                } catch (Exception e) {
//                    restartMediaMuxer();
//                }
//            }
        }

        requestStart();
    }

    private void exit() {
        if (videoThread != null) {
            videoThread.exit();
            try {
                videoThread.join();
            } catch (InterruptedException e) {

            }
        }
        if (audioThread != null) {
            audioThread.exit();
            try {
                audioThread.join();
            } catch (InterruptedException e) {

            }
        }

        isExit = true;
        synchronized (lock) {
            lock.notify();
        }
    }


    public void addMuxerData(MuxerData data) {
        if (muxerDatas == null) {
            return;
        }
        muxerDatas.add(data);
        synchronized (lock) {
            lock.notify();
        }
    }


    @Override
    public void run() {
        initMuxer();
        while (!isExit) {
            if (isMediaMuxerStart) {
                //混合器开启后
                if (muxerDatas.isEmpty()) {
                    synchronized (lock) {
                        try {
                            if (DEBUG) Log.e("ang-->", "混合等待 混合数据...");
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (fileSwapHelper.requestSwapFile()) {
                        //需要切换文件
                        String nextFileName = fileSwapHelper.getNextFileName();
                        if (DEBUG) Log.e("angcyo-->", "正在重启混合器..." + nextFileName);
                        restartMediaMuxer();
                    } else {
                        MuxerData data = muxerDatas.remove(0);
                        int track;
                        if (data.trackIndex == TRACK_VIDEO) {
                            track = videoTrackIndex;
                        } else {
                            track = audioTrackIndex;
                        }
                        if (DEBUG) Log.e("ang-->", "写入混合数据 " + data.bufferInfo.size);
                        try {
                            mediaMuxer.writeSampleData(track, data.byteBuf, data.bufferInfo);
                        } catch (Exception e) {
//                            e.printStackTrace();
//                            if (DEBUG)
                            Log.e("angcyo-->", "写入混合数据失败!" + e.toString());
                            restartMediaMuxer();
                        }
                    }
                }
            } else {
                //混合器未开启
                synchronized (lock) {
                    try {
                        if (DEBUG) Log.e("angcyo-->", "混合等待开始...");
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        stopMediaMuxer();
//        readyStop();
        if (DEBUG) Log.e("angcyo-->", "混合器退出...");
    }

    private void requestStart() {
        synchronized (lock) {
            if (isMuxerStart()) {
                mediaMuxer.start();
                isMediaMuxerStart = true;
                if (DEBUG) Log.e("angcyo-->", "requestStart 启动混合器 开始等待数据输入...");
                lock.notify();
            }
        }
    }

    private boolean isMuxerStart() {
        return isAudioAdd && isVideoAdd;
    }

    private void restartAudioVideo() {
        if (audioThread != null) {
            audioTrackIndex = -1;
            isAudioAdd = false;
            audioThread.restart();
        }
        if (videoThread != null) {
            videoTrackIndex = -1;
            isVideoAdd = false;
            videoThread.restart();
        }
    }

    private void readyStop() {
        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
            } catch (Exception e) {
//                e.printStackTrace();
                Log.e("angcyo-->", "mediaMuxer.stop() 异常:" + e.toString());

            }
            try {
                mediaMuxer.release();
            } catch (Exception e) {
//                e.printStackTrace();
                Log.e("angcyo-->", "mediaMuxer.release() 异常:" + e.toString());

            }
            mediaMuxer = null;
        }

        if (mediaMuxer == null) {
            try {
                String tempFileName = fileSwapHelper.getTempFileName();
                mediaMuxer = new MediaMuxer(tempFileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                audioTrackIndex = mediaMuxer.addTrack(audioMediaFormat);
                videoTrackIndex = mediaMuxer.addTrack(videoMediaFormat);
                mediaMuxer.start();
                Log.e("angcyo-->", "临时保存-->" + tempFileName);
            } catch (IOException e) {
                Log.e("angcyo-->", "new MediaMuxer.release() 异常:" + e.toString());
                e.printStackTrace();
            }

        }
    }

    /**
     * 封装需要传输的数据类型
     */
    public static class MuxerData {
        int trackIndex;
        ByteBuffer byteBuf;
        MediaCodec.BufferInfo bufferInfo;

        public MuxerData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
        }
    }

}
