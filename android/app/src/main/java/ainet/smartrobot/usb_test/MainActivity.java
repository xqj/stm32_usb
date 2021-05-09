package ainet.smartrobot.usb_test;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button btnSend;
    private TextView textView;
    //USB管理器:负责管理USB设备的类
    private UsbManager manager;
    //stm32的USB设备
    private UsbDevice stm32Device;
    //stm32设备的一个接口
    private UsbInterface stm32Interface;
    private UsbDeviceConnection stm32DeviceConnection;
    //代表一个接口的某个节点的类:写数据节点
    private UsbEndpoint usbEpOut;
    //代表一个接口的某个节点的类:读数据节点
    private UsbEndpoint usbEpIn;
    //要发送信息字节
    private byte[] sendBytes;
    //接收到的信息字节
    private byte[] receiveBytes;
    //发送线程池
    ExecutorService sendPool = Executors.newCachedThreadPool();
     UsbSendThread usbSendThread=new UsbSendThread();
    UsbRecevieThread usbRecevieThread=new UsbRecevieThread();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        btnSend = findViewById(R.id.btn_send);
        ButtonClick buttonClick = new ButtonClick();
        btnSend.setOnClickListener(buttonClick);
        // 获取USB设备
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //获取到设备列表
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        stm32Device = deviceList.get("stm32Usb");
        if (stm32Device != null) {
            showMsg("vid=" + stm32Device.getVendorId() + "---pid=" + stm32Device.getProductId());
        }
        //获取设备接口
        // 一般来说一个设备都是一个接口，你可以通过getInterfaceCount()查看接口的个数
        // 这个接口上有两个端点，分别对应OUT 和 IN
        stm32Interface = stm32Device.getInterface(0);
        //用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
        if (stm32Interface.getEndpoint(1) != null) {
            usbEpOut = stm32Interface.getEndpoint(1);
        }
        if (stm32Interface.getEndpoint(0) != null) {
            usbEpIn = stm32Interface.getEndpoint(0);
        }
        if (stm32Interface != null) {
            // 判断是否有权限
            if (manager.hasPermission(stm32Device)) {
                // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
                stm32DeviceConnection = manager.openDevice(stm32Device);
                if (stm32DeviceConnection == null) {
                    return;
                }
                if (stm32DeviceConnection.claimInterface(stm32Interface, true)) {
                    showMsg("设备已找到");
                } else {
                    stm32DeviceConnection.close();
                }
            } else {
                showMsg("无权限");
            }
        } else {
            showMsg("未找到设备");
        }
        BroadcastReceiver usbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        stm32DeviceConnection.releaseInterface(stm32Interface);
                        stm32DeviceConnection.close();
                    }
                }
            }
        };
        sendPool.execute(usbRecevieThread);
    }

    private void sendToUsb(String content) {
        sendBytes = content.getBytes();
        int ret = -1;
        // 发送准备命令
        ret = stm32DeviceConnection.bulkTransfer(usbEpOut, sendBytes, sendBytes.length, 5000);
        showMsg("指令已经发送");
    }

    private String readFromUsb() {
        String  str=null;
        //读取数据2
        int inMax = usbEpIn.getMaxPacketSize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(inMax);
        UsbRequest usbRequest = new UsbRequest();
        usbRequest.initialize(stm32DeviceConnection, usbEpIn);
        usbRequest.queue(byteBuffer, inMax);
        if (stm32DeviceConnection.requestWait() == usbRequest) {
            byte[] retData = byteBuffer.array();
            try {
                 str = new String(retData, "UTF-8");
                showMsg("收到数据:" + str);
            } catch (UnsupportedEncodingException e) {
                str=null;
                e.printStackTrace();
            }
        }
        return  str;
    }

    public void showMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.e("stm32Usb：-->>", msg);
    }

    class ButtonClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            int id = view.getId();
            String temp = null;
            switch (id) {
                case R.id.btn_send:
                    usbSendThread.setMsg("usb data test");
                    sendPool.execute(usbSendThread);
                    break;
                default:
                    break;

            }

        }
    }

    class UsbSendThread extends Thread {
        private String c_v_msg;

        public UsbSendThread() {
        }

        public void setMsg(String msg) {
            c_v_msg = msg;
        }

        @Override
        public void run() {
            sendToUsb(c_v_msg);
            c_v_msg="";
        }
    }
    class UsbRecevieThread extends Thread {
        private String c_v_msg;

        public UsbRecevieThread() {
        }

        public String getMsg() {
           return c_v_msg;
        }

        @Override
        public void run() {
            while (true) {
                c_v_msg = null;
                c_v_msg = readFromUsb();
                if(c_v_msg!=null){
                    textView.setText(c_v_msg);
                }
            }
        }
    }
}



