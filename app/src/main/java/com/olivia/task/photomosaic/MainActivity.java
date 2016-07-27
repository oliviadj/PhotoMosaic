package com.olivia.task.photomosaic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private static final int LOAD_IMAGE = 100;
    private static final int totalThread = 3;   //number of threads
    List<Tile> tileList = new ArrayList<Tile>();
    boolean running;    //flag to control thread execution, as interrupt method is not reliable
    int threadCount;
    Button btnSelectImage;
    ImageView imgView;
    Canvas canvas;
    Bitmap bitmap;
    Thread thread1;
    Thread thread2;
    Thread thread3;
    int countCol;
    int height;
    int width;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgView = (ImageView) findViewById(R.id.imgView);

        btnSelectImage = (Button) findViewById(R.id.btnSelectImage);
        btnSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // each time user clicks "load image" button, all threads are stopped
                running = false;
                bitmap = null;
                if (thread1 != null) {
                    thread1.interrupt();
                    thread1 = null;
                }
                if (thread2 != null) {
                    thread2.interrupt();
                    thread2 = null;
                }
                if (thread3 != null) {
                    thread3.interrupt();
                    thread3 = null;
                }
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, LOAD_IMAGE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        switch (requestCode) {
            case LOAD_IMAGE:
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = imageReturnedIntent.getData();
                    try {
                        InputStream imageStream = getContentResolver().openInputStream(selectedImage);
                        Bitmap oriBitmap = BitmapFactory.decodeStream(imageStream);
                        bitmap = oriBitmap.copy(Bitmap.Config.ARGB_8888, true);
                        canvas = new Canvas(bitmap);
                        imgView.setImageBitmap(bitmap);

                        if (bitmap != null) {
                            threadCount = -1;
                            running = true; //set thread count to -1, so the first thread will count from 0

                            height = bitmap.getHeight() - (bitmap.getHeight() % Config.tileHeight);
                            width = bitmap.getWidth() - (bitmap.getWidth() % Config.tileWitdth);
                            countCol = bitmap.getWidth() / Config.tileWitdth;
                            //Log.e(TAG, "height : " + height + ", width : " + width + ", countCol : " + countCol);

                            //use few threads to do parallel rendering
                            thread1 = new Thread(runnable1);
                            thread1.start();
                            thread2 = new Thread(runnable2);
                            thread2.start();
                            thread3 = new Thread(runnable3);
                            thread3.start();

                        } else {    //bitmap is null
                            Toast.makeText(MainActivity.this, getResources().getString(R.string.no_photo_selected), Toast.LENGTH_LONG).show();
                        }
                    } catch (OutOfMemoryError e){
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.size_too_big), Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        }
    }

    Runnable runnable1 = new Runnable() {
        @Override
        public void run() {
            //renderMosaicImage(++threadCount, totalThread * Config.tileHeight, thread1);
            renderMosaicImage(++threadCount);
        }
    };

    Runnable runnable2 = new Runnable() {
        @Override
        public void run() {
            //renderMosaicImage(++threadCount, totalThread * Config.tileHeight, thread2);
            renderMosaicImage(++threadCount);
        }
    };

    Runnable runnable3 = new Runnable() {
        @Override
        public void run() {
            //renderMosaicImage(++threadCount, totalThread * Config.tileHeight, thread3);
            renderMosaicImage(++threadCount);
        }
    };

    //private void renderMosaicImage(int startIndex, int range, Thread thread) {
    private void renderMosaicImage(int startIndex) {
        try {
            int range = totalThread * Config.tileHeight;

            //divide the image into tiles, row first
            for (int y = startIndex * Config.tileHeight; y < height; y = y + range) {
                int col = 0;
                for (int x = 0; x < width; x = x + Config.tileWitdth) {
/*
                    if (thread != null && thread.isInterrupted()) {
                        Log.e(TAG, "THREAD IS INTERRUPTED!");
                        break;
                    }
*/
                    //find a representative color for each tile (average);
                    String hexColor = representativeColor(bitmap, x, y);

                    Tile tile = new Tile();
                    tile.hexColor = hexColor;
                    tile.x = x;
                    tile.y = y;
                    tile.col = ++col;

                    //fetch a tile from the provided server for that color;
                    String url = Config.serverUrl + hexColor;
                    new ServerTile(imgView, canvas, x, y, tile).execute(url);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String representativeColor(Bitmap bitmap, int x, int y) {
        long redBucket = 0;
        long greenBucket = 0;
        long blueBucket = 0;
        long pixelCount = 0;

        for (int row = y; row < y + Config.tileHeight; row++) {
            for (int col = x; col < x + Config.tileWitdth; col++) {
                int c = bitmap.getPixel(col, row);
                pixelCount++;
                redBucket += Color.red(c);
                greenBucket += Color.green(c);
                blueBucket += Color.blue(c);
            }
        }
        int r = (int) (redBucket / pixelCount);
        int g = (int) (greenBucket / pixelCount);
        int b = (int) (blueBucket / pixelCount);
        return String.format("%02x%02x%02x", r, g, b);
    }


    class ServerTile extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;
        Canvas canvas;
        int x;
        int y;
        Tile tile;

        ServerTile(ImageView imageView, Canvas canvas, int x, int y, Tile tile) {
            this.bmImage = imageView;
            this.canvas = canvas;
            this.x = x;
            this.y = y;
            this.tile = tile;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            Bitmap mosaicBitmap = null;
            if (running) {
                String url = urls[0];
                //Log.e(TAG, "urlDisplay : " + urlDisplay);
                try {
                    InputStream in = new java.net.URL(url).openStream();
                    mosaicBitmap = BitmapFactory.decodeStream(in);
                } catch (Exception e) {
                    //Log.e("Error", e.getMessage());
                    e.printStackTrace();
                }
            }
            return mosaicBitmap;
        }

        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                //Log.e(TAG, "onPostExecute, x:" + x + ", y:" + y + ", tileCol: " + tile.col);

                // put in a list first, as later we will render the mosaic per row
                tile.bitmap = result;
                tileList.add(tile);

                // if each row has been completed, render it to the screen
                if (tile.col == countCol) {
                    for (Tile tile : tileList) {
                        canvas.drawBitmap(tile.bitmap, tile.x, tile.y, null);
                        imgView.invalidate();
                    }
                    tileList.clear();
                }
            }
        }
    }

    public class Tile {
        String hexColor;
        int x;
        int y;
        int col;
        Bitmap bitmap;
    }
}