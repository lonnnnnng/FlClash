package com.lonnnnnng.flclash.ui

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.sqrt

/** 作者 long：固定Prism的局部光学方程使用四个GPU采样槽；只缓存相同参数effect，触摸不能触发重新抓背景。 */
@androidx.annotation.RequiresApi(33)
internal class LivePaneOptics {
    private val shader = android.graphics.RuntimeShader(LIVE_GLASS_SHADER)
    private val blurs = linkedMapOf<Float, RenderEffect>()
    private var last: List<Any>? = null
    private var cached: RenderEffect? = null
    fun blur(sigma: Float): RenderEffect? {
        if (sigma <= .01f) return null
        blurs[sigma]?.let { return it }
        // 作者 long：镜片强度每帧产生不同sigma，只保留最近四档，避免长时间拖动累积原生RenderEffect。
        if (blurs.size >= 4) blurs.remove(blurs.keys.first())
        return android.graphics.RenderEffect.createBlurEffect(sigma,sigma,android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect().also { blurs[sigma]=it }
    }
    fun effect(width: Float, height: Float, tileWidth: Float, padding: Float, radius: Float, unit: Float,
        style: AppGlassStyle, light: AppTouchLight?, dock: Boolean, superellipse: Boolean): RenderEffect {
        val amount = light?.amount?.coerceIn(0f,1f) ?: 0f
        val strength = if (dock) .06f*amount*amount else .16f*amount
        val reach = (if (dock) 28f*unit else height/sqrt(2f)) * (1f+3f*(light?.spreadAmount ?: 0f))
        val point = light?.point ?: androidx.compose.ui.geometry.Offset(width/2,height/2)
        val key = listOf(width,height,tileWidth,padding,radius,unit,style,strength,reach,point,dock,superellipse)
        if (key==last) return checkNotNull(cached)
        shader.setFloatUniform("atlasInfo",tileWidth,padding,height+padding*2,if(superellipse) 1f else 0f)
        shader.setFloatUniform("box",width/2,height/2,width/2,height/2)
        shader.setFloatUniform("drop",width/2,height/2,width/2,height/2)
        shader.setFloatUniform("geometry",radius,0f,unit,0f)
        shader.setFloatUniform("optics",style.refraction,style.saturation,style.brightness,style.contrast)
        shader.setFloatUniform("edgeStyle",style.bevel,style.dispersion,style.haze,style.endShade)
        shader.setFloatUniform("rimStyle",style.rim,style.rimWidth,style.rimTail,style.rimFocus)
        shader.setFloatUniform("lineStyle",style.rimAdded,style.hairline,style.hairlineSide,0f)
        shader.setFloatUniform("tint",style.tint.red,style.tint.green,style.tint.blue,style.tint.alpha)
        val x=appGlassCurve(width/2,radius);val y=appGlassCurve(height/2,radius)
        shader.setFloatUniform("curveX",x.copyOfRange(0,4));shader.setFloatUniform("curveY",y.copyOfRange(0,4))
        shader.setFloatUniform("centers",x[4],x[5],y[4],y[5])
        shader.setFloatUniform("pillCurve",x.copyOfRange(0,4));shader.setFloatUniform("pillCenter",x[4],x[5])
        shader.setFloatUniform("touchGlow",point.x,point.y,strength,0f)
        shader.setFloatUniform("touchSpread",reach,if(dock) .7f else 0f)
        val result=android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
        last=key;cached=result;return result
    }
}

// 作者 long：正负折射、镜像轴色散、定向边线与高光来自固定上游；不是用矩形pow8轮廓或统一白描边近似。
internal const val LIVE_GLASS_SHADER = """

uniform shader content;
uniform float4 atlasInfo;
uniform float4 touchGlow;
uniform float2 touchSpread;
uniform float4 box;
uniform float4 drop;
uniform float4 geometry;
uniform float4 tint;
uniform float4 curveX;
uniform float4 curveY;
uniform float4 centers;
uniform float4 optics;
uniform float4 edgeStyle;
uniform float4 rimStyle;
uniform float4 lineStyle;
uniform float4 pillCurve;
uniform float2 pillCenter;
const float3 luma = float3(.2126,.7152,.0722);
float distanceBox(float2 p,float2 halfSize,float radius) {
    float2 q=abs(p)-halfSize+radius;
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float2 normalBox(float2 p,float2 halfSize,float radius) {
    float2 q=abs(p)-halfSize+radius;
    float2 n=q.x>0.0&&q.y>0.0?normalize(q):(q.x>q.y?float2(1,0):float2(0,1));
    return n*float2(p.x<0.0?-1:1,p.y<0.0?-1:1);
}
float octant(float2 p,float a,float4 curve,float2 center) {
    float2 v=p-center,j=curve.yz-center;
    if(j.x*v.y<j.y*v.x) return curve.w-length(v);
    float2 s=max(p,0.0)/max(a,.001);
    float major=max(max(s.x,s.y),.0001);
    float norm=major*pow(1.0+pow(min(s.x,s.y)/major,curve.x),1.0/curve.x);
    return a*(1.0-norm)/max(length(pow(s/max(norm,.0001),float2(curve.x-1.0))),.0001);
}
float insetCurve(float2 p) {
    if(curveX.x<2.0) return min(box.z-abs(p.x),box.w-abs(p.y));
    float2 q=abs(p); float c=box.z-box.w;
    return q.y+c>=q.x?octant(float2(q.x,q.y+c),box.z,curveX,centers.xy):octant(float2(q.y,q.x-c),box.w,curveY,centers.zw);
}
half4 tap(float2 p, float slot) {
    float2 at=clamp(p+float2(atlasInfo.y),float2(.5),float2(atlasInfo.x-.5,atlasInfo.z-.5));
    return content.eval(at+float2(slot*atlasInfo.x,0));
}
half4 sampleRaw(float2 p) { return tap(p,0.0); }
half4 sampleFrost(float2 p) { return tap(p,1.0); }
half4 sampleBehind(float2 p) { return tap(p,edgeStyle.z>0.0?3.0:1.0); }
half4 sampleMaterial(float2 p,float rise) {
    half4 base=mix(tap(p,1.0),tap(p,2.0),rise*rise);
    return mix(base,tap(p,3.0),edgeStyle.z);
}
float band(float a,float b,float start,float end,float lit) { return max(min(b,end)-max(a,start),0.0)*lit; }
float shine(float edge,float unit) {
    float a=max(edge/unit-.5/unit,0.0), b=edge/unit+.5/unit;
    return (band(a,b,0.0,1.0/3.0,1.0)+band(a,b,1.0/3.0,2.0/3.0,.7)+band(a,b,2.0/3.0,1.0,.47)
        +band(a,b,1.0,5.0/3.0,.18)+band(a,b,5.0/3.0,7.0/3.0,.12)+band(a,b,7.0/3.0,3.0,.085)
        +band(a,b,3.0,11.0/3.0,.065)+band(a,b,11.0/3.0,13.0/3.0,.045)+band(a,b,13.0/3.0,5.0,.035))/max(b-a,.0001);
}
float around(float turn,float radius) {
    float2 h=box.zw;
    float t0=atan(max(h.x-min(1.53*radius,h.x),0.0),max(h.y,.0001));
    float t1=max(t0,atan(max(h.x-min(radius,h.x),0.0),max(h.y,.0001)));
    float t2=max(t1,atan(max(h.x-min(.3*radius,h.x),0.0),max(h.y-min(.3*radius,h.y),.0001)));
    float t3=max(t2,atan(h.x,max(h.y-min(radius,h.y),.0001)));
    float t4=max(t3,atan(h.x,max(h.y-min(1.5*radius,h.y),.0001)));
    float t5=max(t4,atan(h.x,max(h.y-min(2.0*radius,h.y),.0001)));
    return 1.0-.32*clamp(turn/max(t0,.00001),0.0,1.0)-.20*clamp((turn-t0)/max(t1-t0,.00001),0.0,1.0)
       -.10*clamp((turn-t1)/max(t2-t1,.00001),0.0,1.0)-.21*clamp((turn-t2)/max(t3-t2,.00001),0.0,1.0)
       -.10*clamp((turn-t3)/max(t4-t3,.00001),0.0,1.0)-.04*clamp((turn-t4)/max(t5-t4,.00001),0.0,1.0)
       -.03*clamp((turn-t5)/max(1.5707963-t5,.00001),0.0,1.0);
}
half4 main(float2 pixel) {
    float2 p=pixel-float2(atlasInfo.y);
    if(p.x<0.0||p.y<0.0||p.x>box.z*2.0||p.y>box.w*2.0) return half4(0);

    float unit=geometry.z, radius=min(geometry.x,min(box.z,box.w));
    float2 centered=p-box.xy;
    float d=distanceBox(centered,box.zw,radius), edge=atlasInfo.w>0.0?insetCurve(centered):-d;
    float2 normal=normalBox(centered,box.zw,radius);
    if(geometry.y>0.0) {
        float other=distanceBox(p-drop.xy,drop.zw,min(drop.z,drop.w));
        float nearest=min(d,other), lost=max(-d-edge,0.0);
        if(other<d) {
            float2 q=abs(p-drop.xy), h=drop.zw;
            if(h.x<h.y) { q=q.yx; h=h.yx; }
            float c=h.x-h.y;
            float pill=q.y+c<q.x?h.y-length(q-float2(c,0)):octant(float2(q.x,q.y+c),h.x,pillCurve,pillCenter);
            lost=max(-other-pill,0.0);
        }
        float h=clamp(.5+.5*(other-d)/geometry.y,0.0,1.0);
        d=mix(other,d,h)-geometry.y*h*(1.0-h);
        normal=mix(normalBox(p-drop.xy,drop.zw,min(drop.z,drop.w)),normal,h);
        normal=normal/max(length(normal),.0001);
        edge=max(-d-max(lost-(nearest-d),0.0),0.0);
    }
    float inset=max(-d,0.0), minor=min(box.z,box.w);
    float bevel=max(min(edgeStyle.x*unit,minor*.5),.001);
    float rise=1.0-clamp(inset/bevel,0.0,1.0);
    float2 shift;
    if(optics.x<0.0) shift=normal*(-optics.x*unit)*smoothstep(0.0,bevel,inset)*(1.0-smoothstep(bevel,max(minor,bevel+.001),inset));
    else shift=-normal*min(optics.x*unit,minor)*(1.0-sqrt(max(1.0-rise*rise,0.0)));
    half4 base=sampleMaterial(p+shift,rise);
    if(edgeStyle.y>0.0 && inset<10.0*unit) {
        float2 part=normal.yx*.5*edgeStyle.y*unit*(.6+.4*abs(normal.y)*max(1.0-inset/(7.0*unit),0.0))*(1.0-smoothstep(7.0*unit,10.0*unit,inset));
        half4 red=sampleMaterial(p+shift+part,rise), blue=sampleMaterial(p+shift-part,rise);
        base=half4(red.r,base.g,blue.b,max(base.a,max(red.a,blue.a)));
    }
    float3 color=base.rgb/max(float(base.a),.0001);
    color=mix(float3(dot(color,luma)),color,optics.y)*optics.z;
    color*=1.0-edgeStyle.w*abs(normal.x)*exp(-inset/(3.0*unit));
    float contrast=optics.w;
    half4 behind=(sampleBehind(box.xy)+sampleBehind(box.xy+box.zw*float2(-.6,-.4))+sampleBehind(box.xy+box.zw*float2(.6,-.4))
        +sampleBehind(box.xy+box.zw*float2(-.6,.4))+sampleBehind(box.xy+box.zw*float2(.6,.4)))*.2;
    float clash=clamp((sign(contrast)*(dot(tint.rgb,luma)-dot(behind.rgb/max(float(behind.a),.0001),luma))-.3)/.5,0.0,1.0);
    float cover=tint.a+(1.0-tint.a)*clash*abs(contrast);
    float alpha=base.a*(1.0-cover)+cover;
    float3 glass=color*base.a*(1.0-cover)+tint.rgb*cover;
    float n2=normal.y*normal.y, n4=n2*n2, n16=n4*n4*n4*n4;
    float facing=optics.x<0.0?smoothstep(.65,.95,abs(normal.y)):mix(n2,.5*n2*abs(normal.y)*(1.0+n16),rimStyle.w);
    float rimBand=clamp(1.0-max(edge-.67*unit,0.0)/max(rimStyle.y*unit,.001),0.0,1.0);
    float tail=clamp(1.0-max(inset-.67*unit,0.0)/(4.5*unit),0.0,1.0);
    float light=min(rimStyle.x*facing*mix(rimBand,tail,rimStyle.z),1.0);
    glass+=(float3(alpha)-(1.0-lineStyle.x)*glass)*light;
    half4 under=sampleRaw(p);
    float line=clamp((.67*unit-edge)+.5,0.0,1.0);
    float darken=mix(lineStyle.y,lineStyle.z,pow(abs(normal.x),3.0));
    float3 darkLine=max(under.rgb-float3(.298*under.a),.22*under.rgb);
    float lineAmount=line*(max(lineStyle.y,lineStyle.z)>0.0?1.0:0.0);
    glass=mix(glass,mix(under.rgb,darkLine,darken),lineAmount);
    alpha=mix(alpha,float(under.a),lineAmount);
    float across=min(radius/max(box.z*2.0,.001),.4), x=abs(centered.x)/(box.z*2.0)+.5;
    x=min(x,1.0-x);
    float side=mix(.25,.27,geometry.w), corner=mix(.15,.1,geometry.w), flatTone=mix(.09,.02,geometry.w);
    float menuDark=mix(side,corner,clamp(x/max(across/4.0,.0001),0.0,1.0));
    menuDark=mix(menuDark,flatTone,clamp((x-across/4.0)/max(across*.75,.0001),0.0,1.0));
    glass=mix(glass,under.rgb*(1.0-menuDark),line*lineStyle.w);
    glass+=alpha*around(atan(abs(centered.x),max(abs(centered.y),.0001)),radius)*shine(max(edge,0.0),unit)*mix(.16,.22,geometry.w)*lineStyle.w;
    float2 delta=p-touchGlow.xy;
    glass+=touchGlow.z*alpha*mix(exp(-dot(delta,delta)/max(touchSpread.x*touchSpread.x,1.0)),1.0,touchSpread.y);
    return half4(min(glass,float3(alpha)),alpha);
}
"""
