package com.amap.agenuidemo.commonui;

import android.content.Context;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.IComponentFactory;

import java.util.Map;

public class MeloTextComponentFactory implements IComponentFactory {

    @Override
    public A2UIComponent createComponent(Context context, String id, Map<String, Object> properties) {
        return new MeloTextComponent(context, id, properties);
    }

    @Override
    public String getComponentType() {
        return "Text";
    }
}
