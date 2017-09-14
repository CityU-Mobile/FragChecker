package org.cityu.mbos.fragchecker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;


import org.cityu.mbos.fragchecker.conf.PublicParams;
import org.cityu.mbos.fragchecker.datastruce.Ext4StatInfo;
import org.cityu.mbos.fragchecker.listener.E4DefragListener;
import org.cityu.mbos.fragchecker.service.InstallE4DefragService;
import org.cityu.mbos.fragchecker.service.InstallFindService;
import org.cityu.mbos.fragchecker.service.InstallXargsService;
import org.cityu.mbos.fragchecker.service.MkdirService;
import org.cityu.mbos.fragchecker.utils.ExceptionTool;
import org.cityu.mbos.fragchecker.utils.Logger;
import org.cityu.mbos.fragchecker.utils.TypeClassifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_CODE = 1001;

    private TextView textView;

    private Button e4defragButton;

    private PieChart pieChart;

    private BarChart barChart;

    private BarChart averageBarChart;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what == PublicParams.UPDATE_TEXTVIEW){
                String content = (String) msg.obj;
                textView.setText(content);
            }
            if(msg.what == PublicParams.UPDATE_PIECHART){
                Ext4StatInfo ext4StatInfo = (Ext4StatInfo) msg.obj;
                ext4StatInfo.getDensityMap().remove("total");
                setPieChartData(pieChart, ext4StatInfo.getDensityMap());
                setBarChartData(barChart, ext4StatInfo.getDofMap());
                setBarChartData2(averageBarChart, ext4StatInfo.getaDofMap());
                textView.setText("usage = " + String.valueOf(ext4StatInfo.getUsage()));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initPermissionsAndGetUniqueUserID(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE);

        textView = (TextView) findViewById(R.id.loginfo);
        e4defragButton = (Button) findViewById(R.id.button_e4defrage);
        pieChart = (PieChart) findViewById(R.id.pie_chart_1);
        barChart = (BarChart) findViewById(R.id.barchart);
        averageBarChart = (BarChart) findViewById(R.id.barchart2);

        initPieChart(pieChart);
        initBarChart(barChart);
        initBarChart(averageBarChart);

        // install some necessary commands, ex. mkdir e4defrag,
        startService(new Intent(this, MkdirService.class));
        startService(new Intent(this, InstallE4DefragService.class));
        startService(new Intent(this, InstallFindService.class));
        startService(new Intent(this, InstallXargsService.class));

        e4defragButton.setOnClickListener(new E4DefragListener(handler, this));

    }

    private void initPermissionsAndGetUniqueUserID(String ... permissions){

        ArrayList<String> permissionList = new ArrayList<>();

        for (String p : permissions) {
            if(ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED){
                Logger.info("apply permissions : " + p);
                permissionList.add(p);
            }
        }

        String[] permissionSet = permissionList.toArray(new String[permissionList.size()]);

        if(permissionSet.length > 0){
            ActivityCompat.requestPermissions(this, permissionSet, PERMISSIONS_CODE);
        }else {
            //初始化唯一id号
            TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
            try {

                PublicParams.UNIQUEID = telephonyManager.getDeviceId(); // define a unique id for every user
                if(PublicParams.UNIQUEID == null || PublicParams.UNIQUEID.equals("")){
                    PublicParams.UNIQUEID = telephonyManager.getLine1Number(); // if we can not get the device id , try to get phone numbers
                }

                PublicParams.UNIQUEID = android.os.Build.MODEL + "-" + android.os.Build.VERSION.RELEASE + "-" + PublicParams.UNIQUEID.substring(1,5);
                Logger.info("UNIQUEID = " + PublicParams.UNIQUEID);

            }catch (Exception e){
                Logger.error(ExceptionTool.getExceptionStacksMessage(e));
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean isWritable = false;
        boolean isReadable = false;

        if(requestCode == PERMISSIONS_CODE){

            for (int i = 0; i < permissions.length; i++) {

                Logger.info("permission = " + permissions[i] + ", ret = " + grantResults[i]);

                if(permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    isWritable = true;
                }

                if(permissions[i].equals(Manifest.permission.READ_PHONE_STATE) && grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    isReadable = true;
                }

                if(isWritable && isReadable){

                    TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
                    try {

                        PublicParams.UNIQUEID = telephonyManager.getDeviceId();
                        if(PublicParams.UNIQUEID == null || PublicParams.UNIQUEID.equals("")){
                            PublicParams.UNIQUEID = telephonyManager.getLine1Number();
                        }

                        Logger.info("UNIQUEID = " + PublicParams.UNIQUEID);

                    }catch (Exception e){
                        Logger.error(ExceptionTool.getExceptionStacksMessage(e));
                    }
                }

            }


        }


    }

    private void initPieChart(PieChart mPieChart){

        // configure the piechart settings
        mPieChart.setUsePercentValues(true);
        mPieChart.setDescription("");
        mPieChart.setExtraOffsets(5, 10, 5, 5);
        mPieChart.setDragDecelerationFrictionCoef(0.95f);
        mPieChart.setCenterText("");
        mPieChart.setDrawHoleEnabled(true);
        mPieChart.setHoleColorTransparent(true);
        mPieChart.setTransparentCircleColor(Color.WHITE);
        mPieChart.setTransparentCircleAlpha(110);
        mPieChart.setHoleRadius(58f);
        mPieChart.setTransparentCircleRadius(61f);
        mPieChart.setDrawCenterText(true);
        mPieChart.setRotationAngle(0);
        mPieChart.setRotationEnabled(true);
        mPieChart.setHighlightPerTapEnabled(false);
        mPieChart.animateY(1400, Easing.EasingOption.EaseInOutQuad);
        Legend l = mPieChart.getLegend();
        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART);
        l.setXEntrySpace(7f);
        l.setYEntrySpace(0f);
        l.setYOffset(0f);

    }

    public void setPieChartData(PieChart mPieChart , TreeMap<String, Float> data) {

        ArrayList<String> xVals = new ArrayList<String>();
        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        int i = 0;
        Iterator it = data.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            float value = (float) entry.getValue();
            xVals.add(key);
            yVals1.add(new Entry(value, i++));
        }

        PieDataSet dataSet = new PieDataSet(yVals1, "");
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(5f);

        ArrayList<Integer> colors = new ArrayList<Integer>();
        for (int c : ColorTemplate.VORDIPLOM_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.JOYFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.COLORFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.LIBERTY_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.PASTEL_COLORS)
            colors.add(c);
        colors.add(ColorTemplate.getHoloBlue());
        dataSet.setColors(colors);

        PieData data1 = new PieData(xVals, dataSet);
        data1.setValueFormatter(new PercentFormatter());
        data1.setValueTextSize(10f);
        data1.setValueTextColor(Color.BLACK);
        mPieChart.setData(data1);
        mPieChart.highlightValues(null);
        mPieChart.invalidate();
    }

    private void initBarChart(BarChart mBarChart){

        mBarChart.setDrawValueAboveBar(true);
        mBarChart.setTouchEnabled(true);
        mBarChart.setDragEnabled(true);
        mBarChart.setScaleEnabled(true);
        mBarChart.setPinchZoom(true);
        mBarChart.setDrawBarShadow(false);
        mBarChart.setDrawValueAboveBar(true);
        mBarChart.setMaxVisibleValueCount(60);
        mBarChart.setDrawGridBackground(false);
        mBarChart.setDescription("");
        mBarChart.getXAxis().setDrawGridLines(false);
        mBarChart.getXAxis().setDrawLabels(true);
        mBarChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        mBarChart.animateXY(2000,3000);

    }

    private void setBarChartData(BarChart barChart, TreeMap<Integer, Long> dofMap) {

        ArrayList<String> xVals = new ArrayList<String>();
        ArrayList<BarEntry> yVals = new ArrayList<BarEntry>();

        Set<Integer> keySet = dofMap.keySet();

        int index = 0;
        for (Integer key : keySet){

            if(key == 3999){
                xVals.add(index, "total files"); // transfer magic number to a meaningful name
                yVals.add(new BarEntry(dofMap.get(key), index));
                index++;
            }else {
                xVals.add(index, "dof-" + key);
                yVals.add(new BarEntry(dofMap.get(key), index));
                index++;
            }


        }

        BarDataSet set1 = new BarDataSet(yVals, "different dof");
        set1.setBarSpacePercent(10f);

        ArrayList<Integer> colors = new ArrayList<Integer>();
        for (int c : ColorTemplate.VORDIPLOM_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.JOYFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.COLORFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.LIBERTY_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.PASTEL_COLORS)
            colors.add(c);
        colors.add(ColorTemplate.getHoloBlue());
        set1.setColors(colors);

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);
        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);
        data.setValueTypeface(Typeface.DEFAULT);

        barChart.setData(data);
        barChart.invalidate();

    }

    private void setBarChartData2(BarChart barChart, TreeMap<Integer, Float> dofMap) {

        ArrayList<String> xVals = new ArrayList<String>();
        ArrayList<BarEntry> yVals = new ArrayList<BarEntry>();

        Set<Integer> keySet = dofMap.keySet();

        int index = 0;
        for (Integer key : keySet){

            xVals.add(index, String.valueOf(TypeClassifier.FileType.getNameById(key)));
            yVals.add(new BarEntry(dofMap.get(key), index));
            index++;

        }

        BarDataSet set1 = new BarDataSet(yVals, "different types of files");
        set1.setBarSpacePercent(10f);

        ArrayList<Integer> colors = new ArrayList<Integer>();
        for (int c : ColorTemplate.VORDIPLOM_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.JOYFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.COLORFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.LIBERTY_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.PASTEL_COLORS)
            colors.add(c);
        colors.add(ColorTemplate.getHoloBlue());
        set1.setColors(colors);

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);
        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);
        data.setValueTypeface(Typeface.DEFAULT);

        barChart.setData(data);
        barChart.invalidate();

    }


}

