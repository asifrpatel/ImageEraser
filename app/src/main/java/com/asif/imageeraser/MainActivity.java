package com.asif.imageeraser;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutCompat;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Vector;

public class MainActivity extends Activity {

    private int initialDrawingCountLimit = 20;
    private int offset = 250;
    private int undoLimit = 10;
    private float brushSize = 70.0f;

    private boolean isMultipleTouchErasing;
    private boolean isTouchOnBitmap;
    private int initialDrawingCount;
    private int updatedBrushSize;
    private int imageViewWidth;

    private int imageViewHeight;
    private float currentx;
    private float currenty;

    private Bitmap bitmapMaster;
    private Bitmap lastEditedBitmap;
    private Bitmap originalBitmap;
    private Bitmap resizedBitmap;
    private Bitmap highResolutionOutput;

    private Canvas canvasMaster;
    private Point mainViewSize;
    private Path drawingPath;

    private Vector<Integer> brushSizes;
    private Vector<Integer> redoBrushSizes;

    private ArrayList<Path> paths;
    private ArrayList<Path> redoPaths;

    private RelativeLayout rlImageViewContainer;
    private LinearLayout llTopBar;
    private ImageView ivRedo;
    private ImageView ivUndo;
    private ImageView ivDone;
    private SeekBar sbOffset;
    private SeekBar sbWidth;
    private TouchImageView touchImageView;
    private BrushImageView brushImageView;

    private boolean isImageResized;
    private MediaScannerConnection msConn;
    private int MODE;

    public MainActivity() {
        paths = new ArrayList();
        redoPaths = new ArrayList();
        brushSizes = new Vector();
        redoBrushSizes = new Vector();
        MODE=0;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        drawingPath = new Path();
        Display display = getWindowManager().getDefaultDisplay();
        mainViewSize = new Point();
        display.getSize(mainViewSize);

        initViews();

        originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test);

        setBitMap();
        updateBrush((float) (mainViewSize.x / 2), (float) (mainViewSize.y / 2));

    }

    public void initViews() {
        touchImageView = (TouchImageView) findViewById(R.id.drawingImageView);
        brushImageView = (BrushImageView) findViewById(R.id.brushContainingView);
        llTopBar = (LinearLayout) findViewById(R.id.ll_top_bar);
        rlImageViewContainer = (RelativeLayout) findViewById(R.id.rl_image_view_container);
        ivUndo = (ImageView) findViewById(R.id.iv_undo);
        ivRedo = (ImageView) findViewById(R.id.iv_redo);
        ivDone = (ImageView) findViewById(R.id.iv_done);
        sbOffset = (SeekBar) findViewById(R.id.sb_offset);
        sbWidth = (SeekBar) findViewById(R.id.sb_width);


        rlImageViewContainer.getLayoutParams().height = mainViewSize.y
                - (llTopBar.getLayoutParams().height);
        imageViewWidth = mainViewSize.x;
        imageViewHeight = rlImageViewContainer.getLayoutParams().height;

        ivUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undo();
            }
        });

        ivRedo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                redo();
            }
        });

        ivDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveImage();
            }
        });

        touchImageView.setOnTouchListener(new OnTouchListner());
        sbWidth.setMax(150);
        sbWidth.setProgress((int) (brushSize - 20.0f));
        sbWidth.setOnSeekBarChangeListener(new OnWidthSeekbarChangeListner());
        sbOffset.setMax(350);
        sbOffset.setProgress(offset);
        sbOffset.setOnSeekBarChangeListener(new OnOffsetSeekbarChangeListner());

    }

    public void resetPathArrays() {
        ivUndo.setEnabled(false);
        ivRedo.setEnabled(false);
        paths.clear();
        brushSizes.clear();
        redoPaths.clear();
        redoBrushSizes.clear();
    }

    public void resetRedoPathArrays() {
        ivRedo.setEnabled(false);
        redoPaths.clear();
        redoBrushSizes.clear();
    }

    public void undo() {
        int size = this.paths.size();
        if (size != 0) {
            if (size == 1) {
                this.ivUndo.setEnabled(false);
            }
            size--;
            redoPaths.add(paths.remove(size));
            redoBrushSizes.add(brushSizes.remove(size));
            if (!ivRedo.isEnabled()) {
                ivRedo.setEnabled(true);
            }
            UpdateCanvas();
        }
    }

    public void redo() {
        int size = redoPaths.size();
        if (size != 0) {
            if (size == 1) {
                ivRedo.setEnabled(false);
            }
            size--;
            paths.add(redoPaths.remove(size));
            brushSizes.add(redoBrushSizes.remove(size));
            if (!ivUndo.isEnabled()) {
                ivUndo.setEnabled(true);
            }
            UpdateCanvas();
        }
    }

    public void setBitMap() {
        this.isImageResized = false;
        if (resizedBitmap != null) {
            resizedBitmap.recycle();
            resizedBitmap = null;
        }
        if (bitmapMaster != null) {
            bitmapMaster.recycle();
            bitmapMaster = null;
        }
        canvasMaster = null;
        resizedBitmap = resizeBitmapByCanvas();

        lastEditedBitmap = resizedBitmap.copy(Config.ARGB_8888, true);
        bitmapMaster = Bitmap.createBitmap(lastEditedBitmap.getWidth(), lastEditedBitmap.getHeight(), Config.ARGB_8888);
        canvasMaster = new Canvas(bitmapMaster);
        canvasMaster.drawBitmap(lastEditedBitmap, 0.0f, 0.0f, null);
        touchImageView.setImageBitmap(bitmapMaster);
        resetPathArrays();
        touchImageView.setPan(false);
        brushImageView.invalidate();
    }


    public Bitmap resizeBitmapByCanvas() {
        float width;
        float heigth;
        float orginalWidth = (float) originalBitmap.getWidth();
        float orginalHeight = (float) originalBitmap.getHeight();
        if (orginalWidth > orginalHeight) {
            width = (float) imageViewWidth;
            heigth = (((float) imageViewWidth) * orginalHeight) / orginalWidth;
        } else {
            heigth = (float) imageViewHeight;
            width = (((float) imageViewHeight) * orginalWidth) / orginalHeight;
        }
        if (width > orginalWidth || heigth > orginalHeight) {
            return originalBitmap;
        }
        Bitmap background = Bitmap.createBitmap((int) width, (int) heigth, Config.ARGB_8888);
        Canvas canvas = new Canvas(background);
        float scale = width / orginalWidth;
        float yTranslation = (heigth - (orginalHeight * scale)) / 2.0f;
        Matrix transformation = new Matrix();
        transformation.postTranslate(0.0f, yTranslation);
        transformation.preScale(scale, scale);
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(originalBitmap, transformation, paint);
        this.isImageResized = true;
        return background;
    }

    private void moveTopoint(float startx, float starty) {
        float zoomScale = getImageViewZoom();
        starty -= (float) offset;
        if (redoPaths.size() > 0) {
            resetRedoPathArrays();
        }
        PointF transLation = getImageViewTranslation();
        int projectedX = (int) ((float) (((double) (startx - transLation.x)) / ((double) zoomScale)));
        int projectedY = (int) ((float) (((double) (starty - transLation.y)) / ((double) zoomScale)));
        drawingPath.moveTo((float) projectedX, (float) projectedY);

        updatedBrushSize = (int) (brushSize / zoomScale);
    }

    private void lineTopoint(Bitmap bm, float startx, float starty) {
        if (initialDrawingCount < initialDrawingCountLimit) {
            initialDrawingCount += 1;
            if (initialDrawingCount == initialDrawingCountLimit) {
                isMultipleTouchErasing = true;
            }
        }
        float zoomScale = getImageViewZoom();
        starty -= (float) offset;
        PointF transLation = getImageViewTranslation();
        int projectedX = (int) ((float) (((double) (startx - transLation.x)) / ((double) zoomScale)));
        int projectedY = (int) ((float) (((double) (starty - transLation.y)) / ((double) zoomScale)));
        if (!isTouchOnBitmap && projectedX > 0 && projectedX < bm.getWidth() && projectedY > 0 && projectedY < bm.getHeight()) {
            isTouchOnBitmap = true;
        }
        drawingPath.lineTo((float) projectedX, (float) projectedY);
    }

    private void addDrawingPathToArrayList() {
        if (paths.size() >= undoLimit) {
            UpdateLastEiditedBitmapForUndoLimit();
            paths.remove(0);
            brushSizes.remove(0);
        }
        if (paths.size() == 0) {
            ivUndo.setEnabled(true);
            ivRedo.setEnabled(false);
        }
        brushSizes.add(updatedBrushSize);
        paths.add(drawingPath);
        drawingPath = new Path();
    }

    private void drawOnTouchMove() {
        Paint paint = new Paint();
        paint.setStrokeWidth((float) updatedBrushSize);
        paint.setColor(0);
        paint.setStyle(Style.STROKE);
        paint.setAntiAlias(true);
        paint.setStrokeJoin(Join.ROUND);
        paint.setStrokeCap(Cap.ROUND);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC));
        canvasMaster.drawPath(drawingPath, paint);
        touchImageView.invalidate();
    }

    public void UpdateLastEiditedBitmapForUndoLimit() {
        Canvas canvas = new Canvas(lastEditedBitmap);
        for (int i = 0; i < 1; i += 1) {
            int brushSize = brushSizes.get(i);
            Paint paint = new Paint();
            paint.setColor(0);
            paint.setStyle(Style.STROKE);
            paint.setAntiAlias(true);
            paint.setStrokeJoin(Join.ROUND);
            paint.setStrokeCap(Cap.ROUND);
            paint.setXfermode(new PorterDuffXfermode(Mode.SRC));
            paint.setStrokeWidth((float) brushSize);
            canvas.drawPath(paths.get(i), paint);
        }
    }

    public void UpdateCanvas() {
        canvasMaster.drawColor(0, Mode.CLEAR);
        canvasMaster.drawBitmap(lastEditedBitmap, 0.0f, 0.0f, null);
        int i = 0;
        while (true) {
            if (i >= paths.size()) {
                break;
            }
            int brushSize = brushSizes.get(i);
            Paint paint = new Paint();
            paint.setColor(0);
            paint.setStyle(Style.STROKE);
            paint.setAntiAlias(true);
            paint.setStrokeJoin(Join.ROUND);
            paint.setStrokeCap(Cap.ROUND);
            paint.setXfermode(new PorterDuffXfermode(Mode.SRC));
            paint.setStrokeWidth((float) brushSize);
            canvasMaster.drawPath(paths.get(i), paint);
            i += 1;
        }
        touchImageView.invalidate();
    }

    public void updateBrushWidth() {
        brushImageView.width = brushSize / 2.0f;
        brushImageView.invalidate();
    }

    public void updateBrushOffset() {
        float doffest = ((float) offset) - brushImageView.offset;
        BrushImageView brushImageViewView = brushImageView;
        brushImageViewView.centery += doffest;
        brushImageView.offset = (float) offset;
        brushImageView.invalidate();
    }

    public void updateBrush(float x, float y) {
        brushImageView.offset = (float) offset;
        brushImageView.centerx = x;
        brushImageView.centery = y;
        brushImageView.width = brushSize / 2.0f;
        brushImageView.invalidate();
    }

    public float getImageViewZoom() {
        return touchImageView.getCurrentZoom();
    }

    public PointF getImageViewTranslation() {
        return touchImageView.getTransForm();
    }

    protected void onStop() {
        super.onStop();
    }

    protected void onPause() {
        super.onPause();
    }

    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    protected void onDestroy() {
        super.onDestroy();
        UpdateCanvas();
        if (lastEditedBitmap != null) {
            lastEditedBitmap.recycle();
            lastEditedBitmap = null;
        }
        if (originalBitmap != null) {
            originalBitmap.recycle();
            originalBitmap = null;
        }
        if (resizedBitmap != null) {
            resizedBitmap.recycle();
            resizedBitmap = null;
        }
        if (bitmapMaster != null) {
            bitmapMaster.recycle();
            bitmapMaster = null;
        }
        if (this.highResolutionOutput != null) {
            this.highResolutionOutput.recycle();
            this.highResolutionOutput = null;
        }
    }

    private class OnTouchListner implements OnTouchListener {
        OnTouchListner() {
        }

        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (!(event.getPointerCount() == 1 || isMultipleTouchErasing)) {
                if (initialDrawingCount > 0) {
                    UpdateCanvas();
                    drawingPath.reset();
                    initialDrawingCount = 0;
                }
                touchImageView.onTouchEvent(event);
                MODE = 2;
            } else if (action == MotionEvent.ACTION_DOWN) {
                isTouchOnBitmap = false;
                touchImageView.onTouchEvent(event);
                MODE = 1;
                initialDrawingCount = 0;
                isMultipleTouchErasing = false;
                moveTopoint(event.getX(), event.getY());

                updateBrush(event.getX(), event.getY());
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (MODE == 1) {
                    currentx = event.getX();
                    currenty = event.getY();

                    updateBrush(currentx, currenty);
                    lineTopoint(bitmapMaster, currentx, currenty);

                    drawOnTouchMove();
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (MODE == 1) {
                    if (isTouchOnBitmap) {
                        addDrawingPathToArrayList();
                    }
                }
                isMultipleTouchErasing = false;
                initialDrawingCount = 0;
                MODE = 0;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                MODE = 0;
            }
            return true;
        }
    }

    private class OnWidthSeekbarChangeListner implements OnSeekBarChangeListener {
        OnWidthSeekbarChangeListner() {
        }

        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            brushSize = ((float) progress) + 20.0f;
            updateBrushWidth();
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private class OnOffsetSeekbarChangeListner implements OnSeekBarChangeListener {
        OnOffsetSeekbarChangeListner() {
        }

        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            offset = progress;
            updateBrushOffset();
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private void saveImage() {
        makeHighResolutionOutput();
        new imageSaveByAsync().execute(new String[0]);
    }

    private void makeHighResolutionOutput() {
        if (this.isImageResized) {
            Bitmap solidColor = Bitmap.createBitmap(this.originalBitmap.getWidth(), this.originalBitmap.getHeight(), this.originalBitmap.getConfig());
            Canvas canvas = new Canvas(solidColor);
            Paint paint = new Paint();
            paint.setColor(Color.argb(255, 255, 255, 255));
            Rect src = new Rect(0, 0, this.bitmapMaster.getWidth(), this.bitmapMaster.getHeight());
            Rect dest = new Rect(0, 0, this.originalBitmap.getWidth(), this.originalBitmap.getHeight());
            canvas.drawRect(dest, paint);
            paint.setXfermode(new PorterDuffXfermode(Mode.DST_OUT));
            canvas.drawBitmap(this.bitmapMaster, src, dest, paint);
            this.highResolutionOutput = null;
            this.highResolutionOutput = Bitmap.createBitmap(this.originalBitmap.getWidth(), this.originalBitmap.getHeight(), this.originalBitmap.getConfig());
            Canvas canvas1 = new Canvas(this.highResolutionOutput);
            canvas1.drawBitmap(this.originalBitmap, 0.0f, 0.0f, null);
            Paint paint1 = new Paint();
            paint1.setXfermode(new PorterDuffXfermode(Mode.DST_OUT));
            canvas1.drawBitmap(solidColor, 0.0f, 0.0f, paint1);
            if (solidColor != null && !solidColor.isRecycled()) {
                solidColor.recycle();
                solidColor = null;
            }
            return;
        }
        this.highResolutionOutput = null;
        this.highResolutionOutput = this.bitmapMaster.copy(this.bitmapMaster.getConfig(), true);
    }

    private class imageSaveByAsync extends AsyncTask<String, Void, Boolean> {
        private imageSaveByAsync() {
        }

        protected void onPreExecute() {
            getWindow().setFlags(16, 16);
        }

        protected Boolean doInBackground(String... args) {
            try {
                savePhoto(highResolutionOutput);
                return Boolean.valueOf(true);
            } catch (Exception e) {
                return Boolean.valueOf(false);
            }
        }

        protected void onPostExecute(Boolean success) {
            Toast toast = Toast.makeText(getBaseContext(), "PNG Saved", Toast.LENGTH_LONG);
            toast.setGravity(17, 0, 0);
            toast.show();
            getWindow().clearFlags(16);

        }
    }

    public void savePhoto(Bitmap bmp) {
        File imageFileName;
        FileOutputStream out;
        File imageFileFolder = new File(Environment.getExternalStorageDirectory(), "ImageEraser");
        imageFileFolder.mkdir();
        Calendar c = Calendar.getInstance();
        String date = String.valueOf(c.get(Calendar.MONTH))
                + String.valueOf(c.get(Calendar.DAY_OF_MONTH))
                + String.valueOf(c.get(Calendar.YEAR))
                + String.valueOf(c.get(Calendar.HOUR_OF_DAY))
                + String.valueOf(c.get(Calendar.MINUTE))
                + String.valueOf(c.get(Calendar.SECOND));
        FileOutputStream out2;


        imageFileName = new File(imageFileFolder, date.toString() + ".png");
        try {
            out2 = new FileOutputStream(imageFileName);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out2);
            out = out2;
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bmp != null && !bmp.isRecycled()) {
            bmp.recycle();
            bmp = null;
        }
        scanPhoto(imageFileName.toString());
    }

    public void scanPhoto(String imageFileName) {
        this.msConn = new MediaScannerConnection(this, new ScanPhotoConnection(imageFileName));
        this.msConn.connect();
    }

    class ScanPhotoConnection implements MediaScannerConnection.MediaScannerConnectionClient {
        final String val$imageFileName;

        ScanPhotoConnection(String str) {
            this.val$imageFileName = str;
        }

        public void onMediaScannerConnected() {
            msConn.scanFile(this.val$imageFileName, null);
        }

        public void onScanCompleted(String path, Uri uri) {
            msConn.disconnect();
        }
    }


}
