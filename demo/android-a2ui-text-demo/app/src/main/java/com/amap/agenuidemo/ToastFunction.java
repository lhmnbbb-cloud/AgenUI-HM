package com.amap.agenuidemo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.amap.agenui.function.FunctionConfig;
import com.amap.agenui.function.FunctionResult;
import com.amap.agenui.function.IFunction;

import org.json.JSONObject;

public class ToastFunction implements IFunction {

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public ToastFunction(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public FunctionResult execute(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            String toastString = json.optString("value", json.optString("message", "Toast triggered"));
            handler.post(() -> Toast.makeText(context, toastString, Toast.LENGTH_LONG).show());
            return FunctionResult.createSuccess(null);
        } catch (Exception e) {
            return FunctionResult.createError("ToastFunction error: " + e.getMessage());
        }
    }

    @Override
    public FunctionConfig getConfig() {
        return new FunctionConfig("toast");
    }
}
