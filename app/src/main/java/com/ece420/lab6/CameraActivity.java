package com.ece420.lab6;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
// import android.util.Log;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.lang.Math;
import java.util.function.LongToDoubleFunction;
// import java.util.List;


public class CameraActivity extends AppCompatActivity implements SurfaceHolder.Callback{

    // UI Variable
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView2;
    private SurfaceHolder surfaceHolder2;
    private TextView textHelper;
    // Camera Variable
    private Camera camera;
    boolean previewing = false;
    private int width = 640;
    private int height = 480;
    // Kernels
    private double[][] kernelS = new double[][] {{-1,-1,-1},{-1,9,-1},{-1,-1,-1}};
    private double[][] kernelX = new double[][] {{1,0,-1},{1,0,-1},{1,0,-1}};
    private double[][] kernelY = new double[][] {{1,1,1},{0,0,0},{-1,-1,-1}};

    private boolean runFlag = false;

    private Button captureButton;
    int retData[] = new int[width * height];

    int first_col_idx=0, second_col_idx=0, first_row_idx=0, second_row_idx=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Modify UI Text
        textHelper = (TextView) findViewById(R.id.Helper);
        if(MainActivity.appFlag == 1) textHelper.setText("Histogram Equalized Image");
        else if(MainActivity.appFlag == 2) textHelper.setText("Sharpened Image");
        else if(MainActivity.appFlag == 3) textHelper.setText("Edge Detected Image");

        // Setup Surface View handler
        surfaceView = (SurfaceView)findViewById(R.id.ViewOrigin);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView2 = (SurfaceView)findViewById(R.id.ViewHisteq);
        surfaceHolder2 = surfaceView2.getHolder();

        captureButton = (Button) findViewById(R.id.run);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runFlag = true;
            }
        });
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Must have to override native method
        return;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(!previewing) {
            camera = Camera.open();
            if (camera != null) {
                try {
                    // Modify Camera Settings
                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setPreviewSize(width, height);
                    // Following lines could log possible camera resolutions, including
                    // 2592x1944;1920x1080;1440x1080;1280x720;640x480;352x288;320x240;176x144;
                    // List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
                    // for(int i=0; i<sizes.size(); i++) {
                    //     int height = sizes.get(i).height;
                    //     int width = sizes.get(i).width;
                    //     Log.d("size: ", Integer.toString(width) + ";" + Integer.toString(height));
                    // }
                    camera.setParameters(parameters);
                    camera.setDisplayOrientation(90);
                    camera.setPreviewDisplay(surfaceHolder);
                    camera.setPreviewCallback(new PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera)
                        {
                            // Lock canvas
                            Canvas canvas = surfaceHolder2.lockCanvas(null);
                            // Where Callback Happens, camera preview frame ready
                            onCameraFrame(canvas,data);
                            // Unlock canvas
                            surfaceHolder2.unlockCanvasAndPost(canvas);
                        }
                    });
                    camera.startPreview();
                    previewing = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Cleaning Up
        if (camera != null && previewing) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
            previewing = false;
        }
    }

    // Camera Preview Frame Callback Function
    protected void onCameraFrame(Canvas canvas, byte[] data) {

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
//        int retData[] = new int[width * height]; This needs to be a global variable only update if button is pressed

        // Apply different processing methods
        if (MainActivity.appFlag == 3 && runFlag == true) {
            byte[] small_data = downSample(data);
            int[] xData = conv2(small_data, width / 4, height / 4, kernelX);
            int[] yData = conv2(small_data, width / 4, height / 4, kernelY);
            retData = merge(xData, yData); //down sampled
            //retData = yuv2rgb(data); //original
            int[] retData_gray = merge_gray(xData, yData);
            int[][] digital_grid = Tic_Tac_Toe_R(retData_gray, width / 4, height / 4);

            retData = classification(retData_gray, width/4, height/4, digital_grid);
            int size_t=height*width/16;
            for (int i=0; i<size_t; i++){
                int p = retData[i];
                retData[i] = 0xff000000 | p<<16 | p<<8 | p;
            }

        }
        // Create ARGB Image, rotate and draw (this is for downsampled edge detection image
        Bitmap bmp = Bitmap.createBitmap(retData, width/4, height/4, Bitmap.Config.ARGB_8888);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        canvas.drawBitmap(bmp, new Rect(0,0, height/4, width/4), new Rect(0,0, canvas.getWidth(), canvas.getHeight()),null);
        runFlag = false;
//        // Create ARGB Image, rotate and draw (this is for downsampled edge detection image (original image)
//        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
//        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
//        canvas.drawBitmap(bmp, new Rect(0,0, height, width), new Rect(0,0, canvas.getWidth(), canvas.getHeight()),null);
//        runFlag = false;


    }

    public int[] classification(int[] retData, int width, int height, int[][] digital_grid) {
        if (digital_grid[0][0] == 1 && digital_grid[0][1] == 1 && digital_grid[0][2] == 1) {
            int i = (int) (first_row_idx / 2);
            for (int j = 0; j < width; j++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[1][0] == 1 && digital_grid[1][1] == 1 && digital_grid[1][2] == 1) {
            int i = (int) (first_row_idx + (int) ((second_row_idx - first_row_idx) / 2));
            for (int j = 0; j < width; j++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[2][0] == 1 && digital_grid[2][1] == 1 && digital_grid[2][2] == 1) {
            int i = (int) (second_row_idx + (int) ((height - second_row_idx) / 2));
            for (int j = 0; j < width; j++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[0][0] == 1 && digital_grid[1][0] == 1 && digital_grid[2][0] == 1) {
            int j = (int) (first_col_idx / 2);
            for (int i = 0; i < height; i++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[0][1] == 1 && digital_grid[1][1] == 1 && digital_grid[2][1] == 1) {
            int j = (int) (first_col_idx + (int) ((second_col_idx - first_col_idx) / 2));
            for (int i = 0; i < height; i++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[0][2] == 1 && digital_grid[1][2] == 1 && digital_grid[2][2] == 1) {
            int j = (int) (second_col_idx + (int) ((width - second_col_idx) / 2));
            for (int i = 0; i < height; i++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[0][0] == 1 && digital_grid[1][1] == 1 && digital_grid[2][2] == 1) {
            int i = 0, j = 0, l = 0;
            for (int m = 0; m < height-1; m++) {
                retData[i * width + j] = 255;
                i++;
                j++;
                l++;
                if (l == 3) {
                    l = 0;
                    j++;
                    retData[i * width + j] += 255;
                }
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[0][2] == 1 && digital_grid[1][1] == 1 && digital_grid[2][0] == 1) {
            int i = 0, j = width, l = 0;
            for (int m = 0; m < height-1; m++) {
                retData[i * width + j] = 255;
                i++;
                j--;
                l++;
                if (l == 3) {
                    l = 0;
                    j--;
                    retData[i * width + j] = 255;
                }
            }
            textHelper.setText("Edge Detected Image   Crosses Win!");
        } else if (digital_grid[0][0] == 2 && digital_grid[0][1] == 2 && digital_grid[0][2] == 2) {
            int i = (int) (first_row_idx / 2);
            for (int j = 0; j < width; j++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[1][0] == 2 && digital_grid[1][1] == 2 && digital_grid[1][2] == 2) {
            int i = (int) (first_row_idx + (int) ((second_row_idx - first_row_idx) / 2));
            for (int j = 0; j < width; j++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[2][0] == 2 && digital_grid[2][1] == 2 && digital_grid[2][2] == 2) {
            int i = (int) (second_row_idx + (int) ((height - second_row_idx) / 2));
            for (int j = 0; j < width; j++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[0][0] == 2 && digital_grid[1][0] == 2 && digital_grid[2][0] == 2) {
            int j = (int) (first_col_idx / 2);
            for (int i = 0; i < height; i++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[0][1] == 2 && digital_grid[1][1] == 2 && digital_grid[2][1] == 2) {
            int j = (int) (first_col_idx + (int) ((second_col_idx - first_col_idx) / 2));
            for (int i = 0; i < height; i++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[0][2] == 2 && digital_grid[1][2] == 2 && digital_grid[2][2] == 2) {
            int j = (int) (second_col_idx + (int) ((width - second_col_idx) / 2));
            for (int i = 0; i < height; i++) {
                retData[i * width + j] = 255;
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[0][0] == 2 && digital_grid[1][1] == 2 && digital_grid[2][2] == 2) {
            int i = 0, j = 0, l = 0;
            for (int m = 0; m < height-1; m++) {
                retData[i * width + j] = 255;
                i++;
                j++;
                l++;
                if (l == 3) {
                    l = 0;
                    j++;
                    retData[i * width + j] += 255;
                }
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else if (digital_grid[0][2] == 2 && digital_grid[1][1] == 2 && digital_grid[2][0] == 2) {
            int i = 0, j = width, l = 0;
            for (int m = 0; m < height-1; m++) {
                retData[i * width + j] = 255;
                i++;
                j--;
                l++;
                if (l == 3) {
                    l = 0;
                    j--;
                    retData[i * width + j] = 255;
                }
            }
            textHelper.setText("Edge Detected Image   Circles Win!");
        } else {
            textHelper.setText("Edge Detected Image   Nobody win!");
        }
        return retData;
    }

    public byte[] downSample(byte[] data) {
        int small_size = height * width / 16;
        byte[] toReturn = new byte[small_size];
        for (int i = 0; i < height/4; i++) {
            for (int j = 0; j < width/4; j++) {
                toReturn[i*(width/4)+j]=data[i*4*width+j*4];
            }
        }
        return toReturn;
    }
    public int[][] Tic_Tac_Toe_R(int[] retData, int width, int height){
        int[] column_sum=new int[width];
        int[] row_sum=new int[height];

        for (int i=0; i<height; i++){
            for (int j=0; j<width; j++){
                column_sum[j]+=retData[i*width+j];
                row_sum[i]+=retData[i*width+j];
            }
        }
        int half_width=width/2;
        int half_height=height/2;
//        int first_col_idx=0, second_col_idx=0, first_row_idx=0, second_row_idx=0;
        int max=0;
        for(int i = 5; i<half_width; i++){
            if(column_sum[i-1]+column_sum[i]+column_sum[i+1]>max){
                max=column_sum[i-1]+column_sum[i]+column_sum[i+1];
                first_col_idx=i;
            }
        }
        max=0;
        for(int i = half_width; i<width-5; i++){
            if(column_sum[i-1]+column_sum[i]+column_sum[i+1]>max){
                max=column_sum[i-1]+column_sum[i]+column_sum[i+1];
                second_col_idx=i;
            }
        }
        max=0;
        for(int i = 5; i<half_height; i++){
            if(row_sum[i-1]+row_sum[i]+row_sum[i+1]>max){
                max=row_sum[i-1]+row_sum[i]+row_sum[i+1];
                first_row_idx=i;
            }
        }
        max=0;
        for(int i = half_height; i<height-5; i++){
            if(row_sum[i-1]+row_sum[i]+row_sum[i+1]>max){
                max=row_sum[i-1]+row_sum[i]+row_sum[i+1];
                second_row_idx=i;
            }
        }
        int[] grid_0_0 = new int[first_row_idx*first_col_idx];
        int[] grid_0_1 = new int[first_row_idx*(second_col_idx-first_col_idx)];
        int[] grid_0_2 = new int[first_row_idx*(width-second_col_idx)];

        int[] grid_1_0 = new int[(second_row_idx-first_row_idx)*first_col_idx];
        int[] grid_1_1 = new int[(second_row_idx-first_row_idx)*(second_col_idx-first_col_idx)];
        int[] grid_1_2 = new int[(second_row_idx-first_row_idx)*(width-second_col_idx)];

        int[] grid_2_0 = new int[(height-second_row_idx)*first_col_idx];
        int[] grid_2_1 = new int[(height-second_row_idx)*(second_col_idx-first_col_idx)];
        int[] grid_2_2 = new int[(height-second_row_idx)*(width-second_col_idx)];

        int k=0;
        for(int i=0; i<first_row_idx; i++){
            for (int j=0; j<first_col_idx; j++){
                grid_0_0[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=0; i<first_row_idx; i++){
            for (int j=first_col_idx; j<second_col_idx; j++){
                grid_0_1[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=0; i<first_row_idx; i++){
            for (int j=second_col_idx; j<width; j++){
                grid_0_2[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=first_row_idx; i<second_row_idx; i++){
            for (int j=0; j<first_col_idx; j++){
                grid_1_0[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=first_row_idx; i<second_row_idx; i++){
            for (int j=first_col_idx; j<second_col_idx; j++){
                grid_1_1[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=first_row_idx; i<second_row_idx; i++){
            for (int j=second_col_idx; j<width; j++){
                grid_1_2[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=second_row_idx; i<height; i++){
            for (int j=0; j<first_col_idx; j++){
                grid_2_0[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=second_row_idx; i<height; i++){
            for (int j=first_col_idx; j<second_col_idx; j++){
                grid_2_1[k]=retData[i*width+j];
                k++;
            }
        }
        k=0;
        for(int i=second_row_idx; i<height; i++){
            for (int j=second_col_idx; j<width; j++){
                grid_2_2[k]=retData[i*width+j];
                k++;
            }
        }

        Log.d("OUTPUT", "\nfirst_col_idx = " + Integer.toString(first_col_idx) + "\nsecond_col_idx = " + Integer.toString(second_col_idx));
        Log.d("OUTPUT", "\nfirst_row_idx = " + Integer.toString(first_row_idx) + "\nsecond_row_idx = " + Integer.toString(second_row_idx));


        int[][] digital_grid = new int[3][3];
        Log.d("OUTPUT", "-------------------------------grid_0_0");
        digital_grid[0][0] = Detection(grid_0_0,first_col_idx,first_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_0_1");
        digital_grid[0][1] = Detection(grid_0_1,second_col_idx-first_col_idx,first_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_0_2");
        digital_grid[0][2] = Detection(grid_0_2,width-second_col_idx,first_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_1_0");
        digital_grid[1][0] = Detection(grid_1_0,first_col_idx,second_row_idx-first_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_1_1");
        digital_grid[1][1] = Detection(grid_1_1,second_col_idx-first_col_idx,second_row_idx-first_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_1_2");
        digital_grid[1][2] = Detection(grid_1_2,width-second_col_idx,second_row_idx-first_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_2_0");
        digital_grid[2][0] = Detection(grid_2_0,first_col_idx,height-second_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_2_1");
        digital_grid[2][1] = Detection(grid_2_1,second_col_idx-first_col_idx,height-second_row_idx);
        Log.d("OUTPUT", "-------------------------------grid_2_2");
        digital_grid[2][2] = Detection(grid_2_2,width-second_col_idx,height-second_row_idx);


        Log.d("OUTPUT", "\n--------------");

        return digital_grid;
    }
    public int Detection(int[] retData, int width, int height) {
        Log.d("OUTPUT", "\nwidth = " + Integer.toString(width) + "\nheight = " + Integer.toString(height));

        int type = 0;

        int[] edge_coord = new int[width * height];
        int counter = 0;
        for (int i = 5; i < height-5; i++) {
            for (int j = 5; j < width-5; j++) {
                if (retData[i*width+j]>50){
                    edge_coord[counter]=i*width+j;
                    counter++;
                }
            }
        }

        int diagonal = (int)Math.sqrt(height * height + width * width);
        int[][] parameterCord=new int[180][2*diagonal+1];
        int rho;
        for (int pixel=0; pixel<counter; pixel++){
            for(int theta=0; theta<180;theta++){
                rho= (int)((edge_coord[pixel]%width) * Math.cos(theta * Math.PI / 180) + ((int)(edge_coord[pixel]/width)) * Math.sin(theta * Math.PI / 180));
                parameterCord[theta][rho+diagonal]+=1;
            }
        }
        int max_rho_1=0, max_val_1=0, max_theta_1=0;
        for (int i=25; i<65; i++){
            for (int j=0; j<2*diagonal+1; j++){
                if (parameterCord[i][j]>max_val_1){
                    max_val_1=parameterCord[i][j];
                    max_rho_1=j;
                    max_theta_1=i;
                }
            }
        }
                int max_rho_2=0, max_val_2=0, max_theta_2=0;
        for (int i=115; i<155; i++){
            for (int j=0; j<2*diagonal+1; j++){
                if (parameterCord[i][j]>max_val_2){
                    max_val_2=parameterCord[i][j];
                    max_rho_2=j;
                    max_theta_2=i;
                }
            }
        }


        if (max_val_1>(int)(diagonal/2) && max_val_2>(int)(diagonal/2)){
            Log.d("OUTPUT", "Cross detected");
            type = 1;
        }
        else{
            Log.d("OUTPUT", "Cross not detected");
        }
        Log.d("OUTPUT", "\nmax_theta = " + Integer.toString(max_theta_1) + "\nmax_rho = " + Integer.toString(max_rho_1) + "\nmax_val = " + Integer.toString(max_val_1));
        Log.d("OUTPUT", "\nmax_theta = " + Integer.toString(max_theta_2) + "\nmax_rho = " + Integer.toString(max_rho_2) + "\nmax_val = " + Integer.toString(max_val_2));

        int max_r = (int)Math.sqrt(height * height + width * width) / 2;
        int[][][] parameter_coord = new int[height][width][max_r];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < max_r; k++) {
                    parameter_coord[i][j][k] = 0;
                }
            }
        }
        for (int pixel = 0; pixel < counter; pixel++) {
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int edge_y = edge_coord[pixel] / width;
                    int edge_x = edge_coord[pixel] % width;
                    int r = (int)Math.sqrt((edge_x - j) * (edge_x - j) + (edge_y - i) * (edge_y - i));
                    if (r < max_r) {
                        parameter_coord[i][j][r] += 1;
                    }
                }
            }
        }
        int max_x = 0;
        int max_y = 0;
        int max_rad = 0;
        int max_val = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < max_r; k++) {
                    if (parameter_coord[i][j][k] > max_val) {
                        max_val = parameter_coord[i][j][k];
                        max_x = i;
                        max_y = j;
                        max_rad = k;
                    }
                }
            }
        }

        Log.d("OUTPUT", "\nmax_x = " + Integer.toString(max_x) + "\nmax_y = " + Integer.toString(max_y) + "\nmax_r = " + Integer.toString(max_rad) + "\nmax_val = " + Integer.toString(max_val));
        if (max_val>0.6*2*Math.PI*max_rad){
            Log.d("OUTPUT", "Circle detected");
            type = 2;
        }
        else{
            Log.d("OUTPUT", "Circle not detected");
        }
        return type;
    }

    // Helper function to convert YUV to RGB
    public int[] yuv2rgb(byte[] data){
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) data[yp])) - 16;
                y = y<0? 0:y;

                if ((i & 1) == 0) {
                    v = (0xff & data[uvp++]) - 128;
                    u = (0xff & data[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = r<0? 0:r;
                r = r>262143? 262143:r;
                g = g<0? 0:g;
                g = g>262143? 262143:g;
                b = b<0? 0:b;
                b = b>262143? 262143:b;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    // Helper function to merge the results and convert GrayScale to RGB
    public int[] merge(int[] xdata,int[] ydata){
        int size = height * width/16;
        int[] mergeData = new int[size];
        for(int i=0; i<size; i++)
        {
            int p = (int)Math.sqrt((xdata[i] * xdata[i] + ydata[i] * ydata[i]) / 2);
            mergeData[i] = 0xff000000 | p<<16 | p<<8 | p;
        }
        return mergeData;
    }

    // Helper function to merge the results and convert GrayScale to RGB
    public int[] merge_gray(int[] xdata,int[] ydata){
        int size = height * width/16;
        int[] mergeData = new int[size];
        for(int i=0; i<size; i++)
        {
            int p = (int)Math.sqrt((xdata[i] * xdata[i] + ydata[i] * ydata[i]) / 2);
            mergeData[i] = p;
            //mergeData[i] = 0xff000000 | p<<16 | p<<8 | p;
        }
        return mergeData;
    }

    // Function for Histogram Equalization
    public byte[] histEq(byte[] data, int width, int height){
        byte[] histeqData = new byte[data.length];
        int size = height * width;

        // Perform Histogram Equalization
        // Note that you only need to manipulate data[0:size] that corresponds to luminance
        // The rest data[size:data.length] is for colorness that we handle for you
        // *********************** START YOUR CODE HERE  **************************** //
        int[] histogram = new int[256];
        double[] cdf = new double[256];
        int[] h = new int[256];
        for (int i = 0; i < 256; i++) {
            histogram[i] = 0;
            cdf[i] = 0;
            h[i] = 0;
        }
        int idx;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                idx = data[i * width + j] & 0x00FF;
                histogram[idx]++;
            }
        }
        int acum = 0;
        for (int i = 0; i < 256; i++) {
            acum += histogram[i];
            cdf[i] = acum;
        }
        double cdf_min = 0;
        for (int i = 0; i < 256; i++) {
            cdf_min = cdf[i];
            if (cdf_min != 0) {
                break;
            }
        }
        Log.d("myTag", "cdf_min " + cdf_min);
        for (int v = 0; v < 256; v++) {
            h[v] = (int) (((cdf[v] - cdf_min) / (height * width - 1.0)) * 255);
        }
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                idx = data[i * width + j] & 0x00FF;
                histeqData[i*width+j]= (byte) h[idx];
            }
        }
        // *********************** End YOUR CODE HERE  **************************** //
        // We copy the colorness part for you, do not modify if you want rgb images
        for(int i=size; i<data.length; i++){
            histeqData[i] = data[i];
        }
        return histeqData;
    }

    public int[] conv2(byte[] data, int width, int height, double kernel[][]){
        // 0 is black and 255 is white.
        int size = height * width;
        int[] convData = new int[size];
        for (int i = 0;i < size; i++){
            convData[i] = 0;
        }
        // Perform single channel 2D Convolution
        // Note that you only need to manipulate data[0:size] that corresponds to luminance
        // The rest data[size:data.length] is ignored since we only want grayscale output
        // *********************** START YOUR CODE HERE  **************************** //
        double[][] flip_kernel = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                flip_kernel[i][j] = kernel[2 - i][2 - j];
            }
        }
        int pic_cord_x;
        int pic_cord_y;
        int tmp;
        for(int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        pic_cord_x = i - 1 + a;
                        pic_cord_y = j - 1 + b;
                        if (pic_cord_x < 0 || pic_cord_y < 0 || pic_cord_x >= height || pic_cord_y >= width) {
                            continue;
                        }
                        tmp=data[pic_cord_x*width+pic_cord_y]&0x00FF;
                        convData[i * width + j] += ((int) flip_kernel[a][b])*tmp;
                    }
                }
            }
        }


        // *********************** End YOUR CODE HERE  **************************** //
        return convData;
    }

}
