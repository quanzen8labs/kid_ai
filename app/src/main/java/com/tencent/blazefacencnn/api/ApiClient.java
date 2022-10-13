package com.tencent.blazefacencnn.api;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.tencent.blazefacencnn.api.models.FaceDetectResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.*;

interface ApiRepository {
    @Multipart
    @POST("recognize/json")
    Call<List<FaceDetectResult>> detectFaces(@Part MultipartBody.Part image);
}

public class ApiClient {
    ApiRepository repository;

    public ApiClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://face.ai.kidsenglish.vn")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        repository = retrofit.create(ApiRepository.class);
    }

    public  Call<List<FaceDetectResult>> detectResultCall(Context context, Bitmap image) {
//        byte[] byteArray = bitmapToBytes(image);
//        String base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT);
        try {
            File f = new File(context.getCacheDir(), "test");
            f.createNewFile();

//Convert bitmap to byte array
            Bitmap bitmap = image;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

//write the bytes in file
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(f);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            try {
                fos.write(bitmapdata);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            RequestBody reqFile = RequestBody.create(MediaType.parse("image/*"), f);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", f.getName(), reqFile);
            return repository.detectFaces(body);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] bitmapToBytes(Bitmap photo) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }
}