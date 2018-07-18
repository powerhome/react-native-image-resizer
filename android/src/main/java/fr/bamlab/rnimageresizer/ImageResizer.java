package fr.bamlab.rnimageresizer;

import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Provide methods to resize and rotate an image file.
 */
public class ImageResizer {
    private final static String IMAGE_JPEG = "image/jpeg";
    private final static String IMAGE_PNG = "image/png";
    private final static String SCHEME_DATA = "data";
    private final static String SCHEME_CONTENT = "content";
    private final static String SCHEME_FILE = "file";

    /**
     * Resize the specified bitmap, keeping its aspect ratio.
     */
    private static Bitmap resizeImage(Bitmap image, int width, int height) {
        if (image == null) {
            return null; // Can't load the image from the given path.
        }

        try {
            return Bitmap.createScaledBitmap(image, width, height, true);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Rotate the specified bitmap with the given angle, in degrees.
     */
    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        try {
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Save the given bitmap in a directory. Extension is automatically generated using the bitmap format.
     */
    private static File saveImage(Bitmap bitmap, File saveDirectory, String fileName, Bitmap.CompressFormat compressFormat, int quality) throws IOException {
        if (bitmap == null) {
            throw new IOException("The bitmap couldn't be resized");
        }

        File newFile = new File(saveDirectory, fileName + "." + compressFormat.name());
        if(!newFile.createNewFile()) {
            throw new IOException("The file already exists");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(compressFormat, quality, outputStream);
        byte[] bitmapData = outputStream.toByteArray();

        outputStream.flush();
        outputStream.close();

        FileOutputStream fos = new FileOutputStream(newFile);
        fos.write(bitmapData);
        fos.flush();
        fos.close();

        return newFile;
    }

    /**
     * Compute the inSampleSize value to use to load a bitmap.
     * Adapted from https://developer.android.com/training/displaying-bitmaps/load-bitmap.html
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;

        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Load a bitmap either from a real file or using the {@link ContentResolver} of the current
     * {@link Context} (to read gallery images for example).
     *
     * Note that, when options.inJustDecodeBounds = true, we actually expect sourceImage to remain
     * as null (see https://developer.android.com/training/displaying-bitmaps/load-bitmap.html), so
     * getting null sourceImage at the completion of this method is not always worthy of an error.
     */
    private static Bitmap loadBitmap(Context context, Uri imageUri, BitmapFactory.Options options) throws IOException {
        Bitmap sourceImage = null;
        String imageUriScheme = imageUri.getScheme();
        if (imageUriScheme == null || !imageUriScheme.equalsIgnoreCase(SCHEME_CONTENT)) {
            try {
                sourceImage = BitmapFactory.decodeFile(imageUri.getPath(), options);
            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException("Error decoding image file");
            }
        } else {
            ContentResolver cr = context.getContentResolver();
            InputStream input = cr.openInputStream(imageUri);
            if (input != null) {
                sourceImage = BitmapFactory.decodeStream(input, null, options);
                input.close();
            }
        }
        return sourceImage;
    }

    /**
     * Loads the bitmap resource from the file specified in imagePath.
     */
    private static Bitmap loadBitmapFromFile(Context context, Uri imageUri, int newWidth, int newHeight) throws IOException  {
        // Decode the image bounds to find the size of the source image.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        loadBitmap(context, imageUri, options);

        // Set a sample size according to the image size to lower memory usage.
        options.inSampleSize = calculateInSampleSize(options, newWidth, newHeight);
        options.inJustDecodeBounds = false;
        //System.out.println(options.inSampleSize);
        return loadBitmap(context, imageUri, options);
    }

    /**
     * Create a resized version of the given image.
     */
    public static File createResizedImage(Context context, Uri imageUri, int newWidth, int newHeight, Bitmap.CompressFormat compressFormat, int quality, int rotation) throws IOException {
        Bitmap sourceImage = null;
        String imageUriScheme = imageUri.getScheme();
        if (imageUriScheme == null || imageUriScheme.equalsIgnoreCase(SCHEME_FILE) || imageUriScheme.equalsIgnoreCase(SCHEME_CONTENT)) {
            sourceImage = ImageResizer.loadBitmapFromFile(context, imageUri, newWidth, newHeight);
        }

        if (sourceImage == null) {
            throw new IOException("Unable to load source image from path");
        }

        // Scale it first so there are fewer pixels to transform in the rotation
        Bitmap scaledImage = ImageResizer.resizeImage(sourceImage, newWidth, newHeight);

        // Rotate if necessary
        Bitmap rotatedImage = ImageResizer.rotateImage(scaledImage, rotation);

        // Save the resulting image
        File path = context.getCacheDir();

        File newFile = ImageResizer.saveImage(rotatedImage, path, Long.toString(new Date().getTime()), compressFormat, quality);

        // Clean up remaining image
        sourceImage.recycle();
        scaledImage.recycle();
        rotatedImage.recycle();

        return newFile;
    }
}
