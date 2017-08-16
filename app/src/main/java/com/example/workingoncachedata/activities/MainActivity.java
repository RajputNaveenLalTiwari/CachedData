package com.example.workingoncachedata.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StatFs;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;

import com.example.workingoncachedata.R;
import android.content.pm.IPackageStatsObserver;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
{
    private static final int RUNTIME_PERMISSION_REQUEST_CODE = 5;
    private Context context;
    private PackageManager packageManager;
    List<ApplicationInfo> applicationInfoList;
    List<String> pathList;

    Method mGetPackageSizeInfoMethod,mFreeStorageAndNotifyMethod;
    private long mCacheSize = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = MainActivity.this;
pathList = new ArrayList<>();
        new TaskScanCache().execute();

    }

    private void getUserInstalledApplications()
    {
        mCacheSize = 0 ;
        packageManager = context.getPackageManager();
        applicationInfoList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo applicationInfo : applicationInfoList)
        {
                try
                {
                    mGetPackageSizeInfoMethod = packageManager.getClass().getMethod("getPackageSizeInfo", String.class, IPackageStatsObserver.class);
                    mGetPackageSizeInfoMethod.invoke(packageManager, applicationInfo.packageName,
                            new IPackageStatsObserver.Stub() {

                                @Override
                                public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) throws RemoteException {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                                        mCacheSize += pStats.cacheSize;
                                    }

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                        mCacheSize += pStats.externalCacheSize;
                                    }

                                    Log.e("Cached Data",""+pStats.packageName+" = "+ Formatter.formatFileSize(context,mCacheSize));

                                    String cachePaths = Environment.getExternalStorageDirectory().getAbsolutePath().toString()+"/Android/data/";
                                    pathList.add(cachePaths);
                                }
                            });
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

        }


    }

    private class TaskScanCache extends AsyncTask<Void,Integer,Long>
    {

        @Override
        protected Long doInBackground(Void... params)
        {
            getUserInstalledApplications();
            return mCacheSize;
        }

        @Override
        protected void onPostExecute(Long aLong)
        {
//            new TaskCleanCache().execute();


        }
    }

    private void dirDelete(File dir) {

        if (dir.exists()) {
            File[] fileList = dir.listFiles();
            for (int i = 0; i < fileList.length; i++) {
                // Recursive call if it's a directory
                if (fileList[i].isDirectory()) {
                    dirDelete(fileList[i]);
                } else {
                    // Sum the file size in bytes
                    deleteDirectory(fileList[i],true);
//                    fileList[i].delete();
//                    Log.i("Test "+fileList[i].getAbsolutePath(),fileList[i].getName());
                }
            }
            // return the file size
        }

    }

    private class TaskCleanCache extends AsyncTask<Void,Void,Boolean>
    {

        private static final String TAG = "TaskCleanCache";

        @Override
        protected Boolean doInBackground(Void... params)
        {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long availableMemory = 0;
            try
            {
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                {
                    mFreeStorageAndNotifyMethod = getPackageManager().getClass().getMethod("freeStorageAndNotify", long.class, IPackageDataObserver.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        availableMemory = ((long) statFs.getBlockCountLong() * (long) statFs.getBlockSizeLong());
                    } else {
                        availableMemory = ((long) statFs.getBlockCount() * (long) statFs.getBlockSize());
                    }


                    mFreeStorageAndNotifyMethod.invoke(getPackageManager(), availableMemory, new IPackageDataObserver.Stub() {

                        @Override
                        public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {

                        }
                    });
                }
                else
                {
                    PackageManager pm = getPackageManager();
                    Method[] methods = PackageManager.class.getMethods();
                    for (Method method : methods) {
                        if ("freeStorageAndNotify".equals(method.getName())) {
                            try {
                                method.invoke(pm, Long.MAX_VALUE, // Integer.MAX_VALUE,
                                        new IPackageDataObserver.Stub() {

                                            public void onRemoveCompleted(
                                                    String packageName, boolean succeeded)
                                                    throws RemoteException {

                                                System.out.println(succeeded);

                                            }
                                        });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    /*for (String path :pathList)
                    {
                        File file = new File(path);
                        dirDelete(file);
                    }*/
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    if (isExternalStorageWritable()) {
                        final File externalDataDirectory = new File(Environment
                                .getExternalStorageDirectory().getAbsolutePath() + "/Android/data");

                        final String externalCachePath = externalDataDirectory.getAbsolutePath() +
                                "/%s/cache";

                        if (externalDataDirectory.isDirectory()) {

                            final File[] files = externalDataDirectory.listFiles();

                            for (File file : files) {

                                if (!deleteDirectory(new File(String.format(externalCachePath,
                                        file.getName())), true)) {
                                    Log.e(TAG, "External storage suddenly becomes unavailable");

                                    return false;
                                }
                            }
                        } else {
                            Log.e(TAG, "External data directory is not a directory!");
                        }
                    } else {
                        Log.d(TAG, "External storage is unavailable");
                    }
                }

            }
            catch (Exception e)
            {

            }
            return true;
        }
    }

    private boolean deleteDirectory(File file, boolean directoryOnly) {
        if (!isExternalStorageWritable()) {
            return false;
        }

        if (file == null || !file.exists() || (directoryOnly && !file.isDirectory())) {
            return true;
        }

        if (file.isDirectory()) {
            final File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    if (!deleteDirectory(child, false)) {
                        return false;
                    }
                }
            }
        }

        file.delete();

        return true;
    }

    private boolean isExternalStorageWritable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    private boolean checkMultiplePermissions()
    {
//        int getPackageSizePermission = ContextCompat.checkSelfPermission(context,Manifest.permission.GET_PACKAGE_SIZE);
//        int clearAppCachePermission = ContextCompat.checkSelfPermission(context,Manifest.permission.CLEAR_APP_CACHE);
        int readPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
//        int wirtePermission = ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        List<String> permissionList = new ArrayList<>();

        /*if (getPackageSizePermission != PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.GET_PACKAGE_SIZE);
        }

        if (clearAppCachePermission != PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.CLEAR_APP_CACHE);
        }*/

        if (readPermission != PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        /*if (wirtePermission != PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }*/

        if (!permissionList.isEmpty())
        {
            ActivityCompat.requestPermissions(this,permissionList.toArray(new String[permissionList.size()]), RUNTIME_PERMISSION_REQUEST_CODE);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        switch (requestCode)
        {
            case RUNTIME_PERMISSION_REQUEST_CODE:
                if( grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED )
                {
                    Toast.makeText(context,"Now You Got Permission To Work With This Application",Toast.LENGTH_LONG).show();
                    new TaskScanCache().execute();
                }
                else
                {
                    Toast.makeText(context,"Need Permission To Work With This Application",Toast.LENGTH_LONG).show();
                }
                break;

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }


    }



}
