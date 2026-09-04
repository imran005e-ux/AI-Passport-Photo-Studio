package com.cyberplace.passportstudio;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import com.google.mlkit.vision.segmentation.subject.*;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int PICK_IMAGE = 101;
    LinearLayout photoList;
    TextView status;
    EditText spacingInput;
    ArrayList<PhotoItem> items = new ArrayList<>();

    static class PhotoItem {
        Bitmap bitmap;
        EditText copies;
        TextView name;
        PhotoItem(Bitmap b, EditText c, TextView n){bitmap=b; copies=c; name=n;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        photoList=findViewById(R.id.photoList);
        status=findViewById(R.id.statusText);
        spacingInput=findViewById(R.id.spacingInput);
        findViewById(R.id.addPhotoButton).setOnClickListener(v -> pickImage());
        findViewById(R.id.generateButton).setOnClickListener(v -> generatePdf());
    }

    void pickImage(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_IMAGE);
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req!=PICK_IMAGE || res!=RESULT_OK || data==null) return;
        Uri u=data.getData();
        try{
            Bitmap original=MediaStore.Images.Media.getBitmap(getContentResolver(),u);
            status.setText("AI processing: removing background and detecting face…");
            processWithAI(original);
        }catch(Exception e){ status.setText("Could not open image: "+e.getMessage());}
    }

    void processWithAI(Bitmap original){
        InputImage image=InputImage.fromBitmap(original,0);

        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build();
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);

        segmenter.process(image)
            .addOnSuccessListener(result -> {
                Bitmap fg=result.getForegroundBitmap();
                Bitmap white=onWhiteBackground(fg);
                detectAndCropFace(white);
            })
            .addOnFailureListener(e -> {
                status.setText("AI background removal failed. Try a clearer photo. "+e.getMessage());
                // Keep app usable: fall back to original.
                detectAndCropFace(original);
            });
    }

    Bitmap onWhiteBackground(Bitmap fg){
        Bitmap out=Bitmap.createBitmap(fg.getWidth(),fg.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(fg,0,0,p);
        return out;
    }

    void detectAndCropFace(Bitmap src){
        InputImage image=InputImage.fromBitmap(src,0);
        FaceDetectorOptions opts=new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        FaceDetector detector=FaceDetection.getClient(opts);
        detector.process(image)
            .addOnSuccessListener(faces -> {
                Bitmap finalBitmap;
                if(faces.size()>0){
                    finalBitmap=cropForPassport(src,faces.get(0).getBoundingBox());
                } else {
                    finalBitmap=smartCenterCrop(src);
                }
                addPhoto(finalBitmap);
                status.setText("AI ready. Set copies for this photo.");
                detector.close();
            })
            .addOnFailureListener(e -> {
                addPhoto(smartCenterCrop(src));
                status.setText("Face detection unavailable; smart crop applied.");
                detector.close();
            });
    }

    Bitmap cropForPassport(Bitmap src, Rect face){
        int W=src.getWidth(), H=src.getHeight();
        float faceCx=face.centerX();
        // Target portrait box is 3.5:4.5. Keep head with generous shoulder space.
        float desiredCropH=face.height()*3.4f;
        desiredCropH=Math.max(desiredCropH, H*0.55f);
        desiredCropH=Math.min(desiredCropH, H);
        float desiredCropW=desiredCropH*3.5f/4.5f;
        desiredCropW=Math.min(desiredCropW,W);

        // Place face center around upper-middle of final crop.
        float cropTop=face.centerY()-desiredCropH*0.38f;
        float cropLeft=faceCx-desiredCropW/2f;
        if(cropTop<0) cropTop=0;
        if(cropLeft<0) cropLeft=0;
        if(cropTop+desiredCropH>H) cropTop=H-desiredCropH;
        if(cropLeft+desiredCropW>W) cropLeft=W-desiredCropW;
        int l=Math.max(0,Math.round(cropLeft)), t=Math.max(0,Math.round(cropTop));
        int cw=Math.min(W-l,Math.round(desiredCropW));
        int ch=Math.min(H-t,Math.round(desiredCropH));
        return Bitmap.createBitmap(src,l,t,cw,ch);
    }

    Bitmap smartCenterCrop(Bitmap src){
        int W=src.getWidth(),H=src.getHeight();
        float target=3.5f/4.5f;
        int cw=W, ch=Math.round(W/target);
        if(ch>H){ch=H;cw=Math.round(H*target);}
        int l=(W-cw)/2,t=(H-ch)/2;
        return Bitmap.createBitmap(src,l,t,cw,ch);
    }

    void addPhoto(Bitmap b){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0,10,0,10);

        ImageView iv=new ImageView(this);
        iv.setImageBitmap(b);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(iv,new LinearLayout.LayoutParams(180,230));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16,0,0,0);

        TextView name=new TextView(this);
        name.setText("Photo "+(items.size()+1));
        name.setTextSize(17); name.setTypeface(null,1);

        EditText copies=new EditText(this);
        copies.setHint("Copies");
        copies.setText("4");
        copies.setInputType(2);

        Button remove=new Button(this);
        remove.setText("REMOVE");
        remove.setOnClickListener(v -> {
            int idx=photoList.indexOfChild(row);
            if(idx>=0){photoList.removeView(row); items.remove(idx);}
            renumber();
        });

        box.addView(name); box.addView(copies); box.addView(remove);
        row.addView(box,new LinearLayout.LayoutParams(-1,-2));
        photoList.addView(row);
        items.add(new PhotoItem(b,copies,name));
    }

    void renumber(){
        for(int i=0;i<items.size();i++) items.get(i).name.setText("Photo "+(i+1));
    }

    Bitmap makePassport(Bitmap src){
        int W=413,H=531;
        float scale=Math.max((float)W/src.getWidth(),(float)H/src.getHeight());
        int nw=Math.round(src.getWidth()*scale), nh=Math.round(src.getHeight()*scale);
        Bitmap scaled=Bitmap.createScaledBitmap(src,nw,nh,true);
        int left=(nw-W)/2, top=(nh-H)/2;
        return Bitmap.createBitmap(scaled,left,top,W,H);
    }

    void generatePdf(){
        if(items.size()==0){status.setText("Add at least one photo."); return;}
        float spacingMm=2;
        try{spacingMm=Float.parseFloat(spacingInput.getText().toString());}catch(Exception ignored){}
        final float A4W=595.28f, A4H=841.89f;
        final float photoW=99.21f, photoH=127.56f;
        final float spacing=spacingMm*72f/25.4f;
        final float margin=18f, stroke=0.8f;

        PdfDocument pdf=new PdfDocument();
        int pageNo=1;
        PdfDocument.Page page=null; Canvas canvas=null;
        float x=margin,y=margin,rowH=photoH;
        ArrayList<Bitmap> queue=new ArrayList<>();

        for(PhotoItem it:items){
            int n=0; try{n=Integer.parseInt(it.copies.getText().toString());}catch(Exception ignored){}
            Bitmap p=makePassport(it.bitmap);
            for(int k=0;k<n;k++) queue.add(p);
        }
        for(Bitmap p:queue){
            if(page==null){
                PdfDocument.PageInfo pi=new PdfDocument.PageInfo.Builder(Math.round(A4W),Math.round(A4H),pageNo++).create();
                page=pdf.startPage(pi); canvas=page.getCanvas(); canvas.drawColor(Color.WHITE);
                x=margin;y=margin;
            }
            if(x+photoW>A4W-margin){x=margin;y+=rowH+spacing;}
            if(y+photoH>A4H-margin){pdf.finishPage(page);page=null;canvas=null;continue;}
            RectF dst=new RectF(x,y,x+photoW,y+photoH);
            Paint pnt=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(p,null,dst,pnt);
            Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);border.setColor(Color.BLACK);border.setStrokeWidth(stroke);
            canvas.drawRect(dst,border);
            x+=photoW+spacing;
        }
        if(page!=null) pdf.finishPage(page);

        try{
            File out=new File(getExternalFilesDir(null),"Passport_A4_AI_"+System.currentTimeMillis()+".pdf");
            FileOutputStream fos=new FileOutputStream(out);pdf.writeTo(fos);fos.close();pdf.close();
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",out);
            Intent share=new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");share.putExtra(Intent.EXTRA_STREAM,uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Print / Share A4 PDF"));
            status.setText("A4 PDF ready.");
        }catch(Exception e){status.setText("PDF error: "+e.getMessage());try{pdf.close();}catch(Exception ignored){}}
    }
}
