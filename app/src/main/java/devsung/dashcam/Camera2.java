package devsung.dashcam;

/**
 * Created by Peter on 15-03-29.
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2 extends Fragment implements View.OnClickListener {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "Camera2VideoFragment";
    static private float[] gravity = new float[]{0,0,0};
    static private float[] linear_acceleration = new float[]{0,0,0};

    public static boolean crashHappened = false;
    private static String fileNamern = "";


    public void accelorometerEvent(SensorEvent sensorEvent){
        final float alpha = 0.8f;

        //Isolate force of gravity with low-pass filter
        gravity[0] = alpha * gravity[0] + (1 - alpha) * sensorEvent.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * sensorEvent.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * sensorEvent.values[2];

        //Remove the gravity contribution with high-pass filter
        linear_acceleration[0] = sensorEvent.values[0] - gravity[0];
        linear_acceleration[1] = sensorEvent.values[1] - gravity[1];
        linear_acceleration[2] = sensorEvent.values[2] - gravity[2];

        linear_acceleration[0] = (float) Math.sqrt(linear_acceleration[0] * linear_acceleration[0] +
                linear_acceleration[1] * linear_acceleration[1] +
                linear_acceleration[2] * linear_acceleration[2]);
        if (Math.abs(linear_acceleration[0]) > 30){
            crashHappened = true;
        }
    }


    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //Camera Preview
    private AutoFixTextureView mTextureView;

    //Button to record video
    private Button mButtonVideo;

    //Reference to opened camera device
    private CameraDevice mCameraDevice;

    //Reference to capture session
    private CameraCaptureSession mPreviewSession;


    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    //Size of camera preview
    private Size mPreviewSize;

    //Size of video recording
    private Size mVideoSize;

    //Camera Preview
    private CaptureRequest.Builder mPreviewBuilder;

    //Media Recorder
    private MediaRecorder mMediaRecorder;

    //Boolean whether app is recording video right now
    private boolean mIsRecordingVideo;

    //Additional thread to run several tasks at once
    private HandlerThread mBackgroundThread;

    //A handler for running tasks in background
    private Handler mBackgroundHandler;

    //Prevents app from exiting before closing the camera
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);


    //Callback called when camera device changes its state
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (mTextureView != null) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    };

    public static Camera2 newInstance() {
        Camera2 fragment = new Camera2();
        fragment.setRetainInstance(true);
        return fragment;
    }

    //Video size of 3*4 ratio. 1080px video as well
    //Array of possible sizes
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Could not find a suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.activity_main, container,false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState){
        mTextureView = (AutoFixTextureView) view.findViewById(R.id.texture);
        mButtonVideo = (Button) view.findViewById(R.id.video);
        mButtonVideo.setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
    }

    @Override
    public void onResume(){
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()){
            openCamera(mTextureView.getWidth(),mTextureView.getHeight());
        }else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause(){
        Activity activity = getActivity();
        editVideoFile(activity);
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick (View view){
        switch (view.getId()){
            case R.id.video:{
                if (mIsRecordingVideo){
                    stopRecordingVideo();
                } else{
                    startRecordingVideo();
                }
                break;
            }
            case R.id.info:{
                Activity activity = getActivity();
                if (activity !=null){
                    new AlertDialog.Builder(activity).setMessage("Dash-Cam").setPositiveButton(android.R.string.ok,null).show();
                }
                break;
            }
        }
    }


    //Start Background Thread
    private void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    //Stop Background Thread
    private void stopBackgroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException ex){
            ex.printStackTrace();
        }
    }

    //Opens camera device
    private void openCamera(int width, int height){
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()){
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500,TimeUnit.MILLISECONDS)){
                throw new RuntimeException("Time out waiting to lock camera");
            }
            String cameraId = manager.getCameraIdList()[0];

            //Choose size for camera
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),width,height,mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE){
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
            }
            configureTransform(width,height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);

        } catch (CameraAccessException ex){
            Toast.makeText (activity, "Cannot access the camera", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException ex){
            new ErrorDialog().show(getFragmentManager(), "dialog");
        } catch (InterruptedException ex){
            throw new RuntimeException("Interrupted while trying to lock camera opening");
        }
    }

    //Function for closing camera
    private void closeCamera(){
        try {
            mCameraOpenCloseLock.acquire();
            if (mCameraDevice != null){
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mMediaRecorder != null){
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException ex){
            throw new RuntimeException("Interrupted while locking camera");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (mCameraDevice == null || !mTextureView.isAvailable() || mPreviewSize == null) {
            return;
        }
        try {
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Update Camera Preview
    private void updatePreview(){
        if (mCameraDevice == null){
            return;
        }
        try{
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException ex){
            ex.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder){
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    //Configures graphics.matrix transformation
    private void configureTransform(int viewWidth, int viewHeight){
        Activity activity = getActivity();
        if (mTextureView == null || mPreviewSize == null || activity == null){
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0,0,viewWidth,viewHeight);
        RectF bufferRect = new RectF(0,0,mPreviewSize.getHeight(),mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270){
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect,bufferRect,Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight/mPreviewSize.getHeight(),
                    (float) viewWidth/mPreviewSize.getWidth());
            matrix.postScale(scale,scale,centerX,centerY);
            matrix.postRotate(90 * (rotation-2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpMediaRecorder() throws IOException{
        final Activity activity = getActivity();
        if (activity == null){
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(getVideoFile(activity).getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    private File getVideoFile (Context context){
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date();

        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), dateFormat.format(date) + "DashCam.mp4");

        fileNamern = "/storage/emulated/0/Pictures/" + file.getName();


        return file;
        //return new File(context.getExternalFilesDir(null), "video.mp4");
    }

    private File editVideoFile (Context context){

        File file = new File(fileNamern);




        if (crashHappened==false){
            file.delete();
        }else{
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(Uri.fromFile(file));
            context.sendBroadcast(intent);
            crashHappened = false;
            Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show();
        }

        return file;
        //return new File(context.getExternalFilesDir(null), "video.mp4");
    }

    private void startRecordingVideo(){
        try{
            crashHappened = false;
            mButtonVideo.setText("Stop");
            mIsRecordingVideo = true;

            mMediaRecorder.start();

            new CountDownTimer(300000, 1000) {
                public void onTick(long millisUntilFinished) {
                }

                public void onFinish() {
                    if (mIsRecordingVideo) {
                        stopRecordingVideoTemp();
                    }
                }
            }.start();
        }catch (IllegalStateException ex){
            ex.printStackTrace();
        }
    }

    private void stopRecordingVideo(){
        mIsRecordingVideo = false;
        mButtonVideo.setText("Record");

        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Activity activity = getActivity();

        if (activity != null){
            editVideoFile(activity);
        }

        startPreview();
    }
    private void stopRecordingVideoTemp(){

        mMediaRecorder.stop();
        mMediaRecorder.reset();

        Activity activity = getActivity();

        if (activity != null){
            editVideoFile(activity);
        }

        startPreview();

        startRecordingVideo();
    }


    //Compares two sizes based on areas
    static class CompareSizesByArea implements Comparator<Size>{
        @Override
        public int compare(Size lhs, Size rhs){
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getHeight() * rhs.getHeight());
        }
    }


    public static class ErrorDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage("This device doesn't support the camera API")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }
}


