package org.odk.collect.android.views;

import java.io.File;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.odk.collect.android.R;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * @author wspride
 *    Class used by MediaLayout for form images. Can be set to resize the
 *    image using different algorithms based on the preference specified
 *    by PreferencesActivity.KEY_RESIZE. Overrides setMaxWidth, setMaxHeight,
 *  and onMeasure from the ImageView super class.
 */

@SuppressLint("NewApi")
public class ResizingImageView extends ImageView {

    public static String resizeMethod;

    private int mMaxWidth;
    private int mMaxHeight;

    GestureDetector gestureDetector;
    ScaleGestureDetector scaleGestureDetector;

    String imageURI;
    String bigImageURI;

    private float scaleFactor = 1.0f;
    private float scaleFactorThreshhold = 1.2f;

    public ResizingImageView(Context context) {
        this(context, null, null);
    }

    public ResizingImageView(Context context, String imageURI, String bigImageURI){
        super(context);
        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        this.imageURI = imageURI;
        this.bigImageURI = bigImageURI;
    }

    /*
     * (non-Javadoc)
     * @see android.view.View#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleGestureDetector.onTouchEvent(e);
        return gestureDetector.onTouchEvent(e);
    }

    /*
     * (non-Javadoc)
     * @see android.widget.ImageView#setMaxWidth(int)
     */
    @Override
    public void setMaxWidth(int maxWidth) {
        super.setMaxWidth(maxWidth);
        mMaxWidth = maxWidth;
    }

    /*
     * (non-Javadoc)
     * @see android.widget.ImageView#setMaxHeight(int)
     */
    @Override
    public void setMaxHeight(int maxHeight) {
        super.setMaxHeight(maxHeight);
        mMaxHeight = maxHeight;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        /*
         * (non-Javadoc)
         * @see android.view.GestureDetector.SimpleOnGestureListener#onDown(android.view.MotionEvent)
         */
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        /*
         * (non-Javadoc)
         * @see android.view.GestureDetector.SimpleOnGestureListener#onDoubleTap(android.view.MotionEvent)
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            setFullScreen();
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        /*
         * (non-Javadoc)
         * @see android.view.ScaleGestureDetector.SimpleOnScaleGestureListener#onScale(android.view.ScaleGestureDetector)
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();

            // don't let the object get too small or too large.
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));

            if(scaleFactor > scaleFactorThreshhold){
                setFullScreen();
            }
            return true;
        }
    }

    public void setFullScreen(){
        String imageFileURI;

        if(bigImageURI != null){
            imageFileURI = bigImageURI;
        } else if(imageURI != null){
            imageFileURI = imageURI;
        } else{
            return;
        }

        try {
            String imageFilename = ReferenceManager._()
                    .DeriveReference(imageFileURI).getLocalURI();
            File bigImage = new File(imageFilename);

            Intent i = new Intent("android.intent.action.VIEW");
            i.setDataAndType(Uri.fromFile(bigImage), "image/*");
            getContext().startActivity(i);
        } catch (InvalidReferenceException e1) {
            e1.printStackTrace();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    getContext(),
                    getContext().getString(R.string.activity_not_found,
                            "view image"), Toast.LENGTH_SHORT);
        }
    }

    public Pair<Integer,Integer> getWidthHeight(int widthMeasureSpec, int heightMeasureSpec, Drawable drawable, double scaleFactor){

        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);

        // Calculate the most appropriate size for the view. Take into
        // account minWidth, minHeight, maxWith, maxHeigh and allowed size
        // for the view, then scale by the scaleFactor

        int maxWidth = wMode == MeasureSpec.AT_MOST
                ? Math.min(MeasureSpec.getSize(widthMeasureSpec), mMaxWidth)
                        : mMaxWidth;
                int maxHeight = hMode == MeasureSpec.AT_MOST
                        ? Math.min(MeasureSpec.getSize(heightMeasureSpec), mMaxHeight)
                                : mMaxHeight;

                        float dWidth = dipToPixels(getContext(), drawable.getIntrinsicWidth());
                        float dHeight = dipToPixels(getContext(), drawable.getIntrinsicHeight());
                        float ratio = (dWidth) / dHeight;

                        int width = (int) Math.min(Math.max(dWidth, getSuggestedMinimumWidth()), maxWidth);
                        int height = (int) (width / ratio);

                        height = Math.min(Math.max(height, getSuggestedMinimumHeight()), maxHeight);
                        width = (int) (height * ratio);

                        if (width > maxWidth) {
                            width = maxWidth;
                            height = (int) (width / ratio);
                        }

                        Pair<Integer,Integer> mPair = new Pair<Integer,Integer>((int)(width * scaleFactor), (int)(height * scaleFactor));

                        return mPair;
    }

    /*
     * (non-Javadoc)
     * @see android.widget.ImageView#onMeasure(int, int)
     * 
     * The meat and potatoes of the class. Determines what algorithm to use
     * to resize the image based on the KEY_RESIZE preference. Currently can be
     * "full", "width", or "none". Will always preserve aspect ratio. 
     * 
     * "full" attempts to use both the calculated height and width to scale the image. however,
     *         its worth noting that the available height is dynamic and difficult to determine
     * "width" will always stretch/compress the image to make it the exact width of the screen while
     *         maintaining the aspect ratio
     * "none" will leave the picture unchanged
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if(resizeMethod.equals("full")){

            Drawable drawable = getDrawable();
            if (drawable != null) {
                Pair<Integer,Integer> mPair = this.getWidthHeight(widthMeasureSpec, heightMeasureSpec, drawable, 1);
                setMeasuredDimension(mPair.first, mPair.second);
            }
        }
        if(resizeMethod.equals("half")){

            Drawable drawable = getDrawable();
            if (drawable != null) {
                Pair<Integer,Integer> mPair = this.getWidthHeight(widthMeasureSpec, heightMeasureSpec, drawable, .7);
                setMeasuredDimension(mPair.first, mPair.second);
            }
        }
        else if(resizeMethod.equals("width")){
            Drawable d = getDrawable();

            if(d!=null){
                // ceil not round - avoid thin vertical gaps along the left/right edges
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = (int) Math.ceil((float) width * (float) d.getIntrinsicHeight() / (float) d.getIntrinsicWidth());
                setMeasuredDimension(width, height);
            }
        }
    }
    // helper method for algorithm above
    public static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }
}