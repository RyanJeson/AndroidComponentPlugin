package com.malin.hook;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MApplication extends Application {

    private static final String TAG = "MApplication";
    private static MApplication mApplication;

    /**
     * 为了重置,否则在HookAMS的情况下第二次之后的启动都是已经注册的Activity
     */
    private static Object mIActivityManagerObj;
    private static Object msPackageManager;
    private static Object mmPM;


    /**
     * 是否是Hook Instrumentation
     * true:Hook Instrumentation
     * false:Hook AMS and PMS
     */
    private final boolean mHookInstrumentation = true;

    /**
     * Hook Instrumentation的方式下,是否启动appcompatActivity类型的Activity.
     */
    private final boolean mHookInstrumentation_is_appcompatActivity = true;

    private ExecutorService mSingleThreadExecutor = Executors.newSingleThreadExecutor();
    private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        unseal();
        init();
        startStrictMode();
        getPM(context);
        handleService(context);
        handleActivity(context);
        installActivity();
        handleContentProvider(context);
        hookClipboard();
    }

    private void hookClipboard() {
        try {
            BinderHookHelper.hookClipboardService();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void handleContentProvider(final Context context) {
        Runnable providerRunnable = new Runnable() {
            @Override
            public void run() {
                final File apkFile = getFileStreamPath(PluginApkNameVersion.PLUGIN_PROVIDER_APK);
                final File dexFile = getFileStreamPath(PluginApkNameVersion.PLUGIN_PROVIDER_DEX);
                if (!apkFile.exists()) {
                    Log.e(TAG, "extractAssets");
                    if (Build.VERSION.SDK_INT >= 18) {
                        PluginUtils.extractAssets(context, PluginApkNameVersion.PLUGIN_PROVIDER_APK);
                        BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, dexFile);
                        ProviderHelper.installProviders(context, apkFile);
                    } else {
                        //15=<Build.VERSION.SDK_INT<=17
                        //static final ThreadLocal<ActivityThread> sThreadLocal = new ThreadLocal<ActivityThread>();
                        //public static ActivityThread currentActivityThread() {return sThreadLocal.get();}
                        //currentActivityThread()方法需要在UI线程中调用
                        PluginUtils.extractAssets(context, PluginApkNameVersion.PLUGIN_PROVIDER_APK, new PluginUtils.CopyCallback() {
                            @Override
                            public void onSuccess() {
                                BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, dexFile);
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        ProviderHelper.installProviders(context, apkFile);
                                    }
                                });
                            }

                            @Override
                            public void onFail() {

                            }
                        });
                    }
                } else {
                    Log.e(TAG, "extractAssets not!!");
                    if (Build.VERSION.SDK_INT >= 18) {
                        BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, dexFile);
                        ProviderHelper.installProviders(context, apkFile);
                    } else {
                        BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, dexFile);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                ProviderHelper.installProviders(context, apkFile);
                            }
                        });
                    }
                }
            }
        };
        mSingleThreadExecutor.execute(providerRunnable);
    }

    private void init() {
        mApplication = this;
    }

    private void unseal() {
        int reflection = Reflection.unseal();
        Log.d(TAG, reflection == 0 ? "hide api 解除成功" : "hide api 解除失败");
    }

    private void handleActivity(Context context) {
        if (mHookInstrumentation) {
            if (mHookInstrumentation_is_appcompatActivity) {
                HookInstrumentation.hookInstrumentation(context, StubAppCompatActivity.class.getCanonicalName());
            } else {
                HookInstrumentation.hookInstrumentation(context, StubActivity.class.getCanonicalName());
            }
        } else {
            mIActivityManagerObj = HookAMS.getIActivityManagerObj();
        }
    }

    private void installActivity() {
        Runnable patchClassLoaderRunnable = new Runnable() {
            @Override
            public void run() {
                PluginUtils.extractAssets(MApplication.getInstance(), PluginApkNameVersion.PLUGIN_ACTIVITY_APK);
                File dexFile = getFileStreamPath(PluginApkNameVersion.PLUGIN_ACTIVITY_APK);
                File optDexFile = getFileStreamPath(PluginApkNameVersion.PLUGIN_ACTIVITY_DEX);
                BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), dexFile, optDexFile);
            }
        };
        mSingleThreadExecutor.execute(patchClassLoaderRunnable);
    }

    private void getPM(Context context) {
        mmPM = HookPMS.getApplicationPackageManager(context);
        msPackageManager = HookPMS.getPackageManager();
    }


    /**
     * 安装service插件,处理service插件
     */
    private void handleService(final Context context) {
        try {
            HookAMSForServicePlugin.hookActivityManagerNative();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        Runnable providerRunnable = new Runnable() {
            @Override
            public void run() {
                final File apkFile = getFileStreamPath(PluginApkNameVersion.PLUGIN_SERVICE_APK);
                final File odexFile = getFileStreamPath(PluginApkNameVersion.PLUGIN_SERVICE_DEX);
                if (!apkFile.exists()) {
                    Log.e(TAG, "pluginService extractAssets");
                    if (Build.VERSION.SDK_INT >= 18) {
                        PluginUtils.extractAssets(context, PluginApkNameVersion.PLUGIN_SERVICE_APK);
                        BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, odexFile);
                        try {
                            ServiceManager.getInstance().preLoadServices(apkFile);
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    } else {
                        //15=<Build.VERSION.SDK_INT<=17
                        //static final ThreadLocal<ActivityThread> sThreadLocal = new ThreadLocal<ActivityThread>();
                        //public static ActivityThread currentActivityThread() {return sThreadLocal.get();}
                        //currentActivityThread()方法需要在UI线程中调用
                        PluginUtils.extractAssets(context, PluginApkNameVersion.PLUGIN_SERVICE_APK, new PluginUtils.CopyCallback() {
                            @Override
                            public void onSuccess() {
                                BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, odexFile);
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            ServiceManager.getInstance().preLoadServices(apkFile);
                                        } catch (Throwable throwable) {
                                            throwable.printStackTrace();
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onFail() {

                            }
                        });
                    }
                } else {
                    Log.e(TAG, "pluginService extractAssets not!!");
                    if (Build.VERSION.SDK_INT >= 18) {
                        BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, odexFile);
                        try {
                            ServiceManager.getInstance().preLoadServices(apkFile);
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    } else {
                        BaseDexClassLoaderHookHelper.patchClassLoader(getClassLoader(), apkFile, odexFile);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    ServiceManager.getInstance().preLoadServices(apkFile);
                                } catch (Throwable throwable) {
                                    throwable.printStackTrace();
                                }
                            }
                        });
                    }
                }
            }
        };
        mSingleThreadExecutor.execute(providerRunnable);
    }


    public static void resetAms() {
        if (mIActivityManagerObj == null) return;
        HookAMS.resetIActivityManager(mIActivityManagerObj);
    }

    public static void resetPms() {
        if (mmPM == null) return;
        HookPMS.resetApplicationPackageManager(getInstance(), mmPM);
        if (msPackageManager == null) return;
        HookPMS.resetPackageManager(msPackageManager);
    }

    public boolean isHookInstrumentation() {
        return mHookInstrumentation;
    }

    public boolean isHookInstrumentationIsAppCompatActivity() {
        return mHookInstrumentation_is_appcompatActivity;
    }


    public static MApplication getInstance() {
        return mApplication;
    }

    private void startStrictMode() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT <= 15) return;
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyDialog()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyDeath()
                .penaltyLog()
                .build());
    }
}
