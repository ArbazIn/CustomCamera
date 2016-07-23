package com.techno.mycam;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.hardware.Camera.Parameters;


/**
 * Created by arbaz on 23/7/16.
 */
public class MyCamActivity extends Activity implements Camera.PictureCallback, SurfaceHolder.Callback {

    public static final String EXTRA_CAMERA_DATA = "camera_data";
    private static final String KEY_IS_CAPTURING = "is_capturing";

    private ImageView mCameraImage;
    private SurfaceView mCameraPreview;
    private Button mCaptureImageButton, mSaveImageButton, mFrontImageButton, mFlashImageButton;
    private byte[] mCameraData;
    private Camera mCamera;
    private byte[] saveData;
    private Camera saveCamera;
    private boolean mIsCapturing;
    private boolean isFlashOn;
    private boolean hasFlash;
    Parameters params;
    int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    int cameraIdFront = Camera.CameraInfo.CAMERA_FACING_FRONT;
    int count = 0;
    GestureDetector gestureDetector;
    MediaPlayer mp;
    Bitmap saveBitmap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_mycam);

        mCameraImage = (ImageView) findViewById(R.id.camera_image_view);
        mCameraImage.setVisibility(View.INVISIBLE);

        mCameraPreview = (SurfaceView) findViewById(R.id.preview_view);
        mCameraPreview.setOnTouchListener(new OnSwipeTouchListener(this));

        mp = MediaPlayer.create(this, R.raw.capture_sound);

        final SurfaceHolder surfaceHolder = mCameraPreview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


        mCaptureImageButton = (Button) findViewById(R.id.capture_image_button);
        mSaveImageButton = (Button) findViewById(R.id.save_image_button);
        mFrontImageButton = (Button) findViewById(R.id.front_image_button);
        mFlashImageButton = (Button) findViewById(R.id.flash_image_button);
        mSaveImageButton.setEnabled(false);

        mCaptureImageButton.setOnClickListener(mCaptureImageButtonClickListener);
        mSaveImageButton.setOnClickListener(mSaveImageButtonClickListener);
        mFrontImageButton.setOnClickListener(mFrontImageButtonClickListener);
        mFlashImageButton.setOnClickListener(mFlashImageButtonClickListener);

        //First check if device is supporting flashlight or not
        hasFlash = getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);

        if (!hasFlash) {
            // device doesn't support flash
            Toast.makeText(getApplicationContext(), "Flash Light Not Support", Toast.LENGTH_LONG).show();
            mFlashImageButton.setEnabled(false);
        } else {
            //getCamera();
        }


        mCameraPreview.setOnTouchListener(new OnSwipeTouchListener(this) {
            public void onSwipeTop() {
                // Toast.makeText(MyCamActivity.this, "top", Toast.LENGTH_SHORT).show();
                frontCam();
            }

            public void onSwipeRight() {
                //Toast.makeText(MyCamActivity.this, "right", Toast.LENGTH_SHORT).show();
                turnOffFlash();
            }

            public void onSwipeLeft() {
                //Toast.makeText(MyCamActivity.this, "left", Toast.LENGTH_SHORT).show();
                turnOnFlash();

            }

            public void onSwipeBottom() {
                //Toast.makeText(MyCamActivity.this, "bottom", Toast.LENGTH_SHORT).show();
                frontToBackCam();
            }
        });
        //  setContentView(mCameraPreview);

        mIsCapturing = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
                if (mIsCapturing) {
                    mCamera.startPreview();
                }
            } catch (IOException e) {
                Toast.makeText(MyCamActivity.this, "Unable to start camera preview.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(KEY_IS_CAPTURING, mIsCapturing);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIsCapturing = savedInstanceState.getBoolean(KEY_IS_CAPTURING, mCameraData == null);
        if (mCameraData != null) {
            setupImageDisplay();
        } else {
            setupImageCapture();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCamera == null) {
            try {
                mCamera = Camera.open();
                mCamera.setPreviewDisplay(mCameraPreview.getHolder());
                params = mCamera.getParameters();
                if (mIsCapturing) {
                    mCamera.startPreview();
                    mSaveImageButton.setEnabled(false);

                }
            } catch (Exception e) {
                Toast.makeText(MyCamActivity.this, "Unable to open camera.", Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }


    private void captureImage() {
        mCamera.takePicture(null, null, this);
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        mCameraData = data;
        saveData = data;
        saveCamera = camera;
        setupImageDisplay();
    }

    private void setupImageCapture() {
        mCameraImage.setVisibility(View.INVISIBLE);
        mCameraPreview.setVisibility(View.VISIBLE);
        mCamera.startPreview();
        mCaptureImageButton.setText(R.string.capture_image);
        mCaptureImageButton.setOnClickListener(mCaptureImageButtonClickListener);

    }

    private void setupImageDisplay() {
        Bitmap bitmap = BitmapFactory.decodeByteArray(mCameraData, 0, mCameraData.length);
        mCameraImage.setImageBitmap(bitmap);
        mCamera.stopPreview();
        mCameraPreview.setVisibility(View.INVISIBLE);
        mCameraImage.setVisibility(View.VISIBLE);
        mCaptureImageButton.setText(R.string.recapture_image);
        mCaptureImageButton.setOnClickListener(mRecaptureImageButtonClickListener);
    }

    public void frontCam() {

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        mCamera = Camera.open(cameraIdFront);

        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(mCameraPreview.getHolder());
                mCamera.startPreview();
                mFrontImageButton.setText(R.string.back_cam);
                mFrontImageButton.setOnClickListener(mFrontToBackImageButtonClickListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void frontToBackCam() {

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        mCamera = Camera.open(cameraId);

        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(mCameraPreview.getHolder());
                mCamera.startPreview();
                mFrontImageButton.setText(R.string.front_cam);
                mFrontImageButton.setOnClickListener(mFrontImageButtonClickListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void saveImage(byte[] data, Camera camera) {

        File imageFile;
        try {
            // convert byte array into bitmap
            saveBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            String state = Environment.getExternalStorageState();
            File folder = null;
            if (state.contains(Environment.MEDIA_MOUNTED)) {
                folder = new File(Environment
                        .getExternalStorageDirectory() + "/MyCam");
            } else {
                folder = new File(Environment
                        .getExternalStorageDirectory() + "/MyCam");
            }

            boolean success = true;
            if (!folder.exists()) {
                success = folder.mkdirs();
            }
            if (success) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_mm_dd_hh_mm",
                        Locale.getDefault());
                imageFile = new File(folder.getAbsolutePath()
                        + File.separator
                        + dateFormat.format(new Date())
                        + "MyCamImg.jpg");
                imageFile.createNewFile();
            } else {
                Toast.makeText(getBaseContext(), "Image Not saved",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream ostream = new ByteArrayOutputStream();

            // save image into gallery
            saveBitmap.compress(Bitmap.CompressFormat.JPEG, 100, ostream);

            FileOutputStream fout = new FileOutputStream(imageFile);
            fout.write(ostream.toByteArray());
            fout.close();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATE_TAKEN,
                    System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.DATA,
                    imageFile.getAbsolutePath());

            MyCamActivity.this.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //Turning On flash
    private void turnOnFlash() {
        if (!isFlashOn) {
            if (mCamera == null || params == null) {
                return;
            }
            params = mCamera.getParameters();
            params.setFlashMode(Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(params);
            mCamera.startPreview();
            isFlashOn = true;
            // changing button/switch image
            mFlashImageButton.setText(R.string.flash_off);
            mFlashImageButton.setOnClickListener(mFlashOffButtonClickListener);
        }

    }

    //Turning Off flash
    private void turnOffFlash() {
        if (isFlashOn) {
            if (mCamera == null || params == null) {
                return;
            }

            params = mCamera.getParameters();
            params.setFlashMode(Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(params);

            isFlashOn = false;

            // changing button/switch image
            mFlashImageButton.setText(R.string.flash_on);
            mFlashImageButton.setOnClickListener(mFlashImageButtonClickListener);
        }
    }


    public class OnSwipeTouchListener implements View.OnTouchListener {

        private final GestureDetector gestureDetector;

        public OnSwipeTouchListener(Context ctx) {
            gestureDetector = new GestureDetector(ctx, new GestureListener());
        }

        public boolean onTouch(final View view, final MotionEvent motionEvent) {
            return gestureDetector.onTouchEvent(motionEvent);
        }

        private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                boolean result = false;
                try {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                onSwipeRight();
                            } else {
                                onSwipeLeft();
                            }
                        }
                    } else {
                        if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffY > 0) {
                                onSwipeBottom();
                            } else {
                                onSwipeTop();
                            }
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                return result;
            }
        }

        public void onSwipeRight() {
        }

        public void onSwipeLeft() {
        }

        public void onSwipeTop() {
        }

        public void onSwipeBottom() {
        }

    }


    private View.OnClickListener mCaptureImageButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            captureImage();
            mp.start();
            mSaveImageButton.setEnabled(true);

        }
    };

    private View.OnClickListener mRecaptureImageButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            setupImageCapture();
            saveImage(saveData, saveCamera);
            mSaveImageButton.setEnabled(true);


        }
    };
    private View.OnClickListener mSaveImageButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            Toast.makeText(getApplicationContext(), "Photo Saving", Toast.LENGTH_LONG).show();
            saveImage(saveData, saveCamera);
        }
    };

    private View.OnClickListener mFrontImageButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Toast.makeText(getApplicationContext(), "Front", Toast.LENGTH_LONG).show();
            frontCam();
            turnOffFlash();
        }
    };
    private View.OnClickListener mFlashImageButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            turnOnFlash();
        }
    };
    private View.OnClickListener mFlashOffButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            turnOffFlash();
        }
    };
    private View.OnClickListener mFrontToBackImageButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            frontToBackCam();
        }
    };

}
