package com.clj.blesample;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by 98426 on 2019/4/17.
 */

public class DocumentTool {

    /**
     * 【动态申请SD卡读写的权限】
     * Android6.0之后系统对权限的管理更加严格了，不但要在AndroidManifest中添加，还要在应用运行的时候动态申请
     * **/
    private static final int REQUEST_STORAGE = 10 ;
    private static String[] PERMISSON_STORAGE = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE}; // Only for API > 30
    public static void verifyStoragePermissions(Activity activity){
        try {
            for (String p:
                    PERMISSON_STORAGE) {

                int permission = ActivityCompat.checkSelfPermission(activity, p);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, PERMISSON_STORAGE, REQUEST_STORAGE);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**【检查文件目录是否存在，不存在就创建新的目录】**/
    public static void checkFilePath(File file ,boolean isDir){
        if(file!=null){
            if(!isDir){     //如果是文件就返回父目录
                file = file.getParentFile();
            }
            if(file!=null && !file.exists()){
                file.mkdirs();
            }
        }
    }

    /**【创建一个新的文件夹】**/
    public static void addFolder(String folderName){
        try {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File sdCard = Environment.getExternalStorageDirectory();
                File newFolder = new File(sdCard + File.separator + folderName);
                if(!newFolder.exists()){
                    boolean isSuccess = newFolder.mkdirs();
                    Log.i("TAG:","文件夹创建状态--->" + isSuccess);
                }
                Log.i("TAG:","文件夹所在目录：" + newFolder.toString());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**【创建文件】**/
    public static void addFile(String fileName){
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File newFile = new File(sdCard, fileName);
                newFile.setWritable(true);
                if(!newFile.exists()){
                    boolean isSuccess = newFile.createNewFile();
                    Log.i("TAG:","文件创建状态--->"+isSuccess);
                    Log.i("TAG:","文件所在路径："+newFile.toString());
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**【删除文件】**/
    public static void deleteFile(File file){
        if(file.exists()){                          //判断文件是否存在
            if(file.isFile()){                      //判断是否是文件
                boolean isSucess = file.delete();
                Log.i("TAG:","文件删除状态--->" + isSucess);
            }else if(file.isDirectory()){           //判断是否是文件夹
                File files[] = file.listFiles();    //声明目录下所有文件
                for (int i=0;i<files.length;i++){   //遍历目录下所有文件
                    deleteFile(files[i]);           //把每个文件迭代删除
                }
                boolean isSucess = file.delete();
                Log.i("TAG:","文件夹删除状态--->" + isSucess);
            }
        }
    }

    /**【重写数据到文件】**/
    public static void writeData(String path , String fileData){
        try {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File file = new File(path);
                FileOutputStream out = new FileOutputStream(file,false);
                out.write(fileData.getBytes("UTF-8"));              //将数据写入到文件中
                Log.i("TAG:","将数据写入到文件中："+fileData);
                out.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**【续写数据到文件】**/
    public synchronized static void appendFileData(String name , byte[] data){
        try {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File sdCard = Environment.getExternalStorageDirectory();
                File file = new File(sdCard, name);
                file.setWritable(true);

                if(!file.exists()){
                    boolean isSuccess = file.createNewFile();
                    Log.i("TAG:","文件创建状态--->"+isSuccess);
                    Log.i("TAG:","文件所在路径："+file.toString());
                }


                RandomAccessFile raf = new RandomAccessFile(file,"rw");  //按读写方式
                raf.seek(file.length());                                        //将文件指针移到文件尾
                raf.write(data);               //将数据写入到文件中

                Log.i("TAG:","文件所在路径：" + file.toString());
                Log.i("TAG:","要续写进去的数据：" + data.toString());

                raf.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**【读取文件内容】**/
    public static String readFileContent(String path){
        try {
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                File file = new File(path);
                byte [] buffer = new byte[32*1024];
                FileInputStream fis = new FileInputStream(file);
                int len = 0;
                StringBuffer sb = new StringBuffer("");
                while((len=fis.read(buffer))>0){
                    sb.append(new String(buffer,0,len));
                }
                fis.close();
                return sb.toString();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**【判断文件是否存在】**/
    public static boolean isFileExists(String fileName){
        File file = new File(fileName);
        return file.exists();
    }

    /**【判断文件夹是否存在】**/
    public static boolean isFolderExists(String directoryPath){
        if(TextUtils.isEmpty(directoryPath)){
            return false;
        }
        File dire = new File(directoryPath);
        return (dire.exists() && dire.isDirectory());  //如果是文件夹并且文件夹存在则返回true
    }

    /**【获取文件夹名称】**/
    public static String getFolderName(String folderName){
        if(TextUtils.isEmpty(folderName)){
            return folderName;
        }
        int filePosi = folderName.lastIndexOf(File.separator);
        return (filePosi == -1 ) ? "" : folderName.substring(0 , filePosi);
    }

    /**【重命名文件】**/
    public static boolean renameFile(String oldFileName , String newFileName){
        File oldName = new File(oldFileName);
        File newName = new File(newFileName);
        return oldName.renameTo(newName);
    }

    /**【判断文件夹里是否有文件】**/
    public static boolean hasFileExists(String folderPath){
        File file = new File(folderPath);
        if(file.exists()){
            File [] files = file.listFiles();
            if(files.length>0){
                return true;
            }
        }
        return false;
    }

    /**【复制文件】参数为：String **/
    public static int copyFile(String fromFile , String toFile){
        try {
            InputStream fosfrom = new FileInputStream(fromFile);
            OutputStream outto = new FileOutputStream(toFile);
            byte[] bt = new byte[1024];
            int len = fosfrom.read(bt);
            if(len > 0){
                outto.write(bt,0,len);
            }
            fosfrom.close();
            outto.close();
            return 0;
        }catch (Exception e){
            e.printStackTrace();
            return -1;
        }
    }
    /**【复制文件】参数为：File  **/
    public static int copyFile(File formFile , File toFile){
        try {
            InputStream forform = new FileInputStream(formFile);
            OutputStream forto = new FileOutputStream(toFile);
            byte [] bt = new byte[1024];
            int len = forform.read(bt);
            if(len > 0){
                forto.write(bt,0,len);
            }
            forform.close();
            forto.close();
            return 0;
        }catch (Exception e){
            e.printStackTrace();
            return -1;
        }
    }
    /**【复制文件】使用：AssetManager  **/
    public static void copyFileFormAsset(Context context,String assetFile , String toFilePath){
        if(!new File(toFilePath).exists()){
            try {
                AssetManager assetManager = context.getAssets();
                InputStream is = assetManager.open(assetFile);
                OutputStream os = new FileOutputStream(new File(toFilePath));
                byte [] bt = new byte[1024];
                int len = 0;
                while ((is.read(bt))>0){        //循环从输入流读取
                    os.write(bt,0,len);     //将读取到的输入流写到输出流
                }
                is.close();
                os.close();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**【复制文件夹】**/
    public static int copyDir(String fromFolder , String toFolder){
        File [] currentFiles;
        File root = new File(fromFolder);
        if(!root.exists()){                     //如果文件不存在就返回出去
            return -1;
        }
        currentFiles = root.listFiles();        //存在则获取当前目录下的所有文件
        File targetDir = new File(toFolder);    //目标目录
        if(!targetDir.exists()){                //不存在就创建新目录
            targetDir.mkdirs();
        }
        for(int i=0;i<currentFiles.length;i++){ //遍历currentFiles下的所有文件
            if(currentFiles[i].isDirectory()){  //如果当前目录为子目录
                copyDir(currentFiles[i].getPath() + "/" , currentFiles[i].getName()+"/");  /**进行当前函数递归操作**/
            }else{                              //当前为文件，则进行文件拷贝
                copyFile(currentFiles[i].getPath() , toFolder + currentFiles[i].getName());
            }
        }
        return 0;
    }

}
