package cn.xender.transfer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import cn.xender.aar.PhonePxConversion;
import cn.xender.aar.QrCodeCreateWorker;
import cn.xender.aar.permission.PermissionUtil;
import cn.xender.aar.views.NougatOpenApDlg;
import cn.xender.core.ap.CoreApManager;
import cn.xender.core.ap.CoreCreateApCallback;
import cn.xender.core.ap.CreateApEvent;
import cn.xender.core.server.utils.ActionListener;
import cn.xender.core.server.utils.ActionProtocol;

public class MyShareActivity extends Activity {
    RelativeLayout container;
    ActionProtocol protocol;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_myshare);

        container = findViewById(R.id.container);

        CoreApManager.getInstance().initApplicationContext(getApplicationContext());

        createAp();

        registerListener();

    }

    //先放一个ProgressBar，生成二维码之后，再移除pb，添加iv
    private void addQrCodeLayoutAndSetQrCode(){

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(QrCodeCreateWorker.getQrBitmap());
        container.removeAllViews();
        container.addView(iv);
    }

    private boolean dialogOut = false;
    /**
     * Rayn
     * 检查权限+开启热点
     * 检查失败弹出dialog
     */
    private void createAp(){
        if(PermissionUtil.checkAllNeededPermission(this)){
            CoreApManager.getInstance().createAp("", "", 30000, 12, new CoreCreateApCallback() {
                @Override
                public void callback(final CreateApEvent result) {
                    /**
                     * Rayn
                     * callback出来的是子线程，而handleCreateResult里面（7.1会让手动开启热点）
                     * 会创建dialog，所以要用handler post到主线程去跑
                     */
                    _handler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleCreateResult(result,true);
                        }
                    });
                }
            });
        }else{
            /**
             * Rayn
             * 6.0 7.0 7.1 才弹出dlg 1
             */
            if (Build.VERSION.SDK_INT < 26 && Build.VERSION.SDK_INT > 22) {
                /**
                 * Rayn
                 * dialogOut开关，只弹一次
                 */
                if (!dialogOut) {
                    dialogOut = true;//弹出之后，就变成true，下次不弹
                    PermissionUtil.showPermissionDlg(this);
                } else {
                    PermissionUtil.requestAllNeededPermission(this);
                }
            } else {
                PermissionUtil.requestAllNeededPermission(this);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bbb && !CoreApManager.getInstance().isApEnabled()) {
            showManualOpenDialog();
        }
    }

    private void handleCreateResult(CreateApEvent result, boolean retryIfNeed) {
        //CREATE_OK = 6
        if(result.isOk() && !TextUtils.isEmpty(result.getUrl())){
            ActionProtocol.sendApCreatedAction(this);
            int qrSize = PhonePxConversion.dip2px(MyShareActivity.this,240);/****二维码大小*****/
            new QrCodeCreateWorker().startWork(MyShareActivity.this,_handler,result.getUrl(),qrSize,qrSize, Color.WHITE,true);
        }else if(result.isNeedUserManualOpen()){//SAVED_25_CONFIG = 3

            showManualOpenDialog();//显示7.1的dlg

        }else if(result.isManualOpenSuccess()){//AP_ENABLED_25 = 4

            dismissManualOpenDialog();//关掉7.1的dlg
            //回到ShareActivity
            NougatOpenApDlg.goBack(this,this.getClass().getName());
        }else if(result.isOpendButWeCannotUseAndNeedRetry()){//CREATE_OK_BUT_NO_IP_ON25 = 5

            if(retryIfNeed){

                CoreApManager.getInstance().retryCreateAp("", "", 30000, 12, new CoreCreateApCallback() {
                    @Override
                    public void callback(final CreateApEvent result) {
                        _handler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleCreateResult(result,false);
                            }
                        });
                    }
                });
            }else{
                //失败
                finish();
            }
        }else if(result.isError()){
            finish();
        }else if(result.isOff()){
            finish();
        }
    }

    private NougatOpenApDlg dlg;
    private boolean bbb = false;

    private void showManualOpenDialog(){
        if(dlg == null){
            dlg = new NougatOpenApDlg(this);
        }
        bbb = true;
        dlg.show();
    }

    private void dismissManualOpenDialog(){
        if(dlg != null){
            dlg.dismiss();
        }

    }

    Handler _handler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if(msg.what == QrCodeCreateWorker.CREATE_SUCCESS_WHAT){

                addQrCodeLayoutAndSetQrCode();

            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        QrCodeCreateWorker.clear();

        CoreApManager.getInstance().shutdownAp();

        protocol.unregister(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            PermissionUtil.showQuitDlg(this);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSIONS:
                createAp();
                break;
            case PermissionUtil.CREATE_AP_WRITE_SETTING_PERMISSIONS://从allow modify settings回来
                dialogOut = false;//如果没有允许，让dialog再弹出
                createAp();
                break;
            case PermissionUtil.ACCESS_COARSE_LOCATION_PERMISSIONS:
                if (resultCode == RESULT_OK) {
                    createAp();
                }
                break;
            case PermissionUtil.ACCESS_GPS_LOCATION_PERMISSIONS:
                createAp();
                break;
            case PermissionUtil.BACK_FROM_SETTING_PERMISSION://从系统设置回来，dialog4开启的系统设置
                createAp();
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Rayn
     * requestCode
     * 每个数字代表一种权限 1是storage 7是location
     * grantResult
     * PackageManager.PERMISSION_GRANTED 是0
     * PackageManager.PERMISSION_DENIED 是-1
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (null == permissions || permissions.length == 0) return;

        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            if (requestCode == PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSIONS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !shouldShowRequestPermissionRationale("android.permission.READ_EXTERNAL_STORAGE")) {
                    ActionProtocol.sendDenyStoragePermissionAndDontAskAction(this);
                    PermissionUtil.showSettingPermissionDlg(this);
                } else {
                    ActionProtocol.sendDenyStoragePermissionAction(this);
                    createAp();
                }
            }

            if (requestCode == PermissionUtil.ACCESS_COARSE_LOCATION_PERMISSIONS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !shouldShowRequestPermissionRationale("android.permission.ACCESS_COARSE_LOCATION")) {
                    ActionProtocol.sendDenyLocationPermissionAndDontAskAction(this);
                    PermissionUtil.showSettingPermissionDlg(this);
                } else {
                    ActionProtocol.sendDenyLocationPermissionAction(this);
                    createAp();
                }
            }
        } else {
            switch (requestCode) {
                case PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSIONS:
                    ActionProtocol.sendStoragePermissionGrantedAction(this);
                    createAp();
                    break;
                case PermissionUtil.ACCESS_COARSE_LOCATION_PERMISSIONS:
                    ActionProtocol.sendLocatioinPermissionGrantedAction(this);
                    createAp();
                    break;
                default:
                    break;
            }
        }
    }

    private void registerListener() {
        protocol = new ActionProtocol();
        protocol.register(this);
        protocol.setActionListener(new ActionListener(){
            @Override
            public void someoneOnline() {
                System.out.println("---Rayn someoneOnline");
            }

            @Override
            public void transferSuccess(String android_id, String channel, String filePath) {
                System.out.println("---Rayn transferSuccess:"+filePath);
            }

            @Override
            public void transferFailure(String filePath) {
                System.out.println("---Rayn transferFailure");
            }

            @Override
            public void transferAll(String android_id, String channel) {
                System.out.println("---Rayn transferAll");
            }

            @Override
            public void grantPermissionSuccess() {
                System.out.println("---Rayn grantPermissionSuccess");
            }

            @Override
            public void qrCodeShowed() {
                System.out.println("---Rayn qrCodeShowed");
            }

            @Override
            public void grantStoragePermission() {
                System.out.println("---Rayn grantStoragePermission");
            }

            @Override
            public void grantLocationPermission() {
                System.out.println("---Rayn grantLocationPermission");
            }

            @Override
            public void denyStoragePermission() {
                System.out.println("---Rayn denyStoragePermission");
            }

            @Override
            public void denyLocationPermission() {
                System.out.println("---Rayn denyLocationPermission");
            }

            @Override
            public void denyStoragePermissionAndDontAsk() {
                System.out.println("---Rayn denyStoragePermissionAndDontAsk");
            }

            @Override
            public void denyLocationPermissionAndDontAsk() {
                System.out.println("---Rayn denyLocationPermissionAndDontAsk");
            }

            @Override
            public void dlg_1_yes() {
                System.out.println("---Rayn dlg_1_yes");
            }

            @Override
            public void dlg_2_yes() {
                System.out.println("---Rayn dlg_2_yes");
            }

            @Override
            public void dlg_3_yes() {
                System.out.println("---Rayn dlg_3_yes");
            }

            @Override
            public void dlg_4_yes() {
                System.out.println("---Rayn dlg_4_yes");
            }

            @Override
            public void dlg_1_no() {
                System.out.println("---Rayn dlg_1_no");
            }

            @Override
            public void dlg_2_no() {
                System.out.println("---Rayn dlg_2_no");
            }

            @Override
            public void dlg_3_no() {
                System.out.println("---Rayn dlg_3_no");
            }

            @Override
            public void dlg_4_no() {
                System.out.println("---Rayn dlg_4_no");
            }
        });
    }


}
