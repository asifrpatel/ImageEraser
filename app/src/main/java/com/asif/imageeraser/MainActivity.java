package com.asif.imageeraser;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
import android.os.Bundle;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutCompat;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.util.ArrayList;
import java.util.Vector;

public class MainActivity extends Activity {

    private int initialDrawingCountLimit=20;
    private int offset=250;
    private int undoLimit=10;
    private float brushSize=70.0f;

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

    private Canvas canvasMaster;
    private Point mainViewSize;
    private Path drawingPath;

    private Vector<Integer> brushSizes;
    private Vector<Integer> redoBrushSizes;

    private ArrayList<Path> paths;
    private ArrayList<Path> redoPaths;

    private RelativeLayout rlImageViewContainer;
    private LinearLayoutCompat llBottomBar;
    private LinearLayoutCompat llTopBar;
    private AppCompatTextView tvRedo;
    private AppCompatTextView tvUndo;
    private AppCompatSeekBar sbOffset;
    private AppCompatSeekBar sbWidth;
    private TouchImageView touchImageView;
    private BrushImageView brushImageView;


    public MainActivity() {
        paths = new ArrayList();
        redoPaths = new ArrayList();
        brushSizes = new Vector();
        redoBrushSizes = new Vector();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_eraser);
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
        llTopBar = (LinearLayoutCompat) findViewById(R.id.ll_top_bar);
        llBottomBar = (LinearLayoutCompat) findViewById(R.id.ll_bottom_bar);
        rlImageViewContainer = (RelativeLayout) findViewById(R.id.rl_image_view_container);
        tvUndo = (AppCompatTextView) findViewById(R.id.tv_undo);
        tvRedo = (AppCompatTextView) findViewById(R.id.tv_redo);
        sbOffset = (AppCompatSeekBar) findViewById(R.id.sb_offset);
        sbWidth = (AppCompatSeekBar) findViewById(R.id.sb_width);


        rlImageViewContainer.getLayoutParams().height = mainViewSize.y
                - (((llTopBar.getLayoutParams().height
                + llBottomBar.getLayoutParams().height)));
        imageViewWidth = mainViewSize.x;
        imageViewHeight = rlImageViewContainer.getLayoutParams().height;

        tvUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undo();
            }
        });

        tvRedo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View Button) {
                redo();
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
        tvUndo.setEnabled(false);
        tvRedo.setEnabled(false);
        paths.clear();
        brushSizes.clear();
        redoPaths.clear();
        redoBrushSizes.clear();
    }

    public void resetRedoPathArrays() {
        tvRedo.setEnabled(false);
        redoPaths.clear();
        redoBrushSizes.clear();
    }

    public void undo() {
        int size = this.paths.size();
        if (size != 0) {
            if (size == 1) {
                this.tvUndo.setEnabled(false);
            }
            size--;
            redoPaths.add(paths.remove(size));
            redoBrushSizes.add(brushSizes.remove(size));
            if (!tvRedo.isEnabled()) {
                tvRedo.setEnabled(true);
            }
            UpdateCanvas();
        }
    }

    public void redo() {
        int size = redoPaths.size();
        if (size != 0) {
            if (size == 1) {
                tvRedo.setEnabled(false);
            }
            size--;
            paths.add(redoPaths.remove(size));
            brushSizes.add(redoBrushSizes.remove(size));
            if (!tvUndo.isEnabled()) {
                tvUndo.setEnabled(true);
            }
            UpdateCanvas();
        }
    }

    public void setBitMap() {
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
            tvUndo.setEnabled(true);
            tvRedo.setEnabled(false);
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
            } else if (action == MotionEvent.ACTION_DOWN) {
                isTouchOnBitmap = false;
                touchImageView.onTouchEvent(event);
                initialDrawingCount = 0;
                isMultipleTouchErasing = false;
                moveTopoint(event.getX(), event.getY());

                updateBrush(event.getX(), event.getY());
            } else if (action == MotionEvent.ACTION_MOVE) {
                currentx = event.getX();
                currenty = event.getY();

                updateBrush(currentx, currenty);
                lineTopoint(bitmapMaster, currentx, currenty);

                drawOnTouchMove();

            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {

                if (isTouchOnBitmap) {
                    addDrawingPathToArrayList();
                }
                isMultipleTouchErasing = false;
                initialDrawingCount = 0;
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
}
