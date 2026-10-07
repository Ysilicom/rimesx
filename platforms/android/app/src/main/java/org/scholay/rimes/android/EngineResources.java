package org.scholay.rimes.android;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Iterator;

/** Versioned system data and durable user dictionaries are both excluded from backup. */
final class EngineResources {
    static File prepare(Context context) throws Exception {
        byte[] manifest;
        try(InputStream in=context.getAssets().open("rime/manifest.json")) { manifest=readAll(in); }
        JSONObject files=new JSONObject(new String(manifest,StandardCharsets.UTF_8)).getJSONObject("files");
        File root=new File(context.getNoBackupFilesDir(),"rime-system/"+hash(manifest));
        File stamp=new File(root,".verified");
        if(stamp.exists() || valid(root,files)) {
            if(!stamp.exists()) try { stamp.createNewFile(); } catch(Exception ignored) {}
            return root;
        }
        File staging=new File(root.getPath()+".staging");
        remove(staging);
        if (!staging.mkdirs()) throw new java.io.IOException("Cannot prepare dictionaries");
        Iterator<String> names=files.keys();
        while(names.hasNext()) {
            String name=names.next();
            if(name.startsWith("/") || name.contains("..")) throw new java.io.IOException("Invalid resource path");
            File output=new File(staging,name);
            File parent=output.getParentFile();
            if(!parent.isDirectory() && !parent.mkdirs()) throw new java.io.IOException("Cannot create resource directory");
            try(InputStream in=context.getAssets().open("rime/"+name); FileOutputStream out=new FileOutputStream(output)) {
                byte[] block=new byte[32768]; int count;
                while((count=in.read(block))!=-1) out.write(block,0,count);
                out.getFD().sync();
            }
        }
        if(!valid(staging,files)) throw new java.io.IOException("Dictionary checksum mismatch");
        remove(root);
        if(!staging.renameTo(root)) throw new java.io.IOException("Cannot install dictionaries");
        try { new File(root,".verified").createNewFile(); } catch(Exception ignored) {}
        return root;
    }
    static File userDirectory(Context context) throws java.io.IOException {
        File directory=new File(context.getNoBackupFilesDir(),"rime-user");
        if(!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot open user dictionary");
        return directory;
    }
    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] bytes=new byte[8192]; int count;
        while((count=in.read(bytes))!=-1) out.write(bytes,0,count);
        return out.toByteArray();
    }
    private static boolean valid(File root,JSONObject files) throws Exception {
        Iterator<String> names=files.keys();
        while(names.hasNext()) {
            String name=names.next(); File file=new File(root,name);
            if(!file.isFile() || !hash(Files.readAllBytes(file.toPath())).equals(files.getString(name))) return false;
        }
        return true;
    }
    private static String hash(byte[] value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out=new StringBuilder();
        for(byte b:digest) out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static void remove(File file) throws java.io.IOException {
        File[] children=file.listFiles();
        if(children!=null) for(File child:children) remove(child);
        if(file.exists() && !file.delete()) throw new java.io.IOException("Cannot replace system resources");
    }
}
